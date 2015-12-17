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

import dk.umonitor.language.Compiler.CompileError._
import dk.umonitor.util.Validation._
import dk.umonitor.util.{PropertyMap, Validation}

object Compiler {

  private type Out[Result] = Validation[Result, CompileError]

  /**
   * Attempts to compile all the given `paths` into a single program.
   *
   * Emits error messages if compilation of one or more units fail.
   *
   * Returns the minimal program which is consistent.
   */
  def compile(paths: Seq[Path]): Validation[Program, CompileError] = {
    // sort the paths before parsing
    val sortedPaths = paths.sorted

    // iterate through each valid ast
    Validation.collect(sortedPaths map parse) flatMap {
      case asts =>

        // collect all declarations
        val declarations = asts flatMap (ast => ast.decls)
        val actionDecls = declarations.collect { case d: Ast.Declaration.Action => d }
        val bindDecls = declarations.collect { case d: Ast.Declaration.Bind => d }
        val contactDecls = declarations.collect { case d: Ast.Declaration.Contact => d }
        val monitorDecls = declarations.collect { case d: Ast.Declaration.Monitor => d }
        val notifyDecls = declarations.collect { case d: Ast.Declaration.Notify => d }
        val serviceDecls = declarations.collect { case d: Ast.Declaration.Service => d }

        /*
         * Actions
         */
        val actionsVal: Out[Map[String, Program.Action]] = Validation.fold(actionDecls, Map.empty[String, Program.Action]) {
          case (m, action@Ast.Declaration.Action(Ast.Ident(name, location), _)) => m.get(name) match {
            case None => // Unused action name. Compile and update map.
              Action.compileAction(action) map (result => m + (name -> result))
            case Some(otherAction) => // Existing action name. Raise duplicate error.
              DuplicateAction(name, otherAction.location, location).toFailure
          }
        }

        /*
         * Bindings
         */
        val bindingsVal: Out[Map[String, Program.Bind]] = Validation.fold(bindDecls, Map.empty[String, Program.Bind]) {
          case (m, Ast.Declaration.Bind(Ast.Ident(service, _), Ast.Ident(monitor, _), Ast.Ident(binding, location))) => m.get(binding) match {
            case None => // Unused binding name. Update map.
              (m + (binding -> Program.Bind(binding, service, monitor, location))).toSuccess
            case Some(otherTarget) => // Existing binding name. Raise duplicate error.
              DuplicateBinding(binding, otherTarget.location, location).toFailure
          }
        }

        /*
         * Contacts
         */
        val contactsVal: Out[Map[String, Program.Contact]] = Validation.fold(contactDecls, Map.empty[String, Program.Contact]) {
          case (m, contact@Ast.Declaration.Contact(Ast.Ident(name, location), properties)) => m.get(name) match {
            case None => // Unused contact name. Compile and update map.
              Contact.compileContact(contact) map (result => m + (name -> result))
            case Some(otherContact) => // Existing contact name. Raise duplicate error.
              DuplicateContact(name, otherContact.location, location).toFailure
          }
        }

        /*
         * Monitors
         */
        val monitorsVal: Out[Map[String, Program.Monitor]] = Validation.fold(monitorDecls, Map.empty[String, Program.Monitor]) {
          case (m, monitor@Ast.Declaration.Monitor(Ast.Ident(name, location), body)) => m.get(name) match {
            case None => // Unused monitor name. Compile and update map.
              Monitor.compileMonitor(monitor) map (result => m + (name -> result))
            case Some(otherMonitor) => // Existing monitor name. Raise duplicate error.
              DuplicateMonitor(name, otherMonitor.location, location).toFailure
          }
        }

        /*
         * Notifies
         */
        val notifiesVal: Out[Map[String, Set[Ast.Ident]]] = Validation.fold(notifyDecls, Map.empty[String, Set[Ast.Ident]]) {
          case (m, Ast.Declaration.Notify(contact@Ast.Ident(contactName, _), Ast.Ident(targetName, _))) => m.get(targetName) match {
            case None => (m + (targetName -> Set(contact))).toSuccess
            case Some(contacts) => (m + (targetName -> (contacts + contact))).toSuccess
          }
        }

        /*
         * Services
         */
        val servicesVal: Out[Map[String, Program.Service]] = Validation.fold(serviceDecls, Map.empty[String, Program.Service]) {
          case (m, service@Ast.Declaration.Service(Ast.Ident(name, location), connector)) => m.get(name) match {
            case None => // Unused service name. Compile and update map.
              Service.compileService(service) map (result => m + (name -> result))
            case Some(otherService) => // Existing service name. Raise duplicate error.
              DuplicateService(name, otherService.location, location).toFailure
          }
        }

        /*
         * Targets
         */
        @@(actionsVal, bindingsVal, contactsVal, monitorsVal, servicesVal) flatMap {
          case (actionMap, bindingMap, contactMap, monitorMap, serviceMap) =>

            // loop through each binding.
            val targetsVal = collect(bindingMap.values.toSeq.map {
              case Program.Bind(name, serviceName, monitorName, location) =>
                // lookup the monitor and service.
                val monitorVal = monitorMap.get(monitorName).toSuccessOr[CompileError](MonitorNotFound(monitorName, location))
                val serviceVal = serviceMap.get(serviceName).toSuccessOr[CompileError](ServiceNotFound(serviceName, location))
                // lookup the contacts.
                val contactsVal = notifiesVal flatMap {
                  case m => Validation.collect(m.getOrElse(name, Set.empty).toSeq map {
                    case Ast.Ident(contactName, notifyLocation) =>
                      contactMap.get(contactName).toSuccessOr[CompileError](ContactNotFound(contactName, notifyLocation))
                  })
                }

                // construct a target if all the components are well-defined.
                @@(contactsVal, monitorVal, serviceVal) map {
                  case (contacts, monitor, service) =>
                    val boundMonitor = monitor.bind(serviceName)
                    Program.Target(name, service, boundMonitor, contacts.toSet, location)
                }
            })

            targetsVal map {
              case targets => Program(actionMap, contactMap, monitorMap, serviceMap, targets)
            }
        }
    }
  }


  /////////////////////////////////////////////////////////////////////////////
  // Compiler Errors                                                         //
  /////////////////////////////////////////////////////////////////////////////
  sealed trait CompileError {
    /**
     * Returns a pretty formatted string of the compiler error.
     */
    def format: String
  }

  object CompileError {

    /**
     * An error that represents a parse error.
     *
     * @param message the error message
     * @param path the path of the source file.
     */
    case class ParseError(message: String, path: Path) extends CompileError {
      val format = s"Error: Unable to parse '$path' (this file will be ignored.)\n" +
        s"  $message.\n"
    }

    /**
     * An error that represents a missing property.
     *
     * @param name the name of the property.
     * @param expectedType the expected type.
     */
    case class MissingProperty(name: String, expectedType: Type) extends CompileError {
      val format =
        s"Error: Missing required property '$name'.\n" +
          s"  Expected a value of type '$expectedType'.\n"
    }

    /**
     * An error that represents a property which has a value of an incorrect type.
     *
     * @param name the name of the property.
     * @param expectedType the expected type.
     * @param actualType the actual type.
     * @param location the location of the property.
     */
    case class IllegalPropertyType(name: String, expectedType: Type, actualType: Type, location: SourceLocation) extends CompileError {
      val format =
        s"Error: Illegal property value for '$name' at '${location.format}'.\n" +
          s"  Expected a value of type '$expectedType', but got a value of type '$actualType'.\n"
    }

    /**
     * An error that represents a duplicate declaration of an action with the given `name`.
     *
     * @param name the name of the property.
     * @param location1 the location of the first declaration.
     * @param location2 the location of the second declaration.
     */
    case class DuplicateAction(name: String, location1: SourceLocation, location2: SourceLocation) extends CompileError {
      val format =
        s"Error: Duplicated action declaration '$name'.\n" +
          s"  1st declaration was here: '${location1.format}' (this one will be used).\n" +
          s"  2nd declaration was here: '${location2.format}' (this one will be ignored).\n"
    }

    /**
     * An error that represents a duplicate declaration of a binding with the given `name`.
     *
     * @param name the name of the binding.
     * @param location1 the location of the first declaration.
     * @param location2 the location of the second declaration.
     */
    case class DuplicateBinding(name: String, location1: SourceLocation, location2: SourceLocation) extends CompileError {
      val format =
        s"Error: Duplicated binding declaration '$name'.\n" +
          s"  1st declaration was here: '${location1.format}' (this one will be used).\n" +
          s"  2nd declaration was here: '${location2.format}' (this one will be ignored).\n"
    }

