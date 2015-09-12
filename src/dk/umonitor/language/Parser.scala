/*
 * Copyright (c) 2015. Magnus Madsen.
 *
 * This file is part of the uMonitor project.
 *
 * Source code available on the MIT license. See LICENSE.md for details.
 */

package dk.umonitor.language

import java.io.IOException
import java.nio.file.{Files, NoSuchFileException, Path}

import org.parboiled2._

import scala.io.Source
import scala.util.{Failure, Success, Try}

/**
 * Companion object for the [[Parser]] class.
 */
object Parser {

  /**
   * Returns the abstract syntax tree of the given `path`.
   */
  def parse(path: Path): Try[Ast.Root] =
    if (!Files.exists(path))
      Failure(new NoSuchFileException(s"Path '$path' does not exist."))
    else if (!Files.isReadable(path))
      Failure(new IOException(s"Path '$path' is not readable."))
    else if (!Files.isRegularFile(path))
      Failure(new IOException(s"Path '$path' is not a regular file."))
    else
      parse(Source.fromFile(path.toFile).getLines().mkString("\n"), path)

  /**
   * Returns the abstract syntax tree of the given string `input`.
   */
  def parse(input: String, path: Path): Try[Ast.Root] = {
    val parser = new Parser(input, Some(path))
    parser.Root.run() match {
      case Success(ast) => Success(ast)
      case Failure(e: ParseError) => Failure(new RuntimeException(parser.formatError(e, new ErrorFormatter(showLine = true))))
      case Failure(e) => Failure(e)
    }
  }
}

/**
 * A PEG parser for the uMonitor5 configuration language.
 */
class Parser(val input: ParserInput, val path: Option[Path] = None) extends org.parboiled2.Parser {

  // TODO: Improve use of semicolon.

  /**
   * The start non-terminal.
   */
  def Root: Rule1[Ast.Root] = rule {
    optWS ~ zeroOrMore(Declaration) ~ optWS ~ EOI ~> Ast.Root
  }

  /** *************************************************************************/
  /** Declarations                                                          ***/
  /** *************************************************************************/
  def Declaration: Rule1[Ast.Declaration] = rule {
    Action | Contact | Service | Monitor | Bind | Notify
  }

  def Action: Rule1[Ast.Declaration.Action] = rule {
    atomic("action") ~ WS ~ Ident ~ optWS ~ "{" ~ optWS ~ zeroOrMore(Run) ~ optWS ~ "}" ~ optSC ~> Ast.Declaration.Action
  }

  def Contact: Rule1[Ast.Declaration.Contact] = rule {
    atomic("contact") ~ WS ~ Ident ~ optWS ~ "{" ~ optWS ~ zeroOrMore(Property) ~ optWS ~ "}" ~ optSC ~> Ast.Declaration.Contact
  }

  def Service: Rule1[Ast.Declaration.Service] = rule {
    atomic("service") ~ WS ~ Ident ~ optWS ~ "{" ~ optWS ~ Connector ~ optWS ~ "}" ~ optSC ~> Ast.Declaration.Service
  }

  def Monitor: Rule1[Ast.Declaration.Monitor] = rule {
    atomic("monitor") ~ WS ~ Ident ~ optWS ~ "{" ~ optWS ~ zeroOrMore(MonitorComponent) ~ optWS ~ "}" ~ optSC ~> Ast.Declaration.Monitor
  }

  def Bind: Rule1[Ast.Declaration.Bind] = rule {
    atomic("bind") ~ WS ~ Ident ~ WS ~ atomic("to") ~ WS ~ Ident ~ WS ~ atomic("as") ~ WS ~ Ident ~ optSC ~> Ast.Declaration.Bind
  }

  def Notify: Rule1[Ast.Declaration.Notify] = rule {
    atomic("notify") ~ WS ~ Ident ~ WS ~ atomic("of") ~ WS ~ Ident ~ optSC ~> Ast.Declaration.Notify
  }

  /** *************************************************************************/
  /** Monitor Components                                                    ***/
  /** *************************************************************************/
  def MonitorComponent: Rule1[Ast.Declaration] = rule {
    States | Clocks | When
  }

  def States: Rule1[Ast.Monitor.States] = rule {
    atomic("states") ~ optWS ~ "{" ~ optWS ~ zeroOrMore(Ident).separatedBy(optWS ~ "," ~ optWS) ~ optSC ~ "}" ~ optWS ~> Ast.Monitor.States
  }

