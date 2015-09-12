/*
 * Copyright (c) 2015. Magnus Madsen.
 *
 * This file is part of the uMonitor project.
 *
 * Source code available on the MIT license. See LICENSE.md for details.
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
        server.start()

        // start the interactive shell.
        val shell = new Shell(program)
        shell.startAndAwait()
      case Failure(errors) =>
        // print any errors to the console.
        errors.reverse.foreach(e => println(e.format))

        // and exit the program.
        println("Exiting due to previous errors.")
        System.exit(1)
    }

  }

}