    /**
     * An error that represents a duplicate declaration of a contact with the given `name`.
     *
     * @param name the name of the property.
     * @param location1 the location of the first declaration.
     * @param location2 the location of the second declaration.
     */
    case class DuplicateContact(name: String, location1: SourceLocation, location2: SourceLocation) extends CompileError {
      val format =
        s"Error: Duplicated contact declaration '$name'.\n" +
          s"  1st declaration was here: '${location1.format}' (this one will be used).\n" +
          s"  2nd declaration was here: '${location2.format}' (this one will be ignored).\n"
    }

    /**
     * An error that represents a duplicate declaration of a property with the given `name`.
     *
     * @param name the name of the property.
     * @param location1 the location of the first declaration.
     * @param location2 the location of the second declaration.
     */
    case class DuplicateProperty(name: String, location1: SourceLocation, location2: SourceLocation) extends CompileError {
      val format =
        s"Error: Duplicated property declaration '$name'.\n" +
          s"  1st declaration was here: '${location1.format}' (this one will be used).\n" +
          s"  2nd declaration was here: '${location2.format}' (this one will be ignored).\n"
    }

    /**
     * An error that represents a duplicate declaration of a monitor with the given `name`.
     *
     * @param name the name of the monitor.
     * @param location1 the location of the first declaration.
     * @param location2 the location of the second declaration.
     */
    case class DuplicateMonitor(name: String, location1: SourceLocation, location2: SourceLocation) extends CompileError {
      val format =
        s"Error: Duplicated monitor declaration '$name'.\n" +
          s"  1st declaration was here: '${location1.format}' (this one will be used).\n" +
          s"  2nd declaration was here: '${location2.format}' (this one will be ignored).\n"
    }

    /**
     * An error that represents a duplicate declaration of a service with the given `name`.
     *
     * @param name the name of the service.
     * @param location1 the location of the first declaration.
     * @param location2 the location of the second declaration.
     */
    case class DuplicateService(name: String, location1: SourceLocation, location2: SourceLocation) extends CompileError {
      val format =
        s"Error: Duplicated service declaration '$name'.\n" +
          s"  1st declaration was here: '${location1.format}' (this one will be used).\n" +
          s"  2nd declaration was here: '${location2.format}' (this one will be ignored).\n"
    }

    /**
     * An error that represents that the named action will not be available due to previous errors.
     *
     * @param name the name of the action.
     * @param location the location of the declaration.
     */
    case class ActionUnavailable(name: String, location: SourceLocation) extends CompileError {
      val format =
        s"Error: The action with name '$name' declared at '${location.format}' is unavailable due to previous errors.\n"
    }

    /**
     * An error that represents that the named contact will not be available due to previous errors.
     *
     * @param name the name of the contact.
     * @param location the location of the declaration.
     */
    case class ContactUnavailable(name: String, location: SourceLocation) extends CompileError {
      val format =
        s"Error: The contact with name '$name' declared at '${location.format}' is unavailable due to previous errors.\n"
    }

    /**
     * An error that represents that the named service will not be available due to previous errors.
     *
     * @param name the name of the service.
     * @param location the location of the declaration.
     */
    case class ServiceUnavailable(name: String, location: SourceLocation) extends CompileError {
      val format =
        s"Error: The service with name '$name' declared at '${location.format}' is unavailable due to previous errors.\n"
    }

    /**
     * An error that represents a reference to an unknown contact.
     *
     * @param name the name of the contact.
     * @param location the location of the binding which refers to the monitor.
     */
    case class ContactNotFound(name: String, location: SourceLocation) extends CompileError {
      val format =
        s"Error: The contact with name '$name' referenced at '${location.format}' does not exist.\n"
    }

    /**
     * An error that represents a reference to an unknown monitor.
     *
     * @param name the name of the monitor.
     * @param location the location of the binding which refers to the monitor.
     */
    case class MonitorNotFound(name: String, location: SourceLocation) extends CompileError {
      val format =
        s"Error: The monitor with name '$name' referenced at '${location.format}' does not exist.\n"
    }

