/*
 * Copyright (c) 2015. Magnus Madsen.
 *
 * This file is part of the uMonitor project.
 *
 * Source code available under the MIT license. See LICENSE.md for details.
 */

package dk.umonitor.runtime

import java.time.LocalDateTime

import dk.umonitor.language.Program

/**
 * A common super-type for events.
 */
sealed trait Event {
  /**
   * Returns the name of the service the event is associated with.
   */
  def name: String

  /**
   * Returns the timestamp associated with the event.
   */
  def timestamp: LocalDateTime

  /**
   * Returns the event as a symbol.
   */
  def asSymbol: Program.Symbol = this match {
    case Event.Up(name, _) => Program.Symbol.Up(name)
    case Event.Dn(name, _, _, _) => Program.Symbol.Dn(name)
  }
}

object Event {

  /**
   * An event indicating that the named service is available.
   *
   * @param name the name of the service.
   * @param timestamp the time when the event occured.
   */
  case class Up(name: String, timestamp: LocalDateTime) extends Event {
    val message: String = ""
  }

  /**
   * An event indicating that the name service is unavailable.
   *
   * @param name the name of the service.
   * @param timestamp the time when the event occured.
   * @param message the error message.
   * @param exception the optional exception.
   */
  case class Dn(name: String, timestamp: LocalDateTime, message: String, exception: Option[Exception] = None) extends Event

}