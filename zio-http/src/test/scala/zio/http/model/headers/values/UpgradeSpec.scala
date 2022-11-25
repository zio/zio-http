package zio.http.model.headers.values

import zio.Scope
import zio.test.{Spec, TestEnvironment, ZIOSpecDefault, assertTrue}

object UpgradeSpec extends ZIOSpecDefault {
  override def spec: Spec[TestEnvironment with Scope, Any] = suite("Upgrade suite")()
  suite("Upgrade header value transformation should be symmetrical")(
    test("single value") {
      assertTrue(Upgrade.fromUpgrade(Upgrade.toUpgrade("h2c")) == "h2c")
    },
    test("multiple values") {
      assertTrue(
        Upgrade.fromUpgrade(
          Upgrade.toUpgrade("HTTP/2.0, SHTTP/1.3, IRC/6.9, RTA/x11"),
        ) == "HTTP/2.0, SHTTP/1.3, IRC/6.9, RTA/x11",
      )
    },
  )
}
