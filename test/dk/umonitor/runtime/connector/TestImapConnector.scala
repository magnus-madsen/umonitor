package dk.umonitor.runtime.connector

import dk.umonitor.runtime.Event
import org.scalatest.FunSuite
import org.scalatest.concurrent.Timeouts
import org.scalatest.time.SpanSugar._

class TestImapConnector extends FunSuite with Timeouts {

  test("IMAP 173.194.195.109") {
    val event = ImapConnector.connect("name", "173.194.195.109", Some(993))
    assert(event.isInstanceOf[Event.Up])
  }

  test("IMAP imap.gmail.com") {
    val event = ImapConnector.connect("name", "imap.gmail.com", Some(993))
    assert(event.isInstanceOf[Event.Up])
  }

  test("IMAP Dead IP") {
    failAfter((2 * TcpConnector.DefaultConnectTimeout).millis) {
      val event = ImapConnector.connect("name", "127.0.0.1", Some(993))
      assert(event.isInstanceOf[Event.Dn])
    }
  }

  test("IMAP Dead Domain") {
    failAfter((2 * TcpConnector.DefaultConnectTimeout).millis) {
      val event = ImapConnector.connect("name", "localhost", Some(993))
      assert(event.isInstanceOf[Event.Dn])
    }
  }

}
