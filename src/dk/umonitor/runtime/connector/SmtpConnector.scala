/*
 * Copyright (c) 2015. Magnus Madsen.
 *
 * This file is part of the uMonitor project.
 *
 * Source code available on the MIT license. See LICENSE.md for details.
 */

package dk.umonitor.runtime.connector

import dk.umonitor.runtime.Event
import dk.umonitor.runtime.connector.Action.{Connect, Disconnect, _}
import dk.umonitor.util.PropertyMap

object SmtpConnector {

  val DefaultPort = 25

  def steps: List[Action] = List(
    Connect,
    ReadLine(Match.Prefix("220")),
    WriteLine("quit"),
    ReadLine(Match.Prefix("221")),
    Disconnect
  )

  def connect(name: String, host: String, port: Option[Int], opts: PropertyMap = PropertyMap.empty): Event =
    TcpConnector.run(name, host, port.getOrElse(DefaultPort), steps, opts)

}
