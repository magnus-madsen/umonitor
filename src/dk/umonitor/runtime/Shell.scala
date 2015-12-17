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

import java.time._
import java.time.format.DateTimeFormatter

import dk.umonitor.language.Program
import dk.umonitor.language.Program._
import dk.umonitor.util.{AsciiTable, Logger, Version}

object Shell {

  /**
   * A common super-type for shell commands.
   */
  sealed trait Command

  /**
   * A common super-type for the queryable tables.
   */
  sealed trait Table

  object Command {

    /**
     * A command to query internal tables.
     */
    case class Query(table: Table, filter: Option[String]) extends Command

    /**
     * A command to print help information.
     */
    case class Help(item: Option[String]) extends Command

    /**
     * A command which was not recognized.
     */
    case class Unknown(command: String) extends Command

    /**
     * A no operation command.
     */
    case object Nop extends Command

    /**
     * A command which represents that the input stream has been closed.
     */
    case object Eof extends Command

    /**
     * A command to print status information.
     */
    case object Status extends Command

    /**
     * An alias for the shutdown command.
     */
    case object Exit extends Command

    /**
     * An alias for the quit command.
     */
    case object Quit extends Command

    /**
     * A command to shutdown the system.
     */
    case object Shutdown extends Command

  }

  object Table {

    case object Actions extends Table

    case object Contacts extends Table

    case object Monitors extends Table

    case object Services extends Table

    case object Events extends Table

    case object Log extends Table

    case object Transitions extends Table

  }

}

/**
 * An interactive shell.
 */
class Shell(program: Program)(implicit ctx: Context) {

  val dateTimeFormatter = DateTimeFormatter.ofPattern("yyy-MM-dd HH:mm")
  val uptime = LocalDateTime.now()
  private val logger = ctx.logger.getLogger(classOf[Shell])

  /**
   * Prints the welcome banner and starts the interactive shell.
   */
  def startAndAwait(): Unit = {
    logger.trace("Starting Shell.")
    printWelcomeBanner()
    loop()
  }

  /**
   * Continuously reads a line of input from the input stream, parses and executes it.
   */
  def loop(): Unit = {
    while (!Thread.currentThread().isInterrupted) {
      Console.print(prompt)
      Console.flush()
      val line = scala.io.StdIn.readLine()
      val cmd = parse(line)
      try {
        execute(cmd)
      } catch {
        case e: Exception =>
          Console.println("Exception: " + e.getMessage)
          e.printStackTrace()
      }

      if (cmd == Shell.Command.Eof) {
        return
      }
    }
  }

