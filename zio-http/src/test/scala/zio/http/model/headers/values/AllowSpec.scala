package zio.http.model.headers.values

import zio.Scope
import zio.http.internal.HttpGen
import zio.test._

object AllowSpec extends ZIOSpecDefault {
  override def spec: Spec[TestEnvironment with Scope, Any] = suite("Allow header suite")(
    test("allow header transformation must be symmetrical") {
      check(Gen.chunkOfBounded(1, 6)(HttpGen.method).map(Allow.apply)) { allowHeader =>
        assertTrue(Allow.toAllow(Allow.fromAllow(allowHeader)) == allowHeader)
      }
    }
  )
}
