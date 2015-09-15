/*
 * Copyright (c) 2015. Magnus Madsen.
 *
 * This file is part of the uMonitor project.
 *
 * Source code available under the MIT license. See LICENSE.md for details.
 */

package dk.umonitor.util

import java.io._
import java.nio.file._
import java.nio.file.attribute.BasicFileAttributes
import java.util

import scala.collection.mutable

object FileUtils {

  val FileExtension = ".um5"

  def listAll(paths: Traversable[String]): Set[Path] = listPaths(paths.map(s => Paths.get(s)))

  def listPaths(paths: Traversable[Path]): Set[Path] = paths.flatMap(listPaths).toSet

  def listPaths(path: Path): Set[Path] = {
    if (Files.isRegularFile(path) && path.getFileName.toString.endsWith(FileExtension)) {
      return Set(path)
    }

    val result = mutable.Set.empty[Path]

    Files.walkFileTree(path, new util.HashSet, 100, new FileVisitor[Path] {
      def visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult = {
        if (file.getFileName.toString.endsWith(FileExtension)) {
          result += file
        }
        FileVisitResult.CONTINUE
      }

      def visitFileFailed(file: Path, exc: IOException): FileVisitResult = FileVisitResult.CONTINUE

      def preVisitDirectory(dir: Path, attrs: BasicFileAttributes): FileVisitResult = FileVisitResult.CONTINUE

      def postVisitDirectory(dir: Path, exc: IOException): FileVisitResult = FileVisitResult.CONTINUE
    })

    result.toSet
  }
}