    /**
     * An error that represents a reference to an unknown service.
     *
     * @param name the name of the service.
     * @param location the location of the binding which refers to the service.
     */
    case class ServiceNotFound(name: String, location: SourceLocation) extends CompileError {
      val format =
        s"Error: The service with name '$name' referenced at '${location.format}' does not exist.\n"
    }

    /**
     * An error that represents a generic error.
     *
     * @param message the error message.
     */
    case class GenericError(message: String) extends CompileError {
      val format = message + "\n"
    }

  }

  /**
   * Returns the abstract syntax tree of the given `path`.
   */
  private def parse(path: Path): Validation[Ast.Root, CompileError] = Parser.parse(path) match {
    case scala.util.Success(root) =>
      root.toSuccess
    case scala.util.Failure(exception) =>
      ParseError(exception.getMessage, path).toFailure
  }

  /////////////////////////////////////////////////////////////////////////////
  // Actions                                                                 //
  /////////////////////////////////////////////////////////////////////////////
  object Action {

    /**
     * Returns the compiled `action`.
     */
    def compileAction(action: Ast.Declaration.Action): Out[Program.Action] = {
      Validation.flatten(action.run.map(compileRun)) map {
        case runs => Program.Action(action.ident.name, runs, action.ident.location)
      } onFailure {
        ActionUnavailable(action.ident.name, action.ident.location).toFailure
      }
    }

    /**
     * Returns the compiled `run`.
     */
    def compileRun(run: Ast.Action.Run): Out[Program.Run] = {
      getPropertyMap(run.properties) flatMap { case properties =>
        lookupStrSeq("exec", properties, optional = false) map { case command =>
          // NB: the parser guarantees that the sequence is non-empty.
          val path = command.head
          val args = command.tail
          val opts = getOpts(properties)

          Program.Run(path, args, opts)
        }
      }
    }
  }

  /////////////////////////////////////////////////////////////////////////////
  // Contacts                                                                //
  /////////////////////////////////////////////////////////////////////////////
  object Contact {
    /**
     * Returns the compiled `contact`.
     */
    def compileContact(contact: Ast.Declaration.Contact): Out[Program.Contact] =
      getPropertyMap(contact.properties) flatMap { case opts =>
        val emailAddressVal = lookupStrOpt("email", opts)
        val phoneNumberVal = lookupStrOpt("phone", opts)
        val location = contact.ident.location

        @@(emailAddressVal, phoneNumberVal) map {
          case (emailAddress, phoneNumber) =>
            Program.Contact(contact.ident.name, emailAddress, phoneNumber, location)
        }
      } onFailure {
        ContactUnavailable(contact.ident.name, contact.ident.location).toFailure
      }
  }


  /////////////////////////////////////////////////////////////////////////////
  // Connectors                                                              //
  /////////////////////////////////////////////////////////////////////////////
  object Connector {

    /**
     * Returns the compiled `connector`.
     */
    def compileConnector(connector: Ast.Connector): Out[Program.Connector] = connector match {
      case c: Ast.Connector.AllOf => compile(c)
      case c: Ast.Connector.OneOf => compile(c)
      case c: Ast.Connector.Cmd => compile(c)
      case c: Ast.Connector.Dns => compile(c)
      case c: Ast.Connector.File => compile(c)
      case c: Ast.Connector.Ftp => compile(c)
      case c: Ast.Connector.Http => compile(c)
      case c: Ast.Connector.Icmp => compile(c)
      case c: Ast.Connector.Imap => compile(c)
      case c: Ast.Connector.Pop3 => compile(c)
      case c: Ast.Connector.Rdp => compile(c)
      case c: Ast.Connector.Smtp => compile(c)
      case c: Ast.Connector.Ssh => compile(c)
      case c: Ast.Connector.SshCmd => compile(c)
    }

    /**
     * Returns the compiled allOf `connector`.
     */
    def compile(connector: Ast.Connector.AllOf): Out[Program.Connector.AllOf] = {
      val connectors = Validation.flatten(connector.xs.map(compileConnector))
      connectors map {
        case xs => Program.Connector.AllOf(xs)
      }
    }

