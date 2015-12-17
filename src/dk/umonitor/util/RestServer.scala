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

import java.io.{ByteArrayOutputStream, IOException}
import java.net.{BindException, InetSocketAddress}
import java.time.ZoneId
import java.util.concurrent.Executors

import com.sun.net.httpserver.{HttpExchange, HttpHandler, HttpServer}
import dk.umonitor.language.Program._
import dk.umonitor.runtime.{Context, Event}
import org.json4s._
import org.json4s.native.JsonMethods

class RestServer(implicit ctx: Context) {

  private val logger = ctx.logger.getLogger(getClass)

  /**
    * The minimum port number to bind to.
    */
  val MinPort = 8000

  /**
    * The maximum port number to bind to.
    */
  val MaxPort = 8100

  /**
   * A collection of static resources included in the Jar.
   */
  val StaticResources = Set(
    // HTML
    "/web/index.html",

    // Stylesheet
    "/web/css/stylesheet.css",

    // Backgrounds
    "/web/img/bg/green.png",
    "/web/img/bg/grey.png",
    "/web/img/bg/red.png",
    "/web/img/bg/yellow.png",

    // Icons
    "/web/img/icon/green.png",
    "/web/img/icon/grey.png",
    "/web/img/icon/red.png",
    "/web/img/icon/yellow.png",

    // JavaScript
    "/web/js/app.js",
    "/web/js/lib/jquery.min.js",
    "/web/js/lib/lodash.min.js",
    "/web/js/lib/moment.min.js",
    "/web/js/lib/react.min.js"
  )

  /**
   * A simple http handler which serves static resources.
   */
  class FileHandler extends HttpHandler {

    /**
     * A loaded resources is an array of bytes and its associated mimetype.
     */
    case class LoadedResource(bytes: Array[Byte], mimetype: String)

    /**
     * Immediately loads all the given `resources` into memory.
     */
    def loadResources(resources: Set[String]): Map[String, LoadedResource] = StaticResources.foldLeft(Map.empty[String, LoadedResource]) {
      case (m, path) =>
        val inputStream = getClass.getResourceAsStream(path)

        if (inputStream == null) {
          throw new IOException(s"Unable to load static resource '$path'.")
        }

        val buffer = new ByteArrayOutputStream()

        var byte = inputStream.read()
        while (byte != -1) {
          buffer.write(byte)
          byte = inputStream.read()
        }

        m + (path -> LoadedResource(buffer.toByteArray, mimetypeOf(path)))

    }

    /**
     * All resources are loaded upon startup.
     */
    val LoadedResources: Map[String, LoadedResource] = loadResources(StaticResources)

    /**
     * Returns the mimetype corresponding to the given `path`.
     */
    def mimetypeOf(path: String): String = path match {
      case p if p.endsWith(".css") => "text/css"
      case p if p.endsWith(".js") => "text/javascript; charset=utf-8"
      case p if p.endsWith(".jsx") => "text/javascript; charset=utf-8"
      case p if p.endsWith(".html") => "text/html; charset=utf-8"
      case p if p.endsWith(".png") => "image/png"
      case _ =>
        logger.error(s"Unknown mimetype for path $path")
        throw new RuntimeException(s"Unknown mimetype for path $path")
    }

    /**
     * Handles every incoming http request.
     */
    def handle(t: HttpExchange): Unit = try {
      // construct the local path
      val requestPath = t.getRequestURI.getPath

      // rewrite / to /index.html
      val path = if (requestPath == "/")
        "/web/index.html"
      else
        "/web" + requestPath

      // lookup the requested resource
      LoadedResources.get(path) match {
        case None =>
          t.sendResponseHeaders(404, 0)
          t.close()

        case Some(LoadedResource(bytes, mimetype)) =>
          t.getResponseHeaders.add("Content-Type", mimetype)
          t.getResponseHeaders.add("Cache-Control", "max-age=" + (31 * 24 * 60 * 60))

          t.sendResponseHeaders(200, bytes.length)
          val outputStream = t.getResponseBody
          outputStream.write(bytes)
          outputStream.close()
          t.close()
      }
    } catch {
      case e: RuntimeException =>
        logger.error("Unknown error during http exchange.", e)
    }

  }

  /**
   * A simple http handler which serves JSON.
   */
  abstract class JsonHandler extends HttpHandler {
    /**
     * An abstract method which returns the JSON object to be sent.
     */
    def json: JValue

    /**
     * Handles every incoming http request.
     */
    def handle(t: HttpExchange): Unit = {
      t.getResponseHeaders.add("Content-Type", "application/javascript")

      val data = JsonMethods.pretty(JsonMethods.render(json))
      t.sendResponseHeaders(200, data.length())

      val outputStream = t.getResponseBody
      outputStream.write(data.getBytes)
      outputStream.close()
      t.close()
    }
  }

  class StateHandler extends JsonHandler {
    def json: JValue = JArray(ctx.history.getRecentTransitions.toList.sortBy(_._1).map {
      case (name, (event, State(src), State(dst))) =>
        val msg = event match {
          case e: Event.Up => ""
          case e: Event.Dn => e.message
        }
        JObject(List(
          "name" -> JString(name),
          "time" -> JString(event.timestamp.atZone(ZoneId.systemDefault).toEpochSecond.toString),
          "state" -> JString(dst),
          "transition" -> JObject(List(
            "src" -> JString(src),
            "event" -> JString(event.name),
            "dst" -> JString(dst),
            "message" -> JString(msg)
          ))
        ))
    })
  }

  /**
   * Bootstraps the internal http server.
   */
  def start(): InetSocketAddress = try {
    logger.trace("Starting WebServer.")

    // initialize server.
    val server = newServer(MinPort, MaxPort)
    val port = server.getAddress.getPort

    // mount ajax handlers.
    server.createContext("/api/status", new StateHandler())

    // mount file handler.
    server.createContext("/", new FileHandler())

    // ensure that multiple threads are used.
    server.setExecutor(Executors.newCachedThreadPool())

    // start server.
    server.start()

    // return the address bound to.
    server.getAddress
  } catch {
    case e: IOException =>
      logger.error(s"Unable to start web server. The REST API will not be available. Error: ${e.getMessage}", e)
      new InetSocketAddress(0)
  }

  /**
    * Returns a new HttpServer bound to a port between the given `minPort` and `maxPort`.
    */
  private def newServer(minPort: Int, maxPort: Int): HttpServer = {
    assert(minPort <= maxPort)

    for (port <- minPort to maxPort) {
      try {
        return HttpServer.create(new InetSocketAddress(port), 0)
      } catch {
        case e: BindException => // nop - try next port.
      }
    }

    throw new IOException(s"Unable to find an available port between $minPort and $maxPort.")
  }

}
