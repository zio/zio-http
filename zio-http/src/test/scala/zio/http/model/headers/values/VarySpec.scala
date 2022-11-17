package zio.http.model.headers.values

import zio.Scope
import zio.http.model.headers.values.From.InvalidFromValue
import zio.http.model.headers.values.Vary.{HeadersVaryValue, InvalidVaryValue, StarVary}
import zio.test._

object VarySpec extends ZIOSpecDefault {
  override def spec: Spec[TestEnvironment with Scope, Nothing] =
    suite("Vary header suite")(
      test("parse valid values") {
        assertTrue(Vary.toVary("*") == StarVary) &&
          assertTrue(Vary.toVary("SOMEVALUE, ANOTHERVALUE") == HeadersVaryValue(List("somevalue", "anothervalue"))) &&
          assertTrue(Vary.toVary("some,another") == HeadersVaryValue(List("some","another"))) &&
          assertTrue(Vary.toVary("some") == HeadersVaryValue(List("some")))
      },
      test("parse invalid value") {
        assertTrue(Vary.toVary(",") == InvalidVaryValue) &&
          assertTrue(Vary.toVary("") == InvalidVaryValue) &&
          assertTrue(Vary.toVary(" ") == InvalidVaryValue) &&
          assertTrue(Vary.toVary("SOMEVALUE, ANOTHERVALUE, ") == InvalidVaryValue)
      },
    )
}