    /**
     * Returns the compiled oneOf `connector`.
     */
    def compile(connector: Ast.Connector.OneOf): Out[Program.Connector.OneOf] = {
      val connectors = Validation.flatten(connector.xs.map(compileConnector))
      connectors map {
        case xs => Program.Connector.OneOf(xs)
      }
    }

    /**
     * Returns the compiled cmd `connector`.
     */
    def compile(connector: Ast.Connector.Cmd): Out[Program.Connector.Cmd] =
      getPropertyMap(connector.properties) flatMap { case properties =>
        val pathProp = lookupStr("path", properties)
        val exitCodeProp = lookupIntOpt("exit-code", properties)
        val goodWordsProp = lookupStrSeq("good-words", properties, optional = true)
        val badWordsProp = lookupStrSeq("bad-words", properties, optional = true)
        val opts = getOpts(properties)

        @@(pathProp, exitCodeProp, goodWordsProp, badWordsProp) map {
          case (path, exitCode, goodWords, badWords) =>
            Program.Connector.Cmd(path, exitCode, goodWords.toSet, badWords.toSet, opts)
        }
      }

    /**
     * Returns the compiled dns `connector`.
     */
    def compile(connector: Ast.Connector.Dns): Out[Program.Connector.Dns] =
      getPropertyMap(connector.properties) flatMap { case properties =>
        val hostProp = lookupStr("host", properties)
        val domainProp = lookupStr("domain", properties)
        val addressProp = lookupStr("address", properties)
        val opts = getOpts(properties)

        @@(hostProp, domainProp, addressProp) map {
          case (host, domain, address) =>
            Program.Connector.Dns(host, domain, address, opts)
        }
      }

    /**
     * Returns the compiled file `connector`.
     */
    def compile(connector: Ast.Connector.File): Out[Program.Connector.File] =
      getPropertyMap(connector.properties) flatMap { case properties =>
        val pathProp = lookupStr("path", properties)
        val goodWordsProp = lookupStrSeq("good-words", properties, optional = true)
        val badWordsProp = lookupStrSeq("bad-words", properties, optional = true)
        val opts = getOpts(properties)

        @@(pathProp, goodWordsProp, badWordsProp) map {
          case (path, goodWords, badWords) =>
            Program.Connector.File(path, goodWords.toSet, badWords.toSet, opts)
        }
      }

    /**
     * Returns the compiled ftp `connector`.
     */
    def compile(connector: Ast.Connector.Ftp): Out[Program.Connector.Ftp] =
      getPropertyMap(connector.properties) flatMap { case properties =>
        val hostProp = lookupStr("host", properties)
        val portProp = lookupIntOpt("port", properties)
        val opts = getOpts(properties)

        @@(hostProp, portProp) map {
          case (host, port) =>
            Program.Connector.Ftp(host, port, opts)
        }
      }

    /**
     * Returns the compiled http `connector`.
     */
    def compile(connector: Ast.Connector.Http): Out[Program.Connector.Http] =
      getPropertyMap(connector.properties) flatMap { case properties =>
        val urlProp = lookupStr("url", properties)
        val statusCodeProp = lookupIntOpt("status-code", properties)
        val goodWordsProp = lookupStrSeq("good-words", properties, optional = true)
        val badWordsProp = lookupStrSeq("bad-words", properties, optional = true)
        val opts = getOpts(properties)

        @@(urlProp, statusCodeProp, goodWordsProp, badWordsProp) map {
          case (url, statusCode, goodWords, badWords) =>
            Program.Connector.Http(url, statusCode, goodWords.toSet, badWords.toSet, opts)
        }
      }

    /**
     * Returns the compiled icmp `connector`.
     */
    def compile(connector: Ast.Connector.Icmp): Out[Program.Connector.Icmp] =
      getPropertyMap(connector.properties) flatMap { case properties =>
        val hostProp = lookupStr("host", properties)
        val opts = getOpts(properties)

        hostProp map {
          case host =>
            Program.Connector.Icmp(host, opts)
        }
      }

    /**
     * Returns the compiled imap `connector`.
     */
    def compile(connector: Ast.Connector.Imap): Out[Program.Connector.Imap] =
      getPropertyMap(connector.properties) flatMap { case properties =>
        val hostProp = lookupStr("host", properties)
        val portProp = lookupIntOpt("port", properties)
        val opts = getOpts(properties)

        @@(hostProp, portProp) map {
          case (host, port) =>
            Program.Connector.Imap(host, port, opts)
        }
      }

