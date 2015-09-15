/*
 * Copyright (c) 2015. Magnus Madsen.
 *
 * This file is part of the uMonitor project.
 *
 * Source code available under the MIT license. See LICENSE.md for details.
 */

package dk.umonitor.util

import java.io.{PrintWriter, PrintStream}
import java.nio.file.{Paths, Files, Path, StandardOpenOption}
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

import dk.umonitor.util.Logger.Message

/**
 * Companion object for the [[Logger]] class.
 */
object Logger {

  /**
   * A log message.
   *
   * @param source the source of the log message.
   * @param severity the severity of the log message.
   * @param timestamp the time when the log message occured.
   * @param message the message itself.
   * @param exception an optional exception associated with the log message.
   */
  case class Message(source: String, severity: Severity, timestamp: LocalDateTime, message: String, exception: Option[Exception] = None) {
    val formatted: String = {
      val dateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
      val formattedTime = dateTimeFormatter.format(timestamp)

      s"[$formattedTime] [$source] [$severity]: $message." +
        exception.map("\n" + _.toString).getOrElse("")
    }
  }

}

/**
 * The main logger class.
 *
 * @param threshold the threshold needed before a log message is display and stored.
 * @param path an optional path to the logfile.
 * @param stream an optional stream to print to.
 * @param limit the number of log message to keep.
 */
class Logger(threshold: Severity, path: Option[Path], stream: Option[PrintStream], limit: Int) {

  /**
   * Tracks the latest messages.
   */
  private var messages = List.empty[Message]

  /**
   * Returns the most recent log messages.
   */
  def getRecentMessages: List[Message] = messages

  /**
   * Returns a sub logger for the given `name`.
   */
  def getLogger(clazz: Class[_]): SubLogger = new SubLogger(clazz.getCanonicalName)

  /**
   * Prints and logs the give message `m` unless its severity is below the threshold.
   */
  private def handle(m: Message): Unit = {
    if (m.severity.priority >= threshold.priority) {
      print(m)
    }
    messages = (m :: messages).take(limit)
    log(m)
  }

  /**
   * Stores the given message `m` in the logfile (if applicable).
   */
  private def log(m: Message): Unit = {
    // log the entry to the log file.
    if (path.nonEmpty) {
      val writer = Files.newBufferedWriter(path.get, StandardOpenOption.CREATE, StandardOpenOption.APPEND)
      writer.write(m.formatted)
      writer.write("\n")
      writer.close()
    }

    // log the exception to a separate file (if any).
    if (m.exception.nonEmpty) {
      val dateTimeFormatter = DateTimeFormatter.ofPattern("yyy-MM-dd-HH:mm")
      val filename = dateTimeFormatter.format(m.timestamp) + ".txt"
      val writer = Files.newBufferedWriter(Paths.get(filename), StandardOpenOption.CREATE)
      m.exception.get.printStackTrace(new PrintWriter(writer))
      writer.close()
    }
  }

  /**
   * Prints  the given message `m` to the stream (if applicable).
   */
  private def print(m: Message): Unit = stream match {
    case None => // nop
    case Some(writer) =>
      writer.println(m.formatted)
      writer.flush()
  }

  /**
   * The SubLogger class actually used by the clients.
   *
   * @param name the name of the sublogger (typically the class name).
   */
  class SubLogger(name: String) {

    /**
     * Logs the given `message` at the trace level.
     *
     * The message is discarded if the threshold is higher than the trace level.
     */
    def trace(message: String): Unit = {
      handle(Message(name, Severity.Trace, LocalDateTime.now(), message))
    }

    /**
     * Logs the given `message` and `exception` at the trace level.
     *
     * The message is discarded if the threshold is higher than the trace level.
     */
    def trace(message: String, exception: Exception): Unit = {
      handle(Message(name, Severity.Trace, LocalDateTime.now(), message, Some(exception)))
    }

    /**
     * Logs the given `message` at the informational level.
     *
     * The message is discarded if the threshold is higher than the informational level.
     */
    def info(message: String): Unit = {
      handle(Message(name, Severity.Info, LocalDateTime.now(), message))
    }

    /**
     * Logs the given `message` and `exception` at the informational level.
     *
     * The message is discarded if the threshold is higher than the informational level.
     */
    def info(message: String, exception: Exception): Unit = {
      handle(Message(name, Severity.Info, LocalDateTime.now(), message, Some(exception)))
    }

    /**
     * Logs the given `message` at the warning level.
     *
     * The message is discarded if the threshold is higher than the warning level.
     */
    def warn(message: String): Unit = {
      handle(Message(name, Severity.Warning, LocalDateTime.now(), message))
    }

    /**
     * Logs the given `message` and `exception` at the warning level.
     *
     * The message is discarded if the threshold is higher than the warning level.
     */
    def warn(message: String, exception: Exception): Unit = {
      handle(Message(name, Severity.Warning, LocalDateTime.now(), message, Some(exception)))
    }

    /**
     * Logs the given `message` at the error level.
     *
     * The message is discarded if the threshold is higher than the error level.
     */
    def error(message: String): Unit = {
      handle(Message(name, Severity.Error, LocalDateTime.now(), message))
    }

    /**
     * Logs the given `message` and `exception` at the error level.
     *
     * The message is discarded if the threshold is higher than the error level.
     */
    def error(message: String, exception: Exception): Unit = {
      handle(Message(name, Severity.Error, LocalDateTime.now(), message, Some(exception)))
    }

  }

}
