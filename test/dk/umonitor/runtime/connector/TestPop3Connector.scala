package dk.umonitor.runtime.connector

import dk.umonitor.runtime.Event
import org.scalatest.FunSuite
import org.scalatest.concurrent.Timeouts
import org.scalatest.time.SpanSugar._

class TestPop3Connector extends FunSuite with Timeouts {

  test("POP3 74.125.202.109") {
    val event = Pop3Connector.connect("name", "74.125.202.109", Some(995))
    assert(event.isInstanceOf[Event.Up])
  }

  test("POP3 pop.gmail.com") {
    val event = Pop3Connector.connect("name", "pop.gmail.com", Some(995))
    assert(event.isInstanceOf[Event.Up])
  }

  test("POP3 Dead IP") {
    failAfter((2 * TcpConnector.DefaultConnectTimeout).millis) {
      val event = Pop3Connector.connect("name", "127.0.0.1", Some(995))
      assert(event.isInstanceOf[Event.Dn])
    }
  }

  test("POP3 Dead Domain") {
    failAfter((2 * TcpConnector.DefaultConnectTimeout).millis) {
      val event = Pop3Connector.connect("name", "localhost", Some(995))
      assert(event.isInstanceOf[Event.Dn])
    }
  }

}