    /**
     * Returns the compiled pop3 `connector`.
     */
    def compile(connector: Ast.Connector.Pop3): Out[Program.Connector.Pop3] =
      getPropertyMap(connector.properties) flatMap { case properties =>
        val hostProp = lookupStr("host", properties)
        val portProp = lookupIntOpt("port", properties)
        val opts = getOpts(properties)

        @@(hostProp, portProp) map {
          case (host, port) =>
            Program.Connector.Pop3(host, port, opts)
        }
      }

    /**
     * Returns the compiled rdp `connector`.
     */
    def compile(connector: Ast.Connector.Rdp): Out[Program.Connector.Rdp] =
      getPropertyMap(connector.properties) flatMap { case properties =>
        val hostProp = lookupStr("host", properties)
        val portProp = lookupIntOpt("port", properties)
        val opts = getOpts(properties)

        @@(hostProp, portProp) map {
          case (host, port) =>
            Program.Connector.Rdp(host, port, opts)
        }
      }

    /**
     * Returns the compiled smtp `connector`.
     */
    def compile(connector: Ast.Connector.Smtp): Out[Program.Connector.Smtp] =
      getPropertyMap(connector.properties) flatMap { case properties =>
        val hostProp = lookupStr("host", properties)
        val portProp = lookupIntOpt("port", properties)
        val opts = getOpts(properties)

        @@(hostProp, portProp) map {
          case (host, port) =>
            Program.Connector.Smtp(host, port, opts)
        }
      }

    /**
     * Returns the compiled ssh `connector`.
     */
    def compile(connector: Ast.Connector.Ssh): Out[Program.Connector.Ssh] =
      getPropertyMap(connector.properties) flatMap { case properties =>
        val hostProp = lookupStr("host", properties)
        val portProp = lookupIntOpt("port", properties)
        val opts = getOpts(properties)

        @@(hostProp, portProp) map {
          case (host, port) =>
            Program.Connector.Ssh(host, port, opts)
        }
      }

    /**
     * Returns the compiled sshcmd `connector`.
     */
    def compile(connector: Ast.Connector.SshCmd): Out[Program.Connector.SshCmd] =
      getPropertyMap(connector.properties) flatMap { case properties =>
        val hostProp = lookupStr("host", properties)
        val portProp = lookupIntOpt("port", properties)
        val usernameProp = lookupStr("username", properties)
        val passwordProp = lookupStr("password", properties)
        val commandProp = lookupStr("command", properties)
        val exitCodeProp = lookupIntOpt("exit-code", properties)
        val goodWordsProp = lookupStrSeq("good-words", properties, optional = true)
        val badWordsProp = lookupStrSeq("bad-words", properties, optional = true)
        val opts = getOpts(properties)

        @@(hostProp, portProp, usernameProp, passwordProp, commandProp, exitCodeProp, goodWordsProp, badWordsProp) map {
          case (host, port, username, password, command, exitCode, goodWords, badWords) =>
            Program.Connector.SshCmd(host, port, username, password, command, exitCode, goodWords.toSet, badWords.toSet, opts)
        }
      }

  }

  /////////////////////////////////////////////////////////////////////////////
  // Monitors                                                                //
  /////////////////////////////////////////////////////////////////////////////
  object Monitor {

    /**
     * Returns the compiled `monitor`.
     */
    def compileMonitor(monitor: Ast.Declaration.Monitor): Out[Program.Monitor] = {
      val Ast.Declaration.Monitor(Ast.Ident(name, location), body) = monitor

      val states = body.collect({ case d: Ast.Monitor.States => d }).flatMap(_.idents).map(_.name)
      val clocks = body.collect({ case d: Ast.Monitor.Clocks => d }).flatMap(_.idents).map(_.name)
      val when = body.collect({ case d: Ast.Monitor.When => d }) flatMap {
        case Ast.Monitor.When(Ast.Ident(src, _), transitions) => transitions map {
          case transition =>
            val srcState = Program.State(src)
            val symbol = transition.event match {
              case Ast.Event.Up(Ast.Ident(monitorName, _)) => Program.Symbol.Up(monitorName)
              case Ast.Event.Dn(Ast.Ident(monitorName, _)) => Program.Symbol.Dn(monitorName)
            }
            val dstState = Program.State(transition.dst.name)
            val guards = transition.guards.map(Guard.compileGuard)
            val resets = transition.resets.map(_.name)
            val actions = transition.actions.map(_.name)

            Program.Transition(srcState, symbol, dstState, guards, resets, actions)
        }
      }

      if (states.isEmpty) {
        GenericError(s"Error: The monitor declared at '${monitor.ident.location.format}' has no states.").toFailure
      } else {
        Program.Monitor(name, states.toSet, clocks.toSet, when.toSet, location).toSuccess
      }
    }

  }

