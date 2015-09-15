/*
 * Copyright (c) 2015. Magnus Madsen.
 *
 * This file is part of the uMonitor project.
 *
 * Source code available under the MIT license. See LICENSE.md for details.
 */

package dk.umonitor.util

/**
 * A common super-type for severity levels.
 */
sealed trait Severity {
  /**
   * Returns the absolute priority.
   *
   * [[Severity.Error]] has the highest priority whereas [[Severity.Trace]] has the lowest priority.
   */
  def priority: Int = this match {
    case Severity.Trace => 1
    case Severity.Info => 2
    case Severity.Warning => 3
    case Severity.Error => 4
  }
}

object Severity {

  /**
   * The [[Trace]] severity. Used for low-level debugging.
   */
  case object Trace extends Severity

  /**
   * The [[Info]] severity. Used for debugging.
   */
  case object Info extends Severity

  /**
   * The [[Warning]] severity. Used for potentially harmful warnings.
   */
  case object Warning extends Severity

  /**
   * The [[Error]] severity. Used for errors.
   */
  case object Error extends Severity

}
