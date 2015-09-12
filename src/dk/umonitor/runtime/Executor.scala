/*
 * Copyright (c) 2015. Magnus Madsen.
 *
 * This file is part of the uMonitor project.
 *
 * Source code available on the MIT license. See LICENSE.md for details.
 */

package dk.umonitor.runtime

import java.nio.file.{Files, Paths}
import java.time.ZoneOffset

import dk.umonitor.language.Program
import dk.umonitor.language.Program.{Action, Run}

import scala.collection.convert.decorateAsJava._

class Executor(program: Program)(implicit ctx: Context) {

  private val logger = ctx.logger.getLogger(getClass)

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
    for (run <- action.run) {
      exec(run, event, target)
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