  /**
   * Executes the given `command`.
   */
  def execute(command: Shell.Command): Unit = command match {
    case Shell.Command.Nop => // do nothing

    case Shell.Command.Eof =>
      Console.println("Shell closed. Restart to regain accesss to the shell.")

    // actions
    case Shell.Command.Query(Shell.Table.Actions, filter) =>
      val t = new AsciiTable().withCols("Name", "Run", "Location").withFilter(filter)
      for (Action(name, run, location) <- program.actions.values.toSeq.sortBy(_.name)) {
        t.mkRow(List(name, run.mkString(", "), location.format))
      }
      t.write(Console.out)

    // contacts
    case Shell.Command.Query(Shell.Table.Contacts, filter) =>
      val t = new AsciiTable().withCols("Name", "Email", "Phone", "Location").withFilter(filter)
      for (Contact(name, email, phone, location) <- program.contacts.values.toSeq.sortBy(_.name)) {
        t.mkRow(List(name, email.getOrElse("-"), phone.getOrElse("-"), location.format))
      }
      t.write(Console.out)

    // monitors
    case Shell.Command.Query(Shell.Table.Monitors, filter) =>
      val t = new AsciiTable().withCols("Name", "States", "Clocks", "Location").withFilter(filter)
      for (Monitor(name, states, clocks, transitions, location) <- program.monitors.values.toSeq.sortBy(_.name)) {
        t.mkRow(List(name, states.mkString(", "), clocks.mkString(", "), location.format))
      }
      t.write(Console.out)

    // services
    case Shell.Command.Query(Shell.Table.Services, filter) =>
      val t = new AsciiTable().withCols("Name", "Connector", "Location").withFilter(filter)
      for (Service(name, connector, location) <- program.services.values.toSeq.sortBy(_.name)) {
        t.mkRow(List(name, connector, location.format))
      }
      t.write(Console.out)

    // events
    case Shell.Command.Query(Shell.Table.Events, filter) =>
      val t = new AsciiTable().withCols("Status", "Service", "Timestamp", "Message", "Exception").withFilter(filter)
      for (event <- ctx.history.getRecentEvents.reverse) {
        event match {
          case Event.Up(name, timestamp) =>
            t.mkRow(List("UP", name, dateTimeFormatter.format(timestamp), "", ""))
          case Event.Dn(name, timestamp, message, exception) =>
            t.mkRow(List("DN", name, dateTimeFormatter.format(timestamp), message, exception.getOrElse("")))
        }
      }
      t.write(Console.out)

    // log
    case Shell.Command.Query(Shell.Table.Log, filter) =>
      val t = new AsciiTable().withCols("Source", "Severity", "Time", "Message", "Exception").withFilter(filter)
      for (Logger.Message(source, severity, timestamp, message, exception) <- ctx.logger.getRecentMessages.reverse) {
        t.mkRow(List(source, severity, dateTimeFormatter.format(timestamp), message, exception.getOrElse("")))
      }
      t.write(Console.out)

    // transitions
    case Shell.Command.Query(Shell.Table.Transitions, filter) =>
      val t = new AsciiTable().withCols("Status", "Target", "Service", "Src", "Dst", "Timestamp", "Message").withFilter(filter)
      for ((targetName, (event, State(src), State(dst))) <- ctx.history.getRecentTransitions.toList.sortBy(_._1)) {
        event match {
          case Event.Up(serviceName, timestamp) =>
            t.mkRow(List("UP", targetName, serviceName, src, dst, dateTimeFormatter.format(event.timestamp), ""))
          case Event.Dn(serviceName, timestamp, message, exception) =>
            t.mkRow(List("DN", targetName, serviceName, src, dst, dateTimeFormatter.format(event.timestamp), message))
        }
      }
      t.write(Console.out)

    case Shell.Command.Status =>
      val version = System.getProperty("java.version")

      val freeMemory = (Runtime.getRuntime.freeMemory() / 1000L) / 1000L
      val totalMemory = (Runtime.getRuntime.totalMemory() / 1000L) / 1000L

      Console.println(s"JVM version $version. Threads: ${Thread.getAllStackTraces.size()}. Memory: $freeMemory MB / $totalMemory MB. Booted on ${dateTimeFormatter.format(uptime)}.")
      Console.println(s"Currently ${program.monitors.size} monitors and ${program.services.size} services. Events: ${ctx.history.getNumberOfDnEvents} down, ${ctx.history.getNumberOfUpEvents} up, ${ctx.history.getTotalNumberOfEvents} total.")

    case Shell.Command.Exit =>
      Console.println("Did you mean `shutdown'?")

    case Shell.Command.Quit =>
      Console.println("Did you mean `shutdown'?")

    case Shell.Command.Shutdown =>
      Console.println("Are you sure you want to shutdown? [yes/No]")
      val answer = scala.io.StdIn.readLine()
      if ("yes" == answer) {
        Console.println("Goodbye.")
        System.exit(0)
      }

    case Shell.Command.Help(None) =>
      Console.println("Available commands: `query', `status' and `shutdown'.")
      Console.println("Try `help <item>' for more information.")

    case Shell.Command.Help(Some("query")) =>
      Console.println("`query' <name> <regex> where name is one of:")
      Console.println("    actions")
      Console.println("    contacts")
      Console.println("    services")
      Console.println("    events")
      Console.println("    log")
      Console.println("    transitions")

    case Shell.Command.Help(Some("status")) =>
      Console.println("`status' - prints status info, including thread count and memory usage.")

    case Shell.Command.Help(Some("shutdown")) =>
      Console.println("`shutdown' - prompts to terminate the program.")

    case Shell.Command.Help(Some(argument)) =>
      Console.println(s"No help available for `$argument'.")

    case Shell.Command.Unknown(s) =>
      Console.println(s"Unrecognized command '$s'.")
      Console.println("Type `help' for more information. Type `shutdown' to terminate.")
  }

