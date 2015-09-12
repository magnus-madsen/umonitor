/*
 * Copyright (c) 2015. Magnus Madsen.
 *
 * This file is part of the uMonitor project.
 *
 * Source code available on the MIT license. See LICENSE.md for details.
 */

package dk.umonitor.runtime.connector

import java.io.IOException
import java.net.{InetAddress, UnknownHostException}
import java.time.LocalDateTime

import dk.umonitor.runtime.Event
import dk.umonitor.util.PropertyMap

object IcmpConnector {

  val DefaultTimeout = 5000

  def connect(name: String, host: String, opts: PropertyMap = PropertyMap.empty): Event = try {

    val Timeout = opts.getInt("timeout", DefaultTimeout)

    val address = InetAddress.getByName(host)
    if (address.isReachable(Timeout)) {
      Event.Up(name, LocalDateTime.now())
    } else {
      Event.Dn(name, LocalDateTime.now(), "Timeout")
    }
  } catch {
    case ex: UnknownHostException => Event.Dn(name, LocalDateTime.now(), "Unknown Host", Some(ex))
    case ex: IOException => Event.Dn(name, LocalDateTime.now(), "I/O Error", Some(ex))
  }

}
