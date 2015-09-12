package dk.umonitor

import dk.umonitor.runtime.connector.TestConnectors
import org.scalatest.Suites

class TestAll extends Suites(new TestConnectors)
