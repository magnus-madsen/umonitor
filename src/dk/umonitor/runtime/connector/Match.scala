/*
 * Copyright (c) 2015. Magnus Madsen.
 *
 * This file is part of the uMonitor project.
 *
 * Source code available under the MIT license. See LICENSE.md for details.
 */

package dk.umonitor.runtime.connector

sealed trait Match

object Match {

  /**
   * Matches the `prefix`.
   */
  case class Prefix(prefix: String) extends Match

  /**
   * Matches the `suffix`.
   */
  case class Suffix(suffix: String) extends Match

  /**
   * Matches the `regex`
   */
  case class Regex(regex: String) extends Match

}

