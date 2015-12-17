/*
 * Copyright 2015 Magnus Madsen.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package dk.umonitor.runtime

import java.nio.file.{Files, Paths}
import java.time.ZoneOffset
import java.util.concurrent.Executors

import dk.umonitor.language.Program
import dk.umonitor.language.Program.{Action, Run}

import scala.collection.convert.decorateAsJava._

class Executor(program: Program)(implicit ctx: Context) {

  private val logger = ctx.logger.getLogger(getClass)

  /**
   * A thread pool in which actions execute.
   */
  val pool = Executors.newCachedThreadPool()

  /**
   * Executes all the given `actions` triggered by the given `event`.
   */
  def exec(actions: Seq[String], event: Event, target: Program.Target): Unit = {
    for (action <- actions.map(program.actions.apply)) {
      exec(action, event, target)
    }
  }

  /**
   * Executes all runs associated with the given `action` triggered by the given `event` for the given `target`.
   */
  def exec(action: Action, event: Event, target: Program.Target): Unit = {
    for (r <- action.run) {
      pool.submit(new Runnable {
        def run(): Unit = exec(r, event, target)
      })
    }
  }

  /**
   * Executes the given `run` caused by the given `event` for the given `target`.
   */
  def exec(run: Run, event: Event, target: Program.Target): Unit = try {
    val p = Paths.get(run.path)

    if (!Files.exists(p)) {
      logger.error(s"Executable '$p' does not exist.")
      return
    }

    if (!Files.isReadable(p)) {
      logger.error(s"Executable '$p' is not readable.")
      return
    }

    if (!Files.isExecutable(p)) {
      logger.error(s"Executable '$p' is not executable.")
      return
    }

    val commandline = run.path :: run.args.toList.map(x => substitute(x, event, target))
    val builder = new ProcessBuilder().command(commandline.asJava)
    builder.start()
  } catch {
    case e: Exception => logger.error(s"Unable to execute: '${run.path}'. Cause: ${e.getMessage}", e)
  }

  /**
   * Performs variable substitutions in the given string `s`.
   */
  private def substitute(s: String, event: Event, target: Program.Target): String = s match {
    case "$service-name" => event.name

    case "$target-name" => target.name

    case "$event-type" => event match {
      case Event.Up(name, timestamp) => "up"
      case Event.Dn(name, timestamp, message, exception) => "dn"
    }

    case "$event-timestamp" =>
      event.timestamp.toEpochSecond(ZoneOffset.UTC).toString

    case "$event-message" => event match {
      case Event.Up(name, timestamp) => "null"
      case Event.Dn(name, timestamp, message, exception) => message
    }

    case "$contact-emails" =>
      if (target.contacts.isEmpty)
        "null"
      else
        target.contacts.flatMap(_.emailAddress).mkString(",")

    case "$contact-phones" =>
      if (target.contacts.isEmpty)
        "null"
      else
        target.contacts.flatMap(_.phoneNumber).mkString(",")

    case _ => s
  }

}
