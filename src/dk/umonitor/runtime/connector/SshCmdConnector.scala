/*
 * Copyright (c) 2015. Magnus Madsen.
 *
 * This file is part of the uMonitor project.
 *
 * Source code available under the MIT license. See LICENSE.md for details.
 */

package dk.umonitor.runtime.connector

import java.time.LocalDateTime

import com.jcraft.jsch.{ChannelExec, JSch, JSchException}
import dk.umonitor.runtime.Event
import dk.umonitor.util.PropertyMap

import scala.io.Source

object SshCmdConnector {

  val DefaultPort = 22

  def connect(name: String, hostname: String, port: Option[Int], username: String, password: String, command: String, exitCode: Option[Int], goodWords: Set[String], badWords: Set[String], opts: PropertyMap = PropertyMap.empty): Event = try {
    val ssh = new JSch()

    val session = ssh.getSession(username, hostname, port.getOrElse(DefaultPort))
    session.setConfig("StrictHostKeyChecking", "no")
    session.setPassword(password)
    session.connect()

    val channel = session.openChannel("exec").asInstanceOf[ChannelExec]
    channel.setCommand(command)
    channel.setInputStream(null)
    channel.setOutputStream(null)
    channel.setErrStream(null)

    channel.connect()

    val in = channel.getInputStream
    val output = Source.fromInputStream(in).getLines()

    channel.disconnect()
    session.disconnect()

    if (exitCode.nonEmpty) {
      val actualExitCode = channel.getExitStatus
      if (exitCode.get != actualExitCode) {
        return Event.Dn(name, LocalDateTime.now(), s"Unexpected return code. Expected $exitCode. Got $exitCode.")
      }
    }

    var foundGoodWords: Set[String] = Set.empty
    var foundBadWords: Set[String] = Set.empty
    for (line <- output) {
      foundGoodWords = foundGoodWords ++ goodWords.filter(goodWord => line.contains(goodWord))
      foundBadWords = foundBadWords ++ badWords.filter(badWord => line.contains(badWord))
    }

    if (foundBadWords.nonEmpty)
      return Event.Dn(name, LocalDateTime.now(), "Found Bad Words: " + foundBadWords.mkString(", "))

    val missingGoodWords = goodWords -- foundGoodWords
    if (missingGoodWords.nonEmpty) {
      return Event.Dn(name, LocalDateTime.now(), "Missing Good Words: " + missingGoodWords.mkString(", "))
    }
    Event.Up(name, LocalDateTime.now())
  } catch {
    case e: JSchException => Event.Dn(name, LocalDateTime.now(), e.getMessage, Some(e))
  }

}