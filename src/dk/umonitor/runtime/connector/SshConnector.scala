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

object SshConnector {

  val DefaultPort = 22

  def steps: List[Action] = List(
    Connect,
    ReadLine(Match.Prefix("SSH-2")),
    WriteLine("SSH-2.0-OpenSSH_5.1p1 Debian-5"),
    Disconnect
  )

  def connect(name: String, host: String, port: Option[Int] = None, opts: PropertyMap = PropertyMap.empty): Event =
    TcpConnector.run(name, host, port.getOrElse(DefaultPort), steps, opts)

}
