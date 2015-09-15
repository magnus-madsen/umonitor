/*
 * Copyright (c) 2015. Magnus Madsen.
 *
 * This file is part of the uMonitor project.
 *
 * Source code available under the MIT license. See LICENSE.md for details.
 */

package dk.umonitor.runtime.connector

import java.io.{BufferedReader, IOException, InputStreamReader}
import java.nio.charset.Charset
import java.time.LocalDateTime

import dk.umonitor.runtime.Event
import dk.umonitor.util.PropertyMap
import org.apache.http.client.ClientProtocolException
import org.apache.http.client.config.RequestConfig
import org.apache.http.client.methods.HttpGet
import org.apache.http.entity.ContentType
import org.apache.http.impl.client.{HttpClientBuilder, LaxRedirectStrategy}
import org.apache.http.impl.{DefaultConnectionReuseStrategy, NoConnectionReuseStrategy}
import org.apache.http.util.EntityUtils

import scala.collection.JavaConversions._

object HttpConnector {

  val DefaultConnectTimeout = 5000
  val DefaultReadTimeout = 5000
  val DefaultUserAgent = "Mozilla/4.0 (compatible; MSIE 8.0; Windows NT 6.0)"
  val DefaultKeepAlive = false
  val DefaultFallbackCharset = "US-ASCII"

  // Configure logging for Apache HTTP Client
  System.setProperty("org.apache.commons.logging.Log", "org.apache.commons.logging.impl.SimpleLog")
  System.setProperty("org.apache.commons.logging.simplelog.log.org.apache.http.client=", "ERROR")
  System.setProperty("org.apache.commons.logging.simplelog.log.org.apache.http.impl.conn", "ERROR")
  System.setProperty("org.apache.commons.logging.simplelog.log.org.apache.http.impl.client", "ERROR")

  def connect(name: String, url: String, statusCode: Option[Int], goodWords: Set[String], badWords: Set[String], opts: PropertyMap = PropertyMap.empty): Event = try {

    val ConnectTimeout = opts.getInt("connect-timeout", DefaultConnectTimeout)
    val ReadTimeout = opts.getInt("read-timeout", DefaultReadTimeout)
    val UserAgent = opts.getStr("user-agent", DefaultUserAgent)
    val KeepAlive = opts.getBool("keep-alive", DefaultKeepAlive)
    val FallbackCharset = opts.getStr("charset", DefaultFallbackCharset)

    val requestConfigBuilder = RequestConfig.custom()
      .setConnectTimeout(ConnectTimeout)
      .setConnectionRequestTimeout(ReadTimeout)

    val httpClientBuilder = HttpClientBuilder.create()
      .setDefaultRequestConfig(requestConfigBuilder.build())
      .setUserAgent(UserAgent)
      .setRedirectStrategy(new LaxRedirectStrategy())
      .disableAutomaticRetries()

    if (KeepAlive) {
      httpClientBuilder.setConnectionReuseStrategy(new DefaultConnectionReuseStrategy())
    } else {
      httpClientBuilder.setConnectionReuseStrategy(new NoConnectionReuseStrategy())
    }

    val client = httpClientBuilder.build()

    val response = client.execute(new HttpGet(url))

    val actualStatusCode = response.getStatusLine.getStatusCode
    val expectedStatusCode = statusCode.getOrElse(200)
    if (actualStatusCode != expectedStatusCode) {
      Event.Dn(name, LocalDateTime.now(), s"Unexpected status code: '$actualStatusCode'. Expected: '$expectedStatusCode'.")
    } else {
      val entity = response.getEntity
      val inputStream = entity.getContent
      val charset = Option(ContentType.getOrDefault(entity).getCharset).getOrElse(Charset.forName(FallbackCharset))
      val reader = new BufferedReader(new InputStreamReader(inputStream, charset))

      var foundGoodWords = Set.empty[String]
      var foundBadWords = Set.empty[String]
      for (line <- reader.lines().iterator()) {
        foundGoodWords ++= goodWords.filter(goodWord => line.contains(goodWord))
        foundBadWords ++= badWords.filter(badWord => line.contains(badWord))
      }

      if (foundBadWords.nonEmpty) {
        return Event.Dn(name, LocalDateTime.now(), "Found Bad Words: " + foundBadWords.mkString(", "))
      }

      val missingGoodWords = goodWords -- foundGoodWords
      if (missingGoodWords.nonEmpty) {
        return Event.Dn(name, LocalDateTime.now(), "Missing Good Words: " + missingGoodWords.mkString(", "))
      }

      EntityUtils.consume(entity)

      Event.Up(name, LocalDateTime.now())
    }
  } catch {
    case ex: ClientProtocolException => Event.Dn(name, LocalDateTime.now(), "Http Protocol Error: " + ex.getMessage, Some(ex));
    case ex: IOException => Event.Dn(name, LocalDateTime.now(), "I/O Error: " + ex.getMessage, Some(ex));
  }

}
