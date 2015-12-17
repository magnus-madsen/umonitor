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

import java.net.{InetAddress, UnknownHostException}
import java.time.LocalDateTime

import dk.umonitor.runtime.Event
import dk.umonitor.util.PropertyMap
import org.xbill.DNS.{ARecord, Lookup, SimpleResolver, Type}

object DnsConnector {

  def connect(name: String, host: String, domain: String, address: String, opts: PropertyMap = PropertyMap.empty): Event = try {
    val resolver = new SimpleResolver(host)
    val lookup = new Lookup(domain, Type.A)
    lookup.setResolver(resolver)

    val records = lookup.run()
    if (records == null) {
      return Event.Dn(name, LocalDateTime.now(), s"No A-records found for domain '$domain'. Message: ${lookup.getErrorString}")
    }

    val found = records exists {
      case r: ARecord => r.getAddress == InetAddress.getByName(address);
    }

    if (found) {
      Event.Up(name, LocalDateTime.now())
    } else {
      Event.Dn(name, LocalDateTime.now(), s"No matching A-record found for domain '$domain' with address '$address'. Found ${records.size} other record(s)")
    }
  } catch {
    case e: UnknownHostException => Event.Dn(name, LocalDateTime.now(), s"Unable to resolve hostname: $host", Some(e))
  }

}
