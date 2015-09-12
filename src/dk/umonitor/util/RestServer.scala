/*
 * Copyright (c) 2015. Magnus Madsen.
 *
 * This file is part of the uMonitor project.
 *
 * Source code available on the MIT license. See LICENSE.md for details.
 */

package dk.umonitor.util

import java.io.{ByteArrayOutputStream, IOException}
import java.net.InetSocketAddress
import java.time.ZoneId

import com.sun.net.httpserver.{HttpExchange, HttpHandler, HttpServer}
import dk.umonitor.language.Program._
import dk.umonitor.runtime.{Context, Event}
import org.json4s._
import org.json4s.native.JsonMethods

class RestServer(implicit ctx: Context) {

  private val logger = ctx.logger.getLogger(getClass)

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
    "/web/js/lib/jquery.js",
    "/web/js/lib/lodash.js",
    "/web/js/lib/moment.js",
    "/web/js/lib/react.js"
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
  def start(): Unit = try {
    logger.trace("Starting WebServer.")

    // bind to the requested port.
    val server = HttpServer.create(new InetSocketAddress(ctx.options.http.port), 0)

    // mount ajax handlers.
    server.createContext("/api/status", new StateHandler())

    // mount file handler.
    server.createContext("/", new FileHandler())

    // start server.
    server.setExecutor(null)
    server.start()
  } catch {
    case e: IOException =>
      logger.error(s"Unable to start web server. The REST API will not be available. Error: ${e.getMessage}", e)
  }

}
