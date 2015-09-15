/*
 * Copyright (c) 2015. Magnus Madsen.
 *
 * This file is part of the uMonitor project.
 *
 * Source code available under the MIT license. See LICENSE.md for details.
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
