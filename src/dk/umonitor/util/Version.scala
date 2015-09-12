/*
 * Copyright (c) 2015. Magnus Madsen.
 *
 * This file is part of the uMonitor project.
 *
 * Source code available on the MIT license. See LICENSE.md for details.
 */

package dk.umonitor.util

/**
 * Companion object for the [[Version]] class.
 */
object Version {
  /**
   * Returns the current version of the software.
   */
  val currentVersion: Version = Version(5, 0, 0)
}

/**
 * A version number is composed of a major, minor and revision number.
 *
 * @param major the major version.
 * @param minor the minor version.
 * @param revision the revision.
 */
case class Version(major: Int, minor: Int, revision: Int)