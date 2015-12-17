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

/**
 * A common super-type for all Ast nodes.
 */
sealed trait Ast

/**
 * The Ast for the uMonitor5 language.
 */
object Ast {

  /**
   * The root AST node which is a sequence of declarations.
   */
  case class Root(decls: Seq[Declaration]) extends Ast

  /**
   * An AST node representing an identifier.
   */
  case class Ident(name: String, location: SourceLocation) extends Ast

  /**
   * A common super-type for AST node representing declarations.
   */
  sealed trait Declaration extends Ast

  object Declaration {

    /**
     * An AST node representing an action declaration.
     */
    case class Action(ident: Ast.Ident, run: Seq[Ast.Action.Run]) extends Declaration

    /**
     * An AST node representing a contact declaration.
     */
    case class Contact(ident: Ast.Ident, properties: Seq[Ast.Property]) extends Declaration

    /**
     * An AST node representing a monitor declaration.
     */
    case class Monitor(ident: Ast.Ident, body: Seq[Ast.Declaration]) extends Declaration

    /**
     * An AST node representing a service declaration.
     */
    case class Service(ident: Ast.Ident, connector: Connector) extends Declaration

    /**
     * An AST node representing a monitor-service binding.
     */
    case class Bind(service: Ast.Ident, monitor: Ast.Ident, name: Ast.Ident) extends Declaration

    /**
     * An AST node representing a contact-monitor binding.
     */
    case class Notify(contact: Ast.Ident, target: Ast.Ident) extends Declaration

  }

  /**
   * Action components.
   */
  object Action {

    /**
     * An AST node representing a run declaration.
     */
    case class Run(properties: Seq[Ast.Property]) extends Declaration

  }

  /**
   * Monitor components.
   */
  object Monitor {

    /**
     * An AST node representing a set of states.
     */
    case class States(idents: Seq[Ast.Ident]) extends Ast.Declaration

    /**
     * An AST node representing a set of clocks.
     */
    case class Clocks(idents: Seq[Ast.Ident]) extends Ast.Declaration

    /**
     * Ast AST node representing a collection of transitions.
     */
    case class When(ident: Ast.Ident, transitions: Seq[Transition]) extends Ast.Declaration

  }

  /**
   * A common super-type for AST nodes representing connector declarations.
   */
  sealed trait Connector extends Ast

  object Connector {

    /**
     * An AST node representing a disjunction of sub-connectors.
     */
    case class OneOf(xs: Seq[Connector]) extends Connector

    /**
     * An AST node representing a conjunction of sub-connectors.
     */
    case class AllOf(xs: Seq[Connector]) extends Connector

    /**
     * An AST node representing a command connector.
     */
    case class Cmd(properties: Seq[Property]) extends Connector

    /**
     * An AST node representing a DNS connector.
     */
    case class Dns(properties: Seq[Property]) extends Connector

    /**
     * An AST node representing a File connector.
     */
    case class File(properties: Seq[Property]) extends Connector

    /**
     * An AST node representing a FTP connector.
     */
    case class Ftp(properties: Seq[Property]) extends Connector

    /**
     * An AST node representing a HTTP connector.
     */
    case class Http(properties: Seq[Property]) extends Connector

    /**
     * An AST node representing an ICMP connector.
     */
    case class Icmp(properties: Seq[Property]) extends Connector

    /**
     * An AST node representing an IMAP connector.
     */
    case class Imap(properties: Seq[Property]) extends Connector

    /**
     * An AST node representing an Pop3 connector.
     */
    case class Pop3(properties: Seq[Property]) extends Connector

    /**
     * An AST node representing an RDP connector.
     */
    case class Rdp(properties: Seq[Property]) extends Connector

    /**
     * An AST node representing an SMTP connector.
     */
    case class Smtp(properties: Seq[Property]) extends Connector

    /**
     * An AST node representing an SSH connector.
     */
    case class Ssh(properties: Seq[Property]) extends Connector