  /////////////////////////////////////////////////////////////////////////////
  // Guards                                                                  //
  /////////////////////////////////////////////////////////////////////////////
  object Guard {

    /**
     * Returns the compiled `guard`.
     */
    def compileGuard(guard: Ast.Guard): Program.Guard = guard match {
      case Ast.Guard.Clock(ident, duration) => duration match {
        case Ast.Duration.Second(seconds) => Program.Guard.Clock(ident.name, seconds)
        case Ast.Duration.Minute(minutes) => Program.Guard.Clock(ident.name, 60 * minutes)
        case Ast.Duration.Hour(hours) => Program.Guard.Clock(ident.name, 60 * 60 * hours)
        case Ast.Duration.Day(days) => Program.Guard.Clock(ident.name, 24 * 60 * 60 * days)
        case Ast.Duration.Week(weeks) => Program.Guard.Clock(ident.name, 7 * 24 * 60 * 60 * weeks)
      }

      case Ast.Guard.TimeOfDay(b, e) => (b, e) match {
        case (Ast.HourMin(beginHour, beginMin, beginAmPm), Ast.HourMin(endHour, endMin, endAmPm)) =>
          Program.Guard.TimeOfDay(beginHour + offset(beginAmPm), beginMin, endHour + offset(endAmPm), endMin)
      }

      case Ast.Guard.DayOfWeek(days) => Program.Guard.DayOfWeek(days.toSet[Ast.DayOfWeek].map {
        case Ast.DayOfWeek.Monday => 1
        case Ast.DayOfWeek.Tuesday => 2
        case Ast.DayOfWeek.Wednesday => 3
        case Ast.DayOfWeek.Thursday => 4
        case Ast.DayOfWeek.Friday => 5
        case Ast.DayOfWeek.Saturday => 6
        case Ast.DayOfWeek.Sunday => 7
      })
    }

    /**
     * Returns the time offset for the given time-of-day.
     */
    def offset(ampm: Ast.TimeOfDay): Int = ampm match {
      case Ast.TimeOfDay.AM => 0
      case Ast.TimeOfDay.PM => 12
    }
  }

  /////////////////////////////////////////////////////////////////////////////
  // Services                                                                //
  /////////////////////////////////////////////////////////////////////////////
  object Service {
    /**
     * Returns the compiled `service`.
     */
    def compileService(service: Ast.Declaration.Service): Out[Program.Service] = {
      val Ast.Ident(name, location) = service.ident

      Connector.compileConnector(service.connector) map {
        case connector => Program.Service(name, connector, location)
      } onFailure {
        ServiceUnavailable(service.ident.name, service.ident.location).toFailure
      }
    }
  }


  /////////////////////////////////////////////////////////////////////////////
  // Properties                                                              //
  /////////////////////////////////////////////////////////////////////////////
  /**
   * Returns a map from string keys to ast (identifier, literal) pairs.
   *
   * Fails the entire computation if one of the individual properties fail.
   */
  def getPropertyMap(xs: Seq[Ast.Property]): Out[Map[String, (Ast.Ident, Ast.Literal)]] = {
    Validation.fold(xs, Map.empty[String, (Ast.Ident, Ast.Literal)]) {
      case (m, Ast.Property(ident@Ast.Ident(name, location), literal)) => m.get(name) match {
        case None => // Unused action name. Add to map.
          (m + (name -> ((ident, literal)))).toSuccess[Map[String, (Ast.Ident, Ast.Literal)], CompileError]
        case Some((otherIdent, otherLiteral)) => // Existing property name. Raise duplicate error.
          DuplicateProperty(name, otherIdent.location, location).toFailure
      }
    }
  }