  /**
   * Parses the given string `s` as a command.
   */
  def parse(s: String): Shell.Command = s match {
    case null => Shell.Command.Eof
    case "" => Shell.Command.Nop
    case _ => parse(s.split(" ").toList)
  }

  /**
   * Parses the given list of strings `xs` as a command
   */
  def parse(xs: List[String]): Shell.Command = xs match {
    case Nil | "" :: Nil => Shell.Command.Nop
    case "help" :: Nil => Shell.Command.Help(None)
    case "help" :: "query" :: Nil => Shell.Command.Help(Some("query"))
    case "help" :: "status" :: Nil => Shell.Command.Help(Some("status"))
    case "help" :: "shutdown" :: Nil => Shell.Command.Help(Some("shutdown"))
    case "exit" :: Nil => Shell.Command.Exit
    case "quit" :: Nil => Shell.Command.Quit
    case "status" :: Nil => Shell.Command.Status
    case "shutdown" :: Nil => Shell.Command.Shutdown

    case "query" :: Nil => Shell.Command.Help(Some("query"))

    case "query" :: "actions" :: Nil => Shell.Command.Query(Shell.Table.Actions, None)
    case "query" :: "actions" :: filter :: Nil => Shell.Command.Query(Shell.Table.Actions, Some(filter))

    case "query" :: "contacts" :: Nil => Shell.Command.Query(Shell.Table.Contacts, None)
    case "query" :: "contacts" :: filter :: Nil => Shell.Command.Query(Shell.Table.Contacts, Some(filter))

    case "query" :: "monitors" :: Nil => Shell.Command.Query(Shell.Table.Monitors, None)
    case "query" :: "monitors" :: filter :: Nil => Shell.Command.Query(Shell.Table.Monitors, Some(filter))

    case "query" :: "services" :: Nil => Shell.Command.Query(Shell.Table.Services, None)
    case "query" :: "services" :: filter :: Nil => Shell.Command.Query(Shell.Table.Services, Some(filter))

    case "query" :: "events" :: Nil => Shell.Command.Query(Shell.Table.Events, None)
    case "query" :: "events" :: filter :: Nil => Shell.Command.Query(Shell.Table.Events, Some(filter))

    case "query" :: "log" :: Nil => Shell.Command.Query(Shell.Table.Log, None)
    case "query" :: "log" :: filter :: Nil => Shell.Command.Query(Shell.Table.Log, Some(filter))

    case "query" :: "transitions" :: Nil => Shell.Command.Query(Shell.Table.Transitions, None)
    case "query" :: "transitions" :: filter :: Nil => Shell.Command.Query(Shell.Table.Transitions, Some(filter))

    case _ => Shell.Command.Unknown(xs.mkString(" "))
  }

  /**
   * Returns the prompt.
   */
  private def prompt: String = hourAndMinute + "> "

  /**
   * Returns the current hour and minute as a string.
   */
  private def hourAndMinute: String = {
    val localTime = LocalTime.now()
    val formatter = DateTimeFormatter.ofPattern("HH:mm")
    formatter.format(localTime)
  }

  /**
   * Prints a friendly welcome banner when entering the shell.
   */
  private def printWelcomeBanner(): Unit = {
    val version = Version.currentVersion.major + "." + Version.currentVersion.minor + "." + Version.currentVersion.revision
    Console.println(s"Welcome to uMonitor5 (version $version). Copyright 2015 Magnus Madsen.")
    Console.println(s"This is open source software. Please see LICENSE.md for full details.")
    Console.println()
    Console.println(s"Enter a command. Type `help' for information. Type `shutdown' to terminate.")
    Console.flush()
  }

}
