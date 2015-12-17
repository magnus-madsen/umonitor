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

import dk.umonitor.util.PropertyMap

object Program {

  /**
   * An action is a name associated with a sequence of runnable actions.
   *
   * @param name the name of the action.
   * @param run a sequence of run declarations.
   * @param location the source location.
   */
  case class Action(name: String, run: Seq[Program.Run], location: SourceLocation)

  /**
   * A runnable executable.
   *
   * @param path the path to execute.
   * @param args the arguments passed to the executable.
   * @param opts extra configuration options.
   */
  case class Run(path: String, args: Seq[String], opts: PropertyMap) {
    override def toString = s"Run(path = $path, args = ${args.mkString(",")})"
  }

  /**
   * A contact is name associated with an optional e-mail address and an optional phone number.
   *
   * @param name the name of the contact.
   * @param emailAddress an optional e-mail address.
   * @param phoneNumber an optional phone number.
   * @param location the source location.
   */
  case class Contact(name: String, emailAddress: Option[String], phoneNumber: Option[String], location: SourceLocation)

  /**
   * A common super-type for symbols.
   */
  sealed trait Symbol {
    /**
     * Returns the name of the service the symbol is associated with.
     */
    def name: String
  }

  object Symbol {

    /**
     * A symbol representing an up event for the service with `name`.
     *
     * @param name the name of the service the symbol is associated with.
     */
    case class Up(name: String) extends Symbol

    /**
     * A symbol representing an up event for the service with `name`.
     *
     * @param name the name of the service the symbol is associated with.
     */
    case class Dn(name: String) extends Symbol

  }

  /**
   * A monitor is a name associated with a timed automaton.
   *
   * @param name the name of the monitor.
   * @param states the states of automaton.
   * @param clocks the clocks of automaton.
   * @param transitions the transitions of the automaton.
   * @param location the source location.
   */
  case class Monitor(name: String, states: Set[String], clocks: Set[String], transitions: Set[Transition], location: SourceLocation) {
    /**
     * Returns the monitor with every occurrence of `self` replaced by the given `name`.
     */
    def bind(name: String): Monitor = {
      val newTransitions = transitions map {
        case Program.Transition(src, Symbol.Up("self"), dst, guards, resets, actions) =>
          Program.Transition(src, Symbol.Up(name), dst, guards, resets, actions)
        case Program.Transition(src, Symbol.Dn("self"), dst, guards, resets, actions) =>
          Program.Transition(src, Symbol.Dn(name), dst, guards, resets, actions)
        case t => t
      }
      Monitor(name, states, clocks, newTransitions, location)
    }
  }

  /**
   * A service is a name associated with a connector.
   *
   * @param name the name of the service.
   * @param connector the connector associated with the name.
   * @param location the source location.
   */
  case class Service(name: String, connector: Connector, location: SourceLocation)

  /**
   * A state.
   *
   * @param name the name of the state.
   */
  case class State(name: String)

  /**
   * A binding is a name associated with a service, a monitor and a set of contacts.
   *
   * @param name the name of the binding.
   * @param service the name of the service.
   * @param monitor the name of the monitor.
   * @param location the source location.
   */
  case class Bind(name: String, service: String, monitor: String, location: SourceLocation)

  /**
   * A transition.
   *
   * @param src the source state.
   * @param symbol the symbol on the transition.
   * @param dst the destination state.
   * @param guards the guards on the transition.
   * @param resets the resets on the transition.
   * @param actions the actions on the transition.
   */
  case class Transition(src: State, symbol: Symbol, dst: State, guards: Seq[Guard], resets: Seq[String], actions: Seq[String])

  /**
   * A common super-type for connectors.
   */
  sealed trait Connector {
    override def toString: String = this match {
      case Connector.OneOf(xs) =>
        s"OneOf[${xs.mkString(", ")}]"
      case Connector.AllOf(xs) =>
        s"AllOf[${xs.mkString(", ")}]"
      case Connector.Cmd(path, exitCode, goodWords, badWords, opts) =>
        s"Cmd(path = $path)"
      case Connector.Dns(host, domain, ip, opts) =>
        s"Dns(host = $host, domain = $domain, ip = $ip)"
      case Connector.File(path, goodWords, badWords, opt) =>
        s"File(path = $path)"
      case Connector.Ftp(host, port, opts) =>
        s"Ftp(host = $host)"
      case Connector.Http(url, statusCode, goodWords, badWords, opts) =>
        s"Http(url = $url)"
      case Connector.Icmp(host, opts) =>
        s"Icmp(host = $host)"
      case Connector.Imap(host, port, opts) =>
        s"Imap(host = $host)"
      case Connector.Pop3(host, port, opts) =>
        s"Pop3(host = $host)"
      case Connector.Rdp(host, port, opts) =>
        s"Rdp(host = $host)"
      case Connector.Smtp(host, port, opts) =>
        s"Smtp(host = $host)"
      case Connector.Ssh(host, port, opts) =>
        s"Ssh(host = $host)"
      case Connector.SshCmd(host, port, username, password, command, exitCode, goodWords, badWords, opts) =>
        s"SshCmd(host = $host, command = $command)"
    }
  }

  object Connector {

    /**
     * A meta connector which succeeds if *one* of its nested connectors succeed.
     */
    case class OneOf(xs: Seq[Connector]) extends Connector

    /**
     * A meta connector which succeeds if *all* of its nested connectors succeed.
     */
    case class AllOf(xs: Seq[Connector]) extends Connector

    /**
     * A connector which executes a command on the local machine.
     *
     * @param path the path to the executable.
     * @param exitCode the expected exit code.
     * @param goodWords a set of strings which must occur in the output of the program.
     * @param badWords a set of strings which must *not* occur in the output of the program.
     * @param opts extra configuration options.
     */
    case class Cmd(path: String, exitCode: Option[Int], goodWords: Set[String], badWords: Set[String], opts: PropertyMap) extends Connector

    /**
     * A connector which queries a DNS server.
     *
     * @param host the hostname or ip of the DNS srever.
     * @param domain the query domain.
     * @param ip the expected ip for the query domain.
     * @param opts extra configuration options.
     */
    case class Dns(host: String, domain: String, ip: String, opts: PropertyMap) extends Connector

    /**
     * A connector which reads a file on the local machine.
     *
     * @param path the path to the file.
     * @param goodWords a set of strings which must occur in the file.
     * @param badWords a set of strings which must *not* occur in the file.
     * @param opts extra configuration options.
     */
    case class File(path: String, goodWords: Set[String], badWords: Set[String], opts: PropertyMap) extends Connector

    /**
     * A connector which queries an FTP server.
     *
     * @param host the hostname or ip address to connect to.
     * @param port the port to connect to.
     * @param opts extra configuration options.
     */
    case class Ftp(host: String, port: Option[Int], opts: PropertyMap) extends Connector

    /**
     * A connector which queries an internet URL.
     *
     * @param url the address to retrieve.
     * @param statusCode the expected status code.
     * @param goodWords a set of strings which must occur in the file.
     * @param badWords a set of strings which must *not* occur in the file.
     * @param opts extra configuration options.
     */
    case class Http(url: String, statusCode: Option[Int], goodWords: Set[String], badWords: Set[String], opts: PropertyMap) extends Connector

    /**
     * A connector which queries an IMAP server.
     *
     * @param host the hostname or ip address to connect to.
     * @param port the port to connect to.
     * @param opts extra configuration options.
     */
    case class Imap(host: String, port: Option[Int], opts: PropertyMap) extends Connector

    /**
     * A connector which queries whether a server responds to ICMP echo.
     *
     * @param host the hostname or ip address to connect to.
     * @param opts extra configuration options.
     */
    case class Icmp(host: String, opts: PropertyMap) extends Connector

    /**
     * A connector which queries a POP3 server.
     *
     * @param host the hostname or ip address to connect to.
     * @param port the port to connect to.
     * @param opts extra configuration options.
     */
    case class Pop3(host: String, port: Option[Int], opts: PropertyMap) extends Connector

    /**
     * A connector which queries an RDP server.
     *
     * @param host the hostname or ip address to connect to.
     * @param port the port to connect to.
     * @param opts extra configuration options.
     */
    case class Rdp(host: String, port: Option[Int], opts: PropertyMap) extends Connector

    /**
     * A connector which queries an SMTP server.
     *
     * @param host the hostname or ip address to connect to.
     * @param port the port to connect to.
     * @param opts extra configuration options.
     */
    case class Smtp(host: String, port: Option[Int], opts: PropertyMap) extends Connector

    /**
     * A connector which queries an SSH server.
     *
     * @param host the hostname or ip address to connect to.
     * @param port the port to connect to.
     * @param opts extra configuration options.
     */
    case class Ssh(host: String, port: Option[Int], opts: PropertyMap) extends Connector

    /**
     * A connector which queries an SSH server and executes a command.
     *
     * @param host the hostname or ip address to connect to.
     * @param port the port to connect to.
     * @param username the username to login with.
     * @param password the password to login with.
     * @param command the command to execute.
     * @param exitCode the expected exit code.
     * @param goodWords a set of strings which must occur in the file.
     * @param badWords a set of strings which must *not* occur in the file.
     * @param opts extra configuration options.
     */
    case class SshCmd(host: String, port: Option[Int], username: String, password: String, command: String, exitCode: Option[Int], goodWords: Set[String], badWords: Set[String], opts: PropertyMap) extends Connector

  }

  /**
   * A common super-type for transition guards.
   */
  sealed trait Guard

  object Guard {

    /**
     * A clock guard.
     */
    case class Clock(name: String, seconds: Int) extends Guard

    /**
     * A time of day guard.
     */
    case class TimeOfDay(beginHour: Int, beginMinute: Int, endHour: Int, endMinute: Int) extends Guard

    /**
     * A day of week guard.
     */
    case class DayOfWeek(days: Set[Int]) extends Guard

  }

  /**
   * A target is a name associated with a service, monitor and a set of contacts.
   *
   * @param name the name of the target.
   * @param service the service.
   * @param monitor the monitor.
   * @param contacts the set of contacts.
   * @param location the source location where the taget name was defined.
   */
  case class Target(name: String, service: Service, monitor: Monitor, contacts: Set[Contact], location: SourceLocation)

}

/**
 * An uMonitor5 program is a collection of actions, contacts, monitors and services.
 */
case class Program(actions: Map[String, Program.Action],
                   contacts: Map[String, Program.Contact],
                   monitors: Map[String, Program.Monitor],
                   services: Map[String, Program.Service],
                   targets: Seq[Program.Target])

