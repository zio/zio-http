package zhttp.http

import io.netty.util.CharsetUtil
import zio.Chunk
import zio.test.Assertion._
import zio.test._

object GetBodyAsStringSpec extends DefaultRunnableSpec {

  def spec = suite("getBodyAsString")(
    test("should map bytes according to charset encoding UTF-8") {
      val data                            = Chunk.fromArray("abc".getBytes(HTTP_CHARSET))
      val content: HttpData[Any, Nothing] = HttpData.CompleteData(data)
      val request                         = Request(endpoint = Method.GET -> URL(Path("/")), content = content)
      val encoded                         = request.getBodyAsString
      val actual                          = Option(new String(data.toArray, HTTP_CHARSET))
      assert(actual)(equalTo(encoded))
    },
    test("should map bytes according to charset encoding ISO_8859_1") {
      val data                            = Chunk.fromArray("abc".getBytes(CharsetUtil.ISO_8859_1))
      val content: HttpData[Any, Nothing] = HttpData.CompleteData(data)
      val request                         = Request(endpoint = Method.GET -> URL(Path("/")), content = content)
      val encoded                         = request.getBodyAsString
      val actual                          = Option(new String(data.toArray, CharsetUtil.ISO_8859_1))
      assert(actual)(equalTo(encoded))
    },
    test("should map bytes according to charset encoding US_ASCII") {
      val data                            = Chunk.fromArray("abc".getBytes(CharsetUtil.US_ASCII))
      val content: HttpData[Any, Nothing] = HttpData.CompleteData(data)
      val request                         = Request(endpoint = Method.GET -> URL(Path("/")), content = content)
      val encoded                         = request.getBodyAsString
      val actual                          = Option(new String(data.toArray, CharsetUtil.US_ASCII))
      assert(actual)(equalTo(encoded))
    },
    test("should map bytes according to charset encoding UTF-16") {
      val b                               = new String("abc".getBytes(CharsetUtil.UTF_16), CharsetUtil.UTF_16)
      val data                            = Chunk.fromArray(b.getBytes())
      val content: HttpData[Any, Nothing] = HttpData.CompleteData(data)
      val request                         = Request(endpoint = Method.GET -> URL(Path("/")), content = content)
      val encoded                         = request.getBodyAsString
      val actual                          = Option(b)
      assert(actual)(equalTo(encoded))
    },
    test("should map bytes to default if no charset given") {
      val data                            = Chunk.fromArray("abc".getBytes())
      val content: HttpData[Any, Nothing] = HttpData.CompleteData(data)
      val request                         = Request(endpoint = Method.GET -> URL(Path("/")), content = content)
      val encoded                         = request.getBodyAsString
      val actual                          = Option(data.map(_.toChar).mkString)
      assert(actual)(equalTo(encoded))
    },
  )
}
