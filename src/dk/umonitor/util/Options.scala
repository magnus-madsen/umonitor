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

import java.io.PrintStream
import java.nio.file.{Path, Paths}

object Options {
  /**
   * Default options.
   */
  val Default = Options(
    producer =
      Producer(threads = 50, period = 60),

    http =
      Http(port = 8025),

    logging =
      Logging(severity = Severity.Info,
        path = Some(Paths.get("umonitor.txt")),
        stream = Some(Console.out),
        limit = 10 * 1000)
  )
}

/**
 * Options for uMonitor5.
 */
case class Options(producer: Producer, http: Http, logging: Logging)

/**
 * Options for the event producer.
 *
 * @param threads the number of threads to use.
 * @param period the period between running each service (seconds).
 */
case class Producer(threads: Int, period: Int)

/**
 * Options for the embedded web server.
 */
case class Http(port: Int)

/**
 * Options for logging.
 *
 * @param severity the threshold before a log message is logged.
 * @param path the optional path of the logfile.
 * @param stream the optional output stream.
 * @param limit the number of log message to keep.
 */
case class Logging(severity: Severity, path: Option[Path], stream: Option[PrintStream], limit: Int)