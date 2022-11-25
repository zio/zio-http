package zio.http.model.headers.values

import zio.{Chunk, Scope}
import zio.test.{Spec, TestEnvironment, ZIOSpecDefault, assertTrue}

object ViaSpec extends ZIOSpecDefault {
  override def spec: Spec[TestEnvironment with Scope, Any] = suite("Via suite")(
    test("parsing of valid values") {
      assertTrue(
        Via.toVia("1.0 fred, 1.1 nowhere.com (Apache/1.1)") == Via.ViaValues(
          Chunk(
            Via.ViaValue("1.0", comment = Some("fred")),
            Via.ViaValue("1.1", Some("nowhere.com (Apache/1.1)")),
          ),
        ),
      )
    },
  )
}
