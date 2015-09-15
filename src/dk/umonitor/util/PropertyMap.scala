/*
 * Copyright (c) 2015. Magnus Madsen.
 *
 * This file is part of the uMonitor project.
 *
 * Source code available under the MIT license. See LICENSE.md for details.
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

