/*
 * Copyright (c) 2015. Magnus Madsen.
 *
 * This file is part of the uMonitor project.
 *
 * Source code available under the MIT license. See LICENSE.md for details.
 */

package dk.umonitor.runtime.connector

import dk.umonitor.runtime.Event
import dk.umonitor.runtime.connector.Action._
import dk.umonitor.util.PropertyMap

object RdpConnector {

  val DefaultPort = 3389

  val steps: List[Action] = List(
    Connect,
    Disconnect
  )

  def connect(name: String, host: String, port: Option[Int] = None, opts: PropertyMap = PropertyMap.empty): Event =
    TcpConnector.run(name, host, port.getOrElse(DefaultPort), steps, opts)

}
