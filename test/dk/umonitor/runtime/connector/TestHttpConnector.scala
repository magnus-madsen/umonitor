package dk.umonitor.runtime.connector

import dk.umonitor.runtime.Event
import org.scalatest.FunSuite
import org.scalatest.concurrent.Timeouts

class TestHttpConnector extends FunSuite with Timeouts {

  test("HTTP www.debian.org") {
    val event = HttpConnector.connect("debian", "http://debian.org/", Some(200), Set("debian"), Set("error"))
    assert(event.isInstanceOf[Event.Up])
  }

}
