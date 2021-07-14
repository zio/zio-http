package zhttp.http

import zio.Chunk
import zio.test.Assertion._
import zio.test._

object GetBodyAsStringSpec extends DefaultRunnableSpec {

  def spec = suite("getBodyAsString")(
    test("should map bytes to default utf-8 if no charset given") {
      val data                            = Chunk.fromArray("abc".getBytes())
      val content: HttpData[Any, Nothing] = HttpData.CompleteData(data)
      val request                         = Request(endpoint = Method.GET -> URL(Path("/")), content = content)
      val encoded                         = request.getBodyAsString()
      val actual                          = Option(new String(data.toArray, HTTP_CHARSET))
      assert(actual)(equalTo(encoded))
    },
  )
}
