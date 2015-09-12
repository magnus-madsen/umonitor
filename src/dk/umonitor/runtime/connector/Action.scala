/*
 * Copyright (c) 2015. Magnus Madsen.
 *
 * This file is part of the uMonitor project.
 *
 * Source code available on the MIT license. See LICENSE.md for details.
 */

package dk.umonitor.runtime.connector

sealed trait Action

object Action {

  /**
   * Connects to the server.
   */
  case object Connect extends Action

  /**
   * Disconnects from the server.
   */
  case object Disconnect extends Action

  /**
   * Reads a line from the server and attempts to matches it with the given matcher.
   */
  case class ReadLine(matcher: Match) extends Action

  /**
   * Writes a line to the server.
   */
  case class WriteLine(line: String) extends Action

}
