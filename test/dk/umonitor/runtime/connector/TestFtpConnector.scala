package dk.umonitor.runtime.connector

import dk.umonitor.runtime.Event
import org.scalatest.FunSuite
import org.scalatest.concurrent.Timeouts
import org.scalatest.time.SpanSugar._

class TestFtpConnector extends FunSuite with Timeouts {

  test("FTP IP") {
    val event = FtpConnector.connect("name", "198.145.20.140")
    assert(event.isInstanceOf[Event.Up])
  }

  test("FTP ftp.kernel.org") {
    val event = FtpConnector.connect("name", "ftp.kernel.org")
    assert(event.isInstanceOf[Event.Up])
  }

  test("FTP Dead IP") {
    failAfter((2 * TcpConnector.DefaultConnectTimeout).millis) {
      val event = FtpConnector.connect("name", "127.0.0.1")
      assert(event.isInstanceOf[Event.Dn])
    }
  }

  test("FTP Dead Domain") {
    failAfter((2 * TcpConnector.DefaultConnectTimeout).millis) {
      val event = FtpConnector.connect("name", "localhost")
      assert(event.isInstanceOf[Event.Dn])
    }
  }

}