  /**
   * Optionally returns the string value of the given key `k` in map `m`.
   */
  private def lookupStrOpt(k: String, m: Map[String, (Ast.Ident, Ast.Literal)]): Out[Option[String]] = m.get(k) match {
    case None =>
      None.toSuccess
    case Some((Ast.Ident(name, location), Ast.Literal.Str(s))) =>
      Some(s).toSuccess
    case Some((Ast.Ident(name, location), literal)) =>
      IllegalPropertyType(name = k, expectedType = Type.Str, actualType = typeOf(literal), location = location).toFailure
  }

  /**
   * Returns the string value of the given key `k` in map `m`
   */
  private def lookupStr(k: String, m: Map[String, (Ast.Ident, Ast.Literal)]): Out[String] = m.get(k) match {
    case None => MissingProperty(k, Type.Str).toFailure
    case Some((Ast.Ident(name, location), Ast.Literal.Str(s))) => s.toSuccess
    case Some((Ast.Ident(name, location), literal)) =>
      IllegalPropertyType(name = k, expectedType = Type.Str, actualType = typeOf(literal), location = location).toFailure
  }

  /**
   * Optionally returns the int value of the given key `k` in map `m`
   */
  private def lookupIntOpt(k: String, m: Map[String, (Ast.Ident, Ast.Literal)]): Out[Option[Int]] = m.get(k) match {
    case None => None.toSuccess
    case Some((Ast.Ident(name, location), Ast.Literal.Int(i))) => Some(i).toSuccess
    case Some((Ast.Ident(name, location), literal)) =>
      IllegalPropertyType(name = k, expectedType = Type.Int, actualType = typeOf(literal), location = location).toFailure
  }

  /**
   * Returns the sequence of strings for the given key `k`.
   */
  private def lookupStrSeq(k: String, m: Map[String, (Ast.Ident, Ast.Literal)], optional: Boolean): Out[Seq[String]] = m.get(k) match {
    case None if optional => Seq.empty[String].toSuccess
    case None => MissingProperty(k, Type.Seq(Seq(Type.Str))).toFailure
    case Some((Ast.Ident(name, location), literal)) => literal match {
      case Ast.Literal.Str(x) => Seq(x).toSuccess
      case Ast.Literal.Seq(xs) => (xs map unwrap map (_.toString)).toSuccess
      case _ => IllegalPropertyType(name = k, expectedType = Type.Seq(Seq(Type.Str)), typeOf(literal), location = location).toFailure
    }
  }

  /**
   * Returns the property map of the given `properties`.
   */
  private def getOpts(properties: Map[String, (Ast.Ident, Ast.Literal)]): PropertyMap = {
    val inner = properties.foldLeft(Map.empty[String, Any]) {
      case (macc, (key, (ident, literal))) => macc + (key -> unwrap(literal))
    }
    PropertyMap(inner)
  }

  /**
   * Returns the given `literal` as a plain Scala value.
   */
  private def unwrap(literal: Ast.Literal): Any = literal match {
    case Ast.Literal.Bool(b) => b
    case Ast.Literal.Int(i) => i
    case Ast.Literal.Str(s) => s
    case Ast.Literal.Seq(xs) => xs map unwrap
  }


  /////////////////////////////////////////////////////////////////////////////
  // Types                                                                   //
  /////////////////////////////////////////////////////////////////////////////
  /**
   * A common-super type for types.
   */
  sealed trait Type

  object Type {

    /**
     * Boolean type.
     */
    case object Bool extends Type

    /**
     * Integer type.
     */
    case object Int extends Type

    /**
     * String type.
     */
    case object Str extends Type

    /**
     * Sequence type.
     */
    case class Seq(parameters: scala.Seq[Type]) extends Type

  }

  /**
   * Returns the type of the given `literal`.
   */
  def typeOf(literal: Ast.Literal): Type = literal match {
    case Ast.Literal.Bool(_) => Type.Bool
    case Ast.Literal.Int(_) => Type.Int
    case Ast.Literal.Str(_) => Type.Str
    case Ast.Literal.Seq(xs) => Type.Seq(xs map typeOf)
  }

}