  def Clocks: Rule1[Ast.Monitor.Clocks] = rule {
    atomic("clocks") ~ optWS ~ "{" ~ optWS ~ zeroOrMore(Ident).separatedBy(optWS ~ "," ~ optWS) ~ optSC ~ "}" ~ optWS ~> Ast.Monitor.Clocks
  }

  def When: Rule1[Ast.Monitor.When] = rule {
    atomic("when") ~ optWS ~ Ident ~ optWS ~ "{" ~ optWS ~ zeroOrMore(Transition).separatedBy(WS) ~ optSC ~ "}" ~ optWS ~> Ast.Monitor.When
  }

  /** *************************************************************************/
  /** Action Components                                                     ***/
  /** *************************************************************************/
  def Run: Rule1[Ast.Action.Run] = rule {
    atomic("run") ~ optWS ~ "{" ~ optWS ~ zeroOrMore(Property) ~ optWS ~ "}" ~ optWS ~> Ast.Action.Run
  }

  /** *************************************************************************/
  /** Connectors                                                            ***/
  /** *************************************************************************/
  def Connector: Rule1[Ast.Connector] = rule {
    OneOfConnector | AllOfConnector | SimpleConnector
  }

  def OneOfConnector: Rule1[Ast.Connector.OneOf] = rule {
    atomic("oneOf") ~ optWS ~ "{" ~ optWS ~ zeroOrMore(Connector).separatedBy(optWS) ~ optWS ~ "}" ~ optSC ~> Ast.Connector.OneOf
  }

  def AllOfConnector: Rule1[Ast.Connector.AllOf] = rule {
    atomic("allOf") ~ optWS ~ "{" ~ optWS ~ zeroOrMore(Connector).separatedBy(optWS) ~ optWS ~ "}" ~ optSC ~> Ast.Connector.AllOf
  }

  def SimpleConnector: Rule1[Ast.Connector] = rule {
    CommandConnector | DnsConnector | FileConnector | FtpConnector | HttpConnector |
      Pop3Connector | IcmpConnector | ImapConnector | RdpConnector | SmtpConnector | SshConnector
  }

  def CommandConnector: Rule1[Ast.Connector.Cmd] = rule {
    atomic("cmd") ~ optWS ~ PropertyBody ~ optSC ~> Ast.Connector.Cmd
  }

  def DnsConnector: Rule1[Ast.Connector.Dns] = rule {
    atomic("dns") ~ optWS ~ PropertyBody ~ optSC ~> Ast.Connector.Dns
  }

  def FileConnector: Rule1[Ast.Connector.File] = rule {
    atomic("file") ~ optWS ~ PropertyBody ~ optSC ~> Ast.Connector.File
  }

  def FtpConnector: Rule1[Ast.Connector.Ftp] = rule {
    atomic("ftp") ~ optWS ~ PropertyBody ~ optSC ~> Ast.Connector.Ftp
  }

  def HttpConnector: Rule1[Ast.Connector.Http] = rule {
    atomic("http") ~ optWS ~ PropertyBody ~ optSC ~> Ast.Connector.Http
  }

  def IcmpConnector: Rule1[Ast.Connector.Icmp] = rule {
    atomic("icmp") ~ optWS ~ PropertyBody ~ optSC ~> Ast.Connector.Icmp
  }

  def ImapConnector: Rule1[Ast.Connector.Imap] = rule {
    atomic("imap") ~ optWS ~ PropertyBody ~ optSC ~> Ast.Connector.Imap
  }

  def Pop3Connector: Rule1[Ast.Connector.Pop3] = rule {
    atomic("pop3") ~ optWS ~ PropertyBody ~ optSC ~> Ast.Connector.Pop3
  }

  def RdpConnector: Rule1[Ast.Connector.Rdp] = rule {
    atomic("rdp") ~ optWS ~ PropertyBody ~ optSC ~> Ast.Connector.Rdp
  }

  def SmtpConnector: Rule1[Ast.Connector.Smtp] = rule {
    atomic("smtp") ~ optWS ~ PropertyBody ~ optSC ~> Ast.Connector.Smtp
  }

  def SshConnector: Rule1[Ast.Connector.Ssh] = rule {
    atomic("ssh") ~ optWS ~ PropertyBody ~ optSC ~> Ast.Connector.Ssh
  }

  /** *************************************************************************/
  /** Transitions                                                           ***/
  /** *************************************************************************/
  def Transition: Rule1[Ast.Transition] = rule {
    Event ~ Condition ~ "->" ~ optWS ~ Ident ~ Resets ~ Actions ~> Ast.Transition
  }

