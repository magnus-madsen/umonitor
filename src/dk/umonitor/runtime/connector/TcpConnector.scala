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

import java.io.{BufferedReader, IOException, InputStreamReader, PrintWriter}
import java.net.{InetSocketAddress, Socket, SocketTimeoutException, UnknownHostException}
import java.time.LocalDateTime
import java.util.concurrent.TimeoutException

import dk.umonitor.runtime.Event
import dk.umonitor.runtime.connector.Action.{Connect, Disconnect, _}
import dk.umonitor.util.PropertyMap

object TcpConnector {

  val DefaultConnectTimeout = 5000
  val DefaultReadTimeout = 5000

  def run(name: String, host: String, port: Int, protocol: List[Action], opts: PropertyMap = PropertyMap.empty): Event = {

    val ConnectTimeout = opts.getInt("connect-timeout", DefaultConnectTimeout)
    val ReadTimeout = opts.getInt("read-timeout", DefaultReadTimeout)

    var socket: Socket = null
    var reader: BufferedReader = null
    var writer: PrintWriter = null

    def eval(steps: List[Action]): Event = steps match {
      case Nil => Event.Up(name, LocalDateTime.now())

      case Connect :: rest => try {
        socket = new Socket()
        socket.setSoTimeout(ConnectTimeout)
        socket.connect(new InetSocketAddress(host, port))
        socket.setSoTimeout(ReadTimeout)
        reader = new BufferedReader(new InputStreamReader(socket.getInputStream))
        writer = new PrintWriter(socket.getOutputStream)
        eval(rest);
      } catch {
        case e: UnknownHostException => Event.Dn(name, LocalDateTime.now(), s"Unable to resolve host. Cause: ${e.getMessage}", Some(e))
        case e: TimeoutException => Event.Dn(name, LocalDateTime.now(), s"Connection timeout. Cause: ${e.getMessage}", Some(e))
        case e: IOException => Event.Dn(name, LocalDateTime.now(), s"Unable to connect. Cause: ${e.getMessage}", Some(e))
      }

      case Disconnect :: rest => try {
        reader.close()
        writer.close()
        socket.close()
        eval(rest);
      } catch {
        case e: IOException => Event.Dn(name, LocalDateTime.now(), s"Unable to disconnect. Cause: ${e.getMessage}", Some(e));
      }

      case ReadLine(matcher) :: rest => try {
        val line = reader.readLine()
        matcher match {
          case Match.Prefix(prefix) if line == null || !line.startsWith(prefix) => Event.Dn(name, LocalDateTime.now(), s"Protocol Error. Expected Prefix: '$prefix'. Got: '$line'.", None)
          case Match.Suffix(suffix) if line == null || !line.endsWith(suffix) => Event.Dn(name, LocalDateTime.now(), s"Protocol Error. Expected Suffix: '$suffix'.  Got: '$line'", None)
          case Match.Regex(regex) if line == null || !line.matches(regex) => Event.Dn(name, LocalDateTime.now(), s"Protocol Error. Expected Regex: '$regex'. Got: '$line'.", None)
          case _ => eval(rest); // successfully matched!
        }
      } catch {
        case e: SocketTimeoutException => Event.Dn(name, LocalDateTime.now(), s"Unable to read. Cause: '${e.getMessage}'. Expected: '$matcher'.", Some(e))
        case e: IOException => Event.Dn(name, LocalDateTime.now(), s"Unable to read. Cause: '${e.getMessage}'. Expected: '$matcher'.", Some(e))
      }

      case WriteLine(line) :: rest => try {
        writer.print(line + "\r\n")
        writer.flush()
        eval(rest)
      } catch {
        case e: IOException => Event.Dn(name, LocalDateTime.now(), s"Unable to write: '$line'. Cause: '${e.getMessage}'.", Some(e))
      }
    }

    eval(protocol)
  }

}
