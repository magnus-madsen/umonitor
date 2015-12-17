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

import java.util.concurrent.LinkedBlockingQueue

import dk.umonitor.util.{Logger, Options}

class Context(val options: Options) {

  /**
   * The root logger.
   */
  val logger = new Logger(
    options.logging.severity,
    options.logging.path,
    options.logging.stream,
    options.logging.limit
  )

  /**
   * The queue of pending events.
   */
  val queue = new LinkedBlockingQueue[Event]()

  /**
   * The history of recent events, transitions and messages.
   */
  val history = new History(options.logging.limit)

}
