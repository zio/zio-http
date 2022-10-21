package zio.http.model.headers.values

import zio.http.model.headers.values.Server.ServerValue
import zio.test._

object ServerSpec extends ZIOSpecDefault {
  override def spec = suite("Server header suite")(
    test("empty server value") {
      assertTrue(Server.toServer("") == Server.EmptyServerValue) &&
      assertTrue(Server.fromServer(Server.EmptyServerValue) == "")
    },
    test("valid server values"){
      assertTrue(Server.toServer("   Apache/2.4.1   ") == ServerValue("Apache/2.4.1"))
      assertTrue(Server.toServer("tsa_b") == ServerValue("tsa_b"))
    },
    test("parsing and encoding is symmetrical") {
      assertTrue(Server.fromServer(Server.toServer("tsa_b")) == "tsa_b")
      assertTrue(Server.fromServer(Server.toServer("  ")) == "")
    },
  )
}
