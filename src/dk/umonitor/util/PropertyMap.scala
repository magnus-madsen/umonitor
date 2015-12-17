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

/**
 * Companion object for the [[PropertyMap]] class.
 */
object PropertyMap {
  /**
   * Returns the empty property map.
   */
  val empty: PropertyMap = PropertyMap(Map.empty)
}

/**
 * An untyped property map.
 */
case class PropertyMap(m: Map[String, Any]) {
  /**
   * Returns the boolean value corresponding to the given `key`.
   * If no such key exists (or it is of the wrong type) `default` is returned.
   */
  def getBool(key: String, default: Boolean): Boolean = m.get(key) match {
    case Some(x: Boolean) => x
    case _ => default
  }

  /**
   * Returns the integer value corresponding to the given `key`.
   * If no such key exists (or it is of the wrong type) `default` is returned.
   */
  def getInt(key: String, default: Int): Int = m.get(key) match {
    case Some(x: Int) => x
    case _ => default
  }

  /**
   * Returns the string value corresponding to the given `key`.
   * If no such key exists (or it is of the wrong type) `default` is returned.
   */
  def getStr(key: String, default: String): String = m.get(key) match {
    case Some(x: String) => x
    case _ => default
  }
}

