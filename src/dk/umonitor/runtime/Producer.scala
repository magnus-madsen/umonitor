/*
 * Copyright (c) 2015. Magnus Madsen.
 *
 * This file is part of the uMonitor project.
 *
 * Source code available under the MIT license. See LICENSE.md for details.
 */

package dk.umonitor.runtime

import java.time.LocalDateTime
import java.util.concurrent.{Executors, TimeUnit}

import dk.umonitor.language.Program
import dk.umonitor.language.Program.Connector
import dk.umonitor.runtime.connector.{CmdConnector, FileConnector, HttpConnector, RdpConnector, _}

import scala.util.Random

/**
 * Produces events for each service.
 */
class Producer(program: Program)(implicit ctx: Context) {

  // the logger
  private val logger = ctx.logger.getLogger(getClass)

  // default number of threads in the thread pool.
  val Threads = ctx.options.producer.threads

  // default interval in which connectors are executed.
  val Period = ctx.options.producer.period

  /**
   * A thread pool in which services execute.
   */
  val pool = Executors.newScheduledThreadPool(Threads)

  /**
   * A random number generator used to schedule services at startup.
   */
  val random = new Random()

  /**
   * Starts the event producer.
   *
   * Schedules all services registered in the supplied context.
   */
  def start(): Unit = {
    logger.trace("Starting Producer.")

    for ((name, service) <- program.services) {
      // construct a runnable instance.
      val r = new Runnable {
        def run(): Unit = try {
          val event = connect(service.name, service.connector)
          ctx.queue.put(event)
          ctx.history.notifyEvent(event)
        } catch {
          case e: Exception =>
            logger.error(s"Unexpected exception.", e)
            e.printStackTrace()
        }
      }

      // compute a random delay between 0 and 59 seconds.
      val initialDelay = random.nextInt(60)

      // schedule the runnable.
      pool.scheduleAtFixedRate(r, initialDelay, Period, TimeUnit.SECONDS)
    }
  }

  /**
   * Immediately stops the event producer.
   *
   * Cancels all scheduled connectors and stops all running connectors.
   */
  def stop(): Unit = {
    pool.shutdownNow()
  }

  /**
   * Immediately attemps to connect with the given `connector`.
   */
  private def connect(name: String, connector: Connector): Event = connector match {
    /**
     * OneOf Connector.
     */
    case Program.Connector.OneOf(xs) =>
      val results = xs.map(c => connect(name, c))
      val successes = results.collect {
        case e: Event.Up => e
      }
      val failures = results.collect {
        case e: Event.Dn => e
      }
      if (successes.nonEmpty) {
        Event.Up(name, LocalDateTime.now())
      } else if (failures.size == 1) {
        failures.head
      } else {
        val message = "Multiple Failures: [" + failures.map(_.message).mkString(", ") + "]"
        Event.Dn(name, LocalDateTime.now(), message)
      }

    /**
     * AllOf Connector.
     */
    case Program.Connector.AllOf(xs) =>
      val results = xs.map(c => connect(name, c))
      val failures = results.collect {
        case e: Event.Dn => e
      }

      if (failures.isEmpty) {
        Event.Up(name, LocalDateTime.now())
      } else if (failures.size == 1) {
        failures.head
      } else {
        val message = "Multiple Failures: [" + failures.map(_.message).mkString(", ") + "]"
        Event.Dn(name, LocalDateTime.now(), message)
      }

    /**
     * Simple Connectors.
     */
    case Program.Connector.Cmd(path, exitCode, goodWords, badWords, opts) =>
      CmdConnector.connect(name, path, exitCode, goodWords, badWords, opts)

    case Program.Connector.Dns(host, domain, address, opts) =>
      DnsConnector.connect(name, host, domain, address, opts)

    case Program.Connector.File(path, goodWords, badWords, opts) =>
      FileConnector.connect(name, path, goodWords, badWords, opts)

    case Program.Connector.Ftp(host, port, opts) =>
      FtpConnector.connect(name, host, port, opts)

    case Program.Connector.Http(url, statusCode, goodWords, badWords, opts) =>
      HttpConnector.connect(name, url, statusCode, goodWords, badWords, opts)

    case Program.Connector.Icmp(host, opts) =>
      IcmpConnector.connect(name, host, opts)

    case Program.Connector.Imap(host, port, opts) =>
      ImapConnector.connect(name, host, port, opts)

    case Program.Connector.Pop3(host, port, opts) =>
      Pop3Connector.connect(name, host, port, opts)

    case Program.Connector.Rdp(host, port, opts) =>
      RdpConnector.connect(name, host, port, opts)

    case Program.Connector.Smtp(host, port, opts) =>
      SmtpConnector.connect(name, host, port, opts)

    case Program.Connector.Ssh(host, port, opts) =>
      SshConnector.connect(name, host, port, opts)

    case Program.Connector.SshCmd(host, port, username, password, command, exitCode, goodWords, badWords, opts) =>
      SshCmdConnector.connect(name, host, port, username, password, command, exitCode, goodWords, badWords, opts)
  }

}

