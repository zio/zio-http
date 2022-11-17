package zio.http.model.headers.values

import zio.Scope
import zio.http.model.headers.values.From.InvalidFromValue
import zio.http.model.headers.values.Trailer.{InvalidTrailerValue, TrailerValue}
import zio.test._

object TrailerSpec extends ZIOSpecDefault {
  override def spec: Spec[TestEnvironment with Scope, Nothing] =
    suite("Trailer header suite")(
      test("parse valid value") {
        assertTrue(Trailer.toTrailer("Trailer") == TrailerValue("trailer")) &&
          assertTrue(Trailer.toTrailer("Max-Forwards") == TrailerValue("max-forwards")) &&
          assertTrue(Trailer.toTrailer("Cache-Control") == TrailerValue("cache-control")) &&
          assertTrue(Trailer.toTrailer("Content-Type") == TrailerValue("content-type"))
      },
      test("parse invalid value") {
        assertTrue(Trailer.toTrailer(" ") == InvalidTrailerValue) &&
          assertTrue(Trailer.toTrailer("Some Value") == InvalidTrailerValue) &&
          assertTrue(Trailer.toTrailer("Cache-Control ") == InvalidTrailerValue)
      },
    )
}