  def Event: Rule1[Ast.Event] = rule {
    UpEvent | DnEvent
  }

  def UpEvent: Rule1[Ast.Event.Up] = rule {
    atomic("Up") ~ "(" ~ Ident ~ ")" ~ WS ~> Ast.Event.Up
  }

  def DnEvent: Rule1[Ast.Event.Dn] = rule {
    atomic("Dn") ~ "(" ~ Ident ~ ")" ~ WS ~> Ast.Event.Dn
  }

  def Condition: Rule1[Seq[Ast.Guard]] = rule {
    optional("if" ~ WS ~ "(" ~ oneOrMore(Guard).separatedBy(optWS ~ "," ~ optWS) ~ ")" ~ optWS) ~> ((gs: Option[Seq[Ast.Guard]]) => gs match {
      case None => Seq.empty
      case Some(xs) => xs
    })
  }

  def Resets: Rule1[Seq[Ast.Ident]] = rule {
    optional(optWS ~ "!!" ~ optWS ~ oneOrMore(Ident).separatedBy(optWS ~ "," ~ optWS)) ~> ((rs: Option[Seq[Ast.Ident]]) => rs match {
      case None => Seq.empty
      case Some(xs) => xs
    })
  }

  def Actions: Rule1[Seq[Ast.Ident]] = rule {
    optional(optWS ~ "@@" ~ optWS ~ oneOrMore(Ident).separatedBy(optWS ~ "," ~ optWS)) ~> ((as: Option[Seq[Ast.Ident]]) => as match {
      case None => Seq.empty
      case Some(xs) => xs
    })
  }

  /** *************************************************************************/
  /** Guards                                                                ***/
  /** *************************************************************************/
  def Guard: Rule1[Ast.Guard] = rule {
    ClockGuard | TimeGuard | DayGuard
  }

  def ClockGuard: Rule1[Ast.Guard.Clock] = rule {
    Ident ~ optWS ~ ">" ~ optWS ~ Duration ~> Ast.Guard.Clock
  }

  def TimeGuard: Rule1[Ast.Guard.TimeOfDay] = rule {
    atomic("at") ~ WS ~ HourMinute ~ WS ~ "to" ~ WS ~ HourMinute ~ optWS ~> Ast.Guard.TimeOfDay
  }

  def DayGuard: Rule1[Ast.Guard.DayOfWeek] = rule {
    atomic("on") ~ WS ~ oneOrMore(DayOfWeek).separatedBy(optWS ~ "," ~ optWS) ~> Ast.Guard.DayOfWeek
  }

  def HourMinute: Rule1[Ast.HourMin] = rule {
    Digits ~ optional("." ~ Digits) ~ TimeOfDay ~>
      ((hour: Int, min: Option[Int], tod: Ast.TimeOfDay) => Ast.HourMin(hour, min.getOrElse(0), tod))
  }

  def Duration: Rule1[Ast.Duration] = rule {
    Digits ~ atomic("sec") ~> Ast.Duration.Second |
      Digits ~ atomic("min") ~> Ast.Duration.Minute |
      Digits ~ atomic("hour") ~> Ast.Duration.Hour |
      Digits ~ atomic("day") ~> Ast.Duration.Day |
      Digits ~ atomic("week") ~> Ast.Duration.Week
  }

  def TimeOfDay: Rule1[Ast.TimeOfDay] = rule {
    (atomic("am") | atomic("AM")) ~> (() => Ast.TimeOfDay.AM) |
      (atomic("pm") | atomic("PM")) ~> (() => Ast.TimeOfDay.PM)
  }

  def DayOfWeek: Rule1[Ast.DayOfWeek] = rule {
    LongDay | ShortDay
  }

  def ShortDay: Rule1[Ast.DayOfWeek] = rule {
    atomic("Mon") ~> (() => Ast.DayOfWeek.Monday) |
      atomic("Tue") ~> (() => Ast.DayOfWeek.Tuesday) |
      atomic("Wed") ~> (() => Ast.DayOfWeek.Wednesday) |
      atomic("Thu") ~> (() => Ast.DayOfWeek.Thursday) |
      atomic("Fri") ~> (() => Ast.DayOfWeek.Friday) |
      atomic("Sat") ~> (() => Ast.DayOfWeek.Saturday) |
      atomic("Sun") ~> (() => Ast.DayOfWeek.Sunday)
  }

