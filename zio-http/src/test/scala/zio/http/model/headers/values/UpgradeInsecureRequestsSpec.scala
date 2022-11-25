package zio.http.model.headers.values

import zio.Scope
import zio.test.{Spec, TestEnvironment, ZIOSpecDefault, assertTrue}

object UpgradeInsecureRequestsSpec extends ZIOSpecDefault {
  override def spec: Spec[TestEnvironment with Scope, Any] = suite("UpgradeInsecureRequests suite")(
    suite("UpgradeInsecureRequests header value transformation should be symmetrical")(
      test("single value") {
        assertTrue(
          UpgradeInsecureRequests.toUpgradeInsecureRequests(
            UpgradeInsecureRequests.fromUpgradeInsecureRequests(UpgradeInsecureRequests.UpgradeInsecureRequests),
          ) == UpgradeInsecureRequests.UpgradeInsecureRequests,
        )
      },
      test("multiple values") {
        assertTrue(
          UpgradeInsecureRequests.toUpgradeInsecureRequests(
            UpgradeInsecureRequests.fromUpgradeInsecureRequests(UpgradeInsecureRequests.InvalidUpgradeInsecureRequests),
          ) == UpgradeInsecureRequests.InvalidUpgradeInsecureRequests,
        )
      },
    ),
  )
}
