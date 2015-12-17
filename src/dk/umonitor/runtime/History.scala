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

import dk.umonitor.language.Program.State

import scala.collection.mutable

class History(limit: Int) {

  /**
   * Tracks the last n events.
   */
  @volatile
  private var events = List.empty[Event]

  /**
   * Tracks the last transitions.
   */
  @volatile
  private var transitions: Map[String, (Event, State, State)] = Map.empty

  /**
   * Tracks the number of up events for each monitor.
   */
  private val numberOfUpEvents = mutable.Map.empty[String, Int]

  /**
   * Tracks the number of down events for each monitor.
   */
  private val numberOfDnEvents = mutable.Map.empty[String, Int]

  /**
   * Returns the last 100 events.
   */
  def getRecentEvents: List[Event] = events

  /**
   * Returns the recent transitions.
   */
  def getRecentTransitions: Map[String, (Event, State, State)] = transitions

  /**
   * Returns the total number of up events seen since startup.
   */
  def getNumberOfUpEvents: Int = numberOfUpEvents.values.sum

  /**
   * Returns the total number of dn events seen since startup.
   */
  def getNumberOfDnEvents: Int = numberOfDnEvents.values.sum

  /**
   * Returns the total number of events seen since startup.
   */
  def getTotalNumberOfEvents: Int = getNumberOfUpEvents + getNumberOfDnEvents

  /**
   * Notifies that the given event `e` has occurred.
   */
  def notifyEvent(e: Event): Unit = synchronized {
    events = (e :: events).take(limit)

    e match {
      case Event.Up(name, timestamp) =>
        numberOfUpEvents += (name -> (numberOfUpEvents.getOrElse(name, 0) + 1))

      case Event.Dn(name, timestamp, message, exception) =>
        numberOfDnEvents += (name -> (numberOfDnEvents.getOrElse(name, 0) + 1))

    }
  }

  /**
   * Notifies that the given transition has occured.
   */
  def notifyTransition(name: String, event: Event, src: State, dst: State): Unit = synchronized {
    transitions += (name ->(event, src, dst))
  }

}
