package dk.umonitor.runtime.connector

import dk.umonitor.runtime.Event
import org.scalatest.FunSuite
import org.scalatest.concurrent.Timeouts
import org.scalatest.time.SpanSugar._

class TestPingConnector extends FunSuite with Timeouts {

  test("Ping IP") {
    val event = IcmpConnector.connect("name", "127.0.0.1")
    assert(event.isInstanceOf[Event.Up])
  }

  test("Ping Domain") {
    val event = IcmpConnector.connect("name", "localhost")
    assert(event.isInstanceOf[Event.Up])
  }

  test("Ping Dead IP") {
    failAfter((2 * IcmpConnector.DefaultTimeout).millis) {
      val event = IcmpConnector.connect("name", "123.123.123.123")
      assert(event.isInstanceOf[Event.Dn])
    }
  }

  test("Ping Dead Domain") {
    failAfter((2 * IcmpConnector.DefaultTimeout).millis) {
      val event = IcmpConnector.connect("name", "example.com")
      assert(event.isInstanceOf[Event.Dn])
    }
  }

}
