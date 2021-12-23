package zhttp.http
import zhttp.http.Headers.Literals.Name
import zhttp.internal.HttpGen
import zio.test.Assertion._
import zio.test._
object ResponseSpec extends DefaultRunnableSpec {
  def spec = suite("Response") {
    testM("should not set content length if transfer-encoding is chunked") {
      checkAll(HttpGen.streamHttpData(Gen.listOf(Gen.alphaNumericString)), Gen.anyLong) { (data, length) =>
        assert(
          Response(
            status = Status.OK,
            headers = Headers.contentLength(length),
            data = data,
          ).getHeader(Name.ContentLength),
        )(isNone)
      }
    } +
      testM("should  set content length if non stream http data") {
        checkAll(HttpGen.nonStreamHttpData(Gen.listOf(Gen.alphaNumericString))) { data =>
          assert(
            Response(
              status = Status.OK,
              data = data,
            ).getHeader(Name.ContentLength),
          )(isSome)
        }
      }
  }
}