  def LongDay: Rule1[Ast.DayOfWeek] = rule {
    atomic("Monday") ~> (() => Ast.DayOfWeek.Monday) |
      atomic("Tuesday") ~> (() => Ast.DayOfWeek.Tuesday) |
      atomic("Wednesday") ~> (() => Ast.DayOfWeek.Wednesday) |
      atomic("Thursday") ~> (() => Ast.DayOfWeek.Thursday) |
      atomic("Friday") ~> (() => Ast.DayOfWeek.Friday) |
      atomic("Saturday") ~> (() => Ast.DayOfWeek.Saturday) |
      atomic("Sunday") ~> (() => Ast.DayOfWeek.Sunday)
  }

  /** *************************************************************************/
  /** Properties                                                            ***/
  /** *************************************************************************/
  def Property: Rule1[Ast.Property] = rule {
    Ident ~ optWS ~ "=" ~ optWS ~ Literal ~ optSC ~> Ast.Property
  }

  def PropertyBody: Rule1[Seq[Ast.Property]] = rule {
    "{" ~ optWS ~ zeroOrMore(Property) ~ optWS ~ "}"
  }

  /** *************************************************************************/
  /** Literals                                                              ***/
  /** *************************************************************************/
  def Literal: Rule1[Ast.Literal] = {
    def unwrap(xs: Seq[Ast.Literal]): Ast.Literal = xs match {
      case Seq(x) => x
      case _ => Ast.Literal.Seq(xs)
    }

    rule {
      oneOrMore(SimpleLiteral).separatedBy(optWS ~ "," ~ optWS) ~> unwrap _
    }
  }

  def SimpleLiteral: Rule1[Ast.Literal] = rule {
    BoolLiteral | IntLiteral | StrLiteral
  }

  def BoolLiteral: Rule1[Ast.Literal.Bool] = rule {
    str("true") ~> (() => Ast.Literal.Bool(literal = true)) | str("false") ~> (() => Ast.Literal.Bool(literal = false))
  }

  def IntLiteral: Rule1[Ast.Literal.Int] = rule {
    capture(oneOrMore(CharPredicate.Digit)) ~> ((x: String) => Ast.Literal.Int(x.toInt))
  }

  def StrLiteral: Rule1[Ast.Literal.Str] = rule {
    "\"" ~ capture(zeroOrMore(!"\"" ~ CharPredicate.Printable)) ~ "\"" ~> Ast.Literal.Str
  }

  def Digits: Rule1[Int] = rule {
    capture(oneOrMore(CharPredicate.Digit)) ~> ((s: String) => s.toInt)
  }

  /** *************************************************************************/
  /** Identifiers                                                           ***/
  /** *************************************************************************/
  def Ident: Rule1[Ast.Ident] = {
    def LegalIdentifier: Rule1[String] = rule {
      capture(CharPredicate.Alpha ~ zeroOrMore(CharPredicate.AlphaNum | "-" | "$" | "#" | "."))
    }

    rule {
      Position ~ LegalIdentifier ~> ((position: SourceLocation, name: String) => Ast.Ident(name, position))
    }
  }

  /** *************************************************************************/
  /** WhiteSpace                                                            ***/
  /** *************************************************************************/
  def WS: Rule0 = rule {
    oneOrMore(" " | "\t" | NewLine | SingleLinecomment | MultiLineComment)
  }

  def optWS: Rule0 = rule {
    optional(WS)
  }

  def optSC: Rule0 = rule {
    optional(WS) ~ optional(";" ~ optional(WS))
  }

  def NewLine: Rule0 = rule {
    "\n" | "\r\n"
  }

  /** *************************************************************************/
  /** Comments                                                              ***/
  /** *************************************************************************/
  // Note: We must use ANY to match (consume) whatever character which is not a newline.
  // Otherwise the parser makes no progress and loops.
  def SingleLinecomment: Rule0 = rule {
    "//" ~ zeroOrMore(!NewLine ~ ANY) ~ (NewLine | EOI)
  }

  // Note: We must use ANY to match (consume) whatever character which is not a "*/".
  // Otherwise the parser makes no progress and loops.
  def MultiLineComment: Rule0 = rule {
    "/*" ~ zeroOrMore(!"*/" ~ ANY) ~ "*/"
  }

  /** *************************************************************************/
  /** Source Location                                                       ***/
  /** *************************************************************************/
  def Position: Rule1[SourceLocation] = rule {
    push(path) ~ push(org.parboiled2.Position(cursor, input)) ~> ((path: Option[Path], position: org.parboiled2.Position) => {
      SourceLocation(path, position.line, position.column)
    })
  }

}
