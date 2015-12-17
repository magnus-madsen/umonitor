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
