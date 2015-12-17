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
import java.nio.file.{Files, Paths}
import java.time.LocalDateTime

import dk.umonitor.runtime.Event
import dk.umonitor.util.PropertyMap

import scala.collection.JavaConverters._

object FileConnector {

  def connect(name: String, path: String, goodWords: Set[String], badWords: Set[String], opts: PropertyMap = PropertyMap.empty): Event = try {

    val p = Paths.get(path)

    if (!Files.exists(p)) {
      return Event.Dn(name, LocalDateTime.now(), s"File '$p' does not exist.")
    }

    if (!Files.isRegularFile(p)) {
      return Event.Dn(name, LocalDateTime.now(), s"File '$p' is not a regular file.")
    }

    if (!Files.isReadable(p)) {
      return Event.Dn(name, LocalDateTime.now(), s"File '$p' is not readable.")
    }

    if (Files.size(p) == 0) {
      return Event.Dn(name, LocalDateTime.now(), s"File '$p' is empty.")
    }

    var foundGoodWords = Set.empty[String]
    var foundBadWords = Set.empty[String]
    for (line <- Files.readAllLines(p).asScala) {
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

    Event.Up(name, LocalDateTime.now())
  } catch {
    case e: IOException => Event.Dn(name, LocalDateTime.now(), e.getMessage, Some(e))
  }

}
