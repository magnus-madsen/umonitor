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
