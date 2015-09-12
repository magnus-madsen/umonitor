package dk.umonitor.runtime.connector

import dk.umonitor.runtime.Event
import org.scalatest.FunSuite
import org.scalatest.concurrent.Timeouts

class TestDnsConnector extends FunSuite with Timeouts {

  test("DNS Good Domain and IP") {
    val event = DnsConnector.connect("name", "8.8.4.4", "www.iostream.dk", "89.221.166.136")
    assert(event.isInstanceOf[Event.Up])
  }

  test("DNS Good Domain. Bad IP.") {
    val event = DnsConnector.connect("name", "8.8.4.4", "www.iostream.dk", "127.0.0.1")
    assert(event.isInstanceOf[Event.Dn])
  }

  test("DNS Bad Domain.") {
    val event = DnsConnector.connect("name", "8.8.4.4", "www.fsdfgfdgsfdasd.com", "127.0.0.1")
    assert(event.isInstanceOf[Event.Dn])
  }

}
