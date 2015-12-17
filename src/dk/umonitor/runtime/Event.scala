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