/*
 * Copyright (c) 2015. Magnus Madsen.
 *
 * This file is part of the uMonitor project.
 *
 * Source code available under the MIT license. See LICENSE.md for details.
 */

package dk.umonitor.runtime

import java.time.{LocalDate, LocalDateTime, LocalTime}

import dk.umonitor.language.Program
import dk.umonitor.language.Program.{State, Transition}

/**
 * Consumes events and maintains the state of each target.
 */
class Consumer(program: Program)(implicit ctx: Context) extends Thread {

  private val logger = ctx.logger.getLogger(classOf[Consumer])

  /**
   * A map from target names to their current state.
   */
  var states = Map.empty[String, State]

  /**
   * A map from what exactly? to clock timestamps.
   */
  var clocks = Map.empty[String, LocalDateTime]

  /**
   * A helper object to execute actions.
   */
  val executor = new Executor(program)

  /**
   * Initialize and loop.
   */
  override def run(): Unit = {
    logger.trace("Starting Consumer.")
    initialize()
    loop()
  }

  /**
   * Initialize state and clock maps.
   */
  private def initialize(): Unit = {
    for (target <- program.targets) {
      val init = target.monitor.states.head
      states = states + (target.name -> State(init))

      val time = LocalDateTime.now()
      for (clockName <- target.monitor.clocks) {
        clocks += (getClockName(target.name, clockName) -> time)
      }

      ctx.history.notifyTransition(target.name, Event.Up(target.service.name, LocalDateTime.now()), State("<<boot>>"), State(init))
    }
  }

  /**
   * Repeatedly extract and handle events from the queue.
   */
  private def loop(): Unit = {
    while (!Thread.currentThread().isInterrupted) {
      val e = ctx.queue.take()
      try {
        handleEvent(e)
      } catch {
        case e: Exception =>
          logger.error(s"Unexpected exception.", e)
          e.printStackTrace()
      }
    }
  }

  /**
   * Handles the given event `e` by taking all valid transitions in all targets.
   */
  def handleEvent(e: Event): Unit = {
    // loop through all targets.
    for (target <- program.targets) {

      // retrieve the current state.
      val currentState = states(target.name)

      // look for an enabled transition.
      val transition = lookupTransition(target.name, target.monitor, currentState, e.asSymbol)

      // transition found?
      if (transition.isDefined) {
        val Transition(sourceState, _, destinationState, _, resets, actions) = transition.get

        // update the current state.
        states = states + (target.name -> destinationState)

        // register if the state has changed.
        if (currentState != destinationState) {
          ctx.history.notifyTransition(target.name, e, sourceState, destinationState)
        }

        // reset the clocks associated with the transition.
        for (clockName <- resets) {
          clocks += (getClockName(target.name, clockName) -> e.timestamp)
        }

        // perform the actions associated with the transition.
        executor.exec(actions, e, target)
      }
    }
  }

  /**
   * Optionally returns the transition that matches the given `src` state and `symbol` and where the clock guards hold.
   */
  private def lookupTransition(targetName: String, m: Program.Monitor, src: State, symbol: Program.Symbol): Option[Transition] = {
    for (transition <- m.transitions) {
      if (transition.src == src && transition.symbol == symbol) {
        if (checkGuards(targetName, transition.guards, clocks)) {
          return Some(transition)
        }
      }
    }
    None
  }

  /**
   * Returns `true` iff all guards in the given sequence `gs` hold.
   */
  private def checkGuards(targetName: String, gs: Seq[Program.Guard], clocks: Map[String, LocalDateTime]): Boolean =
    gs.forall(g => checkGuard(targetName, g, clocks))

  /**
   * Returns `true` iff the given guard `g` hold.
   */
  private def checkGuard(targetName: String, g: Program.Guard, clocks: Map[String, LocalDateTime]): Boolean = g match {
    case Program.Guard.Clock(clockName, seconds) =>
      val name = getClockName(targetName, clockName)
      val now = LocalDateTime.now()
      val clock = clocks(name).plusSeconds(seconds)
      clock isAfter now

    case Program.Guard.DayOfWeek(days) =>
      val now = LocalDate.now()
      val day = now.getDayOfWeek.getValue
      days contains day

    case Program.Guard.TimeOfDay(beginHour, beginMinute, endHour, endMinute) =>
      val now = LocalTime.now()
      val begin = LocalTime.of(beginHour, beginMinute)
      val end = LocalTime.of(endHour, endMinute)

      (begin isBefore now) && (now isBefore end)
  }

  /**
   * Returns the qualified name for `clockName` associated with `targetName`.
   */
  private def getClockName(targetName: String, clockName: String): String =
    targetName + "." + clockName

}
