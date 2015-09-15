/*
 * Copyright (c) 2015. Magnus Madsen.
 *
 * This file is part of the uMonitor project.
 *
 * Source code available under the MIT license. See LICENSE.md for details.
 */

package dk.umonitor.runtime.connector

import dk.umonitor.runtime.Event
import dk.umonitor.runtime.connector.Action.{Connect, Disconnect, _}
import dk.umonitor.util.PropertyMap

object Pop3Connector {

  val DefaultPort = 110

  val steps: List[Action] = List(
    Connect,
    ReadLine(Match.Prefix("+OK")),
    WriteLine("QUIT"),
    ReadLine(Match.Prefix("+OK")),
    Disconnect
  )

  def connect(name: String, host: String, port: Option[Int], opts: PropertyMap = PropertyMap.empty): Event =
    TcpConnector.run(name, host, port.getOrElse(DefaultPort), steps, opts)

}
