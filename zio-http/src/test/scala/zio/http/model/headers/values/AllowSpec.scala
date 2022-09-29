package zio.http.model.headers.values

import zio.http.internal.HttpGen
import zio.http.model.headers.values.Allow._
import zio.test._
import zio.{Chunk, Scope}

object AllowSpec extends ZIOSpecDefault {
  override def spec: Spec[TestEnvironment with Scope, Any] = suite("Allow header suite")(
    test("allow header transformation must be symmetrical") {
      check(HttpGen.allowHeader) { allowHeader =>
        assertTrue(Allow.toAllow(Allow.fromAllow(allowHeader)) == allowHeader)
      }
    },
    test("empty header value should be parsed to an empty chunk") {
      assertTrue(Allow.toAllow("") == AllowMethods(Chunk.empty))
    },
    test("invalid values parsing") {
      check(Gen.stringBounded(10, 15)(Gen.char)) { value =>
        assertTrue(Allow.toAllow(value) == AllowMethods(Chunk(InvalidAllowMethod)))
      }
    },
  )
}
