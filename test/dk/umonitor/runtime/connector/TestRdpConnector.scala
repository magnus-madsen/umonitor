package dk.umonitor.runtime.connector

import dk.umonitor.runtime.Event
import org.scalatest.FunSuite
import org.scalatest.concurrent.Timeouts
import org.scalatest.time.SpanSugar._

class TestRdpConnector extends FunSuite with Timeouts {

  test("RDP 74.125.202.109") {
    val event = RdpConnector.connect("name", "74.125.202.109")
    assert(event.isInstanceOf[Event.Up])
  }

  test("RDP pop.gmail.com") {
    val event = RdpConnector.connect("name", "pop.gmail.com")
    assert(event.isInstanceOf[Event.Up])
  }

  test("RDP Dead IP") {
    failAfter((2 * TcpConnector.DefaultConnectTimeout).millis) {
      val event = RdpConnector.connect("name", "127.0.0.1")
      assert(event.isInstanceOf[Event.Dn])
    }
  }

  test("RDP Dead Domain") {
    failAfter((2 * TcpConnector.DefaultConnectTimeout).millis) {
      val event = RdpConnector.connect("name", "localhost")
      assert(event.isInstanceOf[Event.Dn])
    }
  }

}
