package zio.http.model.headers.values

import zio.Scope
import zio.http.internal.HttpGen
import zio.test._

object AcceptRangesSpec extends ZIOSpecDefault {
  override def spec: Spec[TestEnvironment with Scope, Nothing] =
    suite("Accept ranges header suite")(
      test("parsing valid values") {
        assertTrue(AcceptRanges.to("bytes") == AcceptRanges.Bytes) &&
        assertTrue(AcceptRanges.to("none") == AcceptRanges.None)
      },
      test("parsing invalid values") {
        assertTrue(AcceptRanges.to("") == AcceptRanges.InvalidAcceptRanges) &&
        assertTrue(AcceptRanges.to("strings") == AcceptRanges.InvalidAcceptRanges)
      },
      test("accept ranges header must be symmetrical") {
        check(HttpGen.acceptRanges) { acceptRanges =>
          assertTrue(AcceptRanges.to(AcceptRanges.from(acceptRanges)) == acceptRanges)
        }
      },
    )
}
