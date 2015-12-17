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
