/*
 * Copyright (c) 2015. Magnus Madsen.
 *
 * This file is part of the uMonitor project.
 *
 * Source code available under the MIT license. See LICENSE.md for details.
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
