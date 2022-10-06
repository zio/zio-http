package zio.http.model.headers.values

import zio.http.internal.HttpGen
import zio.test._
import zio.Scope

object ConnectionSpec extends ZIOSpecDefault {
  override def spec: Spec[TestEnvironment with Scope, Any] = suite("Connection header suite")(
    test("connection header transformation must be symmetrical") {
      check(HttpGen.connectionHeader) { connectionHeader =>
        assertTrue(Connection.toConnection(Connection.fromConnection(connectionHeader)) == connectionHeader)
      }
    },
    test("invalid connection header value should be parsed to an empty string") {
      assertTrue(Connection.toConnection("") == Connection.InvalidConnection)
    },
    test("invalid values parsing") {
      check(Gen.stringBounded(20, 25)(Gen.char)) { value =>
        assertTrue(Connection.toConnection(value) == Connection.InvalidConnection)
      }
    },
  )
}