    /**
     * An AST node representing an SSHx connector.
     */
    case class SshCmd(properties: Seq[Property]) extends Connector

  }

  /**
   * An AST node representing state transitions.
   */
  case class Transition(event: Event, guards: Seq[Guard], dst: Ast.Ident, resets: Seq[Ast.Ident], actions: Seq[Ast.Ident]) extends Ast

  /**
   * A common super-type for AST node representing events.
   */
  sealed trait Event

  object Event {

    /**
     * An AST node representing an up event with the given `ident.`
     */
    case class Up(ident: Ast.Ident) extends Event

    /**
     * An AST node representing a down event with the given `ident.`
     */
    case class Dn(ident: Ast.Ident) extends Event

  }

  /**
   * A common super-type for guards.
   */
  trait Guard

  object Guard {

    /**
     * An AST node representing a clock guard.
     */
    case class Clock(ident: Ast.Ident, d: Duration) extends Guard

    /**
     * An AST node representing a time guard.
     */
    case class TimeOfDay(begin: HourMin, end: HourMin) extends Guard

    /**
     * An AST node representing a day of week guard.
     */
    case class DayOfWeek(days: Seq[Ast.DayOfWeek]) extends Guard

  }

  /**
   * A common super-type for AST nodes representing durations.
   */
  sealed trait Duration

  object Duration {

    /**
     * An AST node representing a duration of `t` seconds.
     */
    case class Second(t: Int) extends Duration

    /**
     * An AST node representing a duration of `t` minutes.
     */
    case class Minute(t: Int) extends Duration

    /**
     * An AST node representing a duration of `t` hours.
     */
    case class Hour(t: Int) extends Duration

    /**
     * An AST node representing a duration of `t` days.
     */
    case class Day(t: Int) extends Duration

    /**
     * An AST node representing a duration of `t` weeks.
     */
    case class Week(t: Int) extends Duration

  }

  /**
   * An AST node representing an hour and minute coupled with the time of day.
   */
  case class HourMin(hour: Int, min: Int, ampm: TimeOfDay)

  /**
   * A common super-type for the time of day, i.e. at morning (AM) or after midday (PM).
   */
  sealed trait TimeOfDay

  object TimeOfDay {

    /**
     * At Morning (AM).
     */
    case object AM extends TimeOfDay

    /**
     * After Midday (PM).
     */
    case object PM extends TimeOfDay

  }

  /**
   * A common super-type for AST nodes representing a day of the week.
   */
  sealed trait DayOfWeek extends Guard

  object DayOfWeek {

    /**
     * An AST node representing Monday.
     */
    case object Monday extends DayOfWeek

    /**
     * An AST node representing Tuesday.
     */
    case object Tuesday extends DayOfWeek

    /**
     * An AST node representing Wednesday.
     */
    case object Wednesday extends DayOfWeek

    /**
     * An AST node representing Thursday.
     */
    case object Thursday extends DayOfWeek

    /**
     * An AST node representing Friday.
     */
    case object Friday extends DayOfWeek

    /**
     * An AST node representing Saturday.
     */
    case object Saturday extends DayOfWeek

    /**
     * An AST node representing Sunday.
     */
    case object Sunday extends DayOfWeek

  }

  /**
   * A common super-type for AST nodes representing literals.
   */
  sealed trait Literal

  object Literal {

    /**
     * An AST node representing a boolean literal.
     */
    case class Bool(literal: scala.Boolean) extends Literal

    /**
     * An AST node representing an integer literal.
     */
    case class Int(literal: scala.Int) extends Literal

    /**
     * An AST node representing a string literal.
     */
    case class Str(literal: java.lang.String) extends Literal

    /**
     * An AST node representing a sequence of literals.
     */
    case class Seq(xs: scala.Seq[Ast.Literal]) extends Literal

  }

  /**
   * An AST node representing a property declaration.
   */
  case class Property(ident: Ast.Ident, literal: Literal) extends Ast

}
