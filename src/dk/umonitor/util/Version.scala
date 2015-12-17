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
 * Companion object for the [[Version]] class.
 */
object Version {
  /**
   * Returns the current version of the software.
   */
  val currentVersion: Version = Version(5, 0, 2)
}

/**
 * A version number is composed of a major, minor and revision number.
 *
 * @param major the major version.
 * @param minor the minor version.
 * @param revision the revision.
 */
case class Version(major: Int, minor: Int, revision: Int)