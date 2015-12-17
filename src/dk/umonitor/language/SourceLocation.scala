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

package dk.umonitor.language

import java.nio.file.Path

/**
 * A source code location.
 *
 * @param path the optional path associated with the source location.
 * @param line the line number.
 * @param column the column offset.
 */
case class SourceLocation(path: Option[Path], line: Int, column: Int) {
  /**
   * Returns a pretty formatted string of the source location.
   */
  def format: String = {
    s"${path.getOrElse("<<unknown>>")}:$line:$column"
  }
}
