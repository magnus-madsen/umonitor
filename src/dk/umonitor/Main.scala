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

package dk.umonitor

import dk.umonitor.language.Compiler
import dk.umonitor.runtime.{Consumer, Context, Producer, Shell}
import dk.umonitor.util.Validation.{Failure, Success}
import dk.umonitor.util.{FileUtils, Options, RestServer}

/**
 * The main class of uMonitor5.
 */
object Main {

  /**
   * The main entry point of uMonitor5.
   */
  def main(args: Array[String]): Unit = {

    implicit val ctx = new Context(Options.Default)

    // find all files (and directories) supplied on the command line.
    val paths = FileUtils.listAll(args).toList.sorted

    // compile all the input files.
    Compiler.compile(paths) match {
      case Success(program, errors) =>
        // print any errors to the console.
        errors.reverse.foreach(e => println(e.format))

        // start the event producer.
        val producer = new Producer(program)
        producer.start()

        // start the event consumer.
        val consumer = new Consumer(program)
        consumer.start()

        // start the web server for the REST api.
        val server = new RestServer
        val address = server.start()

        // add shutdown hook to warn about shutdown.
        Runtime.getRuntime.addShutdownHook(new Thread(new Runnable {
          def run(): Unit = printShutdownWarning()
        }))

        // start the interactive shell.
        // NB: control never continues past this call.
        val shell = new Shell(program, address)
        shell.startAndAwait()

      case Failure(errors) =>
        // print any errors to the console.
        errors.reverse.foreach(e => println(e.format))

        // and exit the program.
        println("Exiting due to previous errors.")
        System.exit(1)
    }

  }

  /**
   * Prints a warning that uMonitor is shutting down.
   */
  private def printShutdownWarning(): Unit = {
    Console.println()
    Console.println("################################################################################")
    Console.println("###                                                                          ###")
    Console.println("###      WARNING. WARNING. WARNING. WARNING. WARNING. WARNING. WARNING.      ###")
    Console.println("###                                                                          ###")
    Console.println("###      SHUTTING DOWN. MONITORING WILL BE UNAVAILABLE. SHUTTING DOWN.       ###")
    Console.println("###      SHUTTING DOWN. MONITORING WILL BE UNAVAILABLE. SHUTTING DOWN.       ###")
    Console.println("###      SHUTTING DOWN. MONITORING WILL BE UNAVAILABLE. SHUTTING DOWN.       ###")
    Console.println("###                                                                          ###")
    Console.println("###      DID YOU ACCIDENTALLY PRESS CTRL+C ???                               ###")
    Console.println("###                                                                          ###")
    Console.println("###      WARNING. WARNING. WARNING. WARNING. WARNING. WARNING. WARNING.      ###")
    Console.println("###                                                                          ###")
    Console.println("################################################################################")
  }

}
