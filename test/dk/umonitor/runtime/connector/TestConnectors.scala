package dk.umonitor.runtime.connector

import org.scalatest.Suites

class TestConnectors extends Suites(
  new TestDnsConnector,
  new TestFtpConnector,
  new TestHttpConnector,
  new TestImapConnector,
  new TestPingConnector,
  new TestPop3Connector,
  new TestRdpConnector)