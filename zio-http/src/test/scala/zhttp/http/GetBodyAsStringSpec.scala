package zhttp.http

import zio.Chunk
import zio.test.Assertion._
import zio.test._

object GetBodyAsStringSpec extends DefaultRunnableSpec {

  def spec = suite("getBodyAsString")(
//    testM("should map bytes according to charset given") {
//      val genPoint: Gen[Random with Sized, Charset]                        = DeriveGen[Charset]
//      val request: ZIO[Random with Sized, Option[Nothing], Option[String]] =
//        for {
//          a <- genPoint.runCollectN(1).head
//        } yield Request(
//          endpoint = Method.GET -> URL(Path("/")),
//          content = HttpData.CompleteData(Chunk.fromArray("abc".getBytes(a))),
//        ).getBodyAsString()
//      val actual                                                           = for {
//        p <- genPoint.runCollectN(1).head
//      } yield Option(new String(Chunk.fromArray("abc".getBytes(p)).toArray, p))
//
//      assertM(actual)(equalTo(request))
//    },
    test("should map bytes to default if no charset given") {
      val data                            = Chunk.fromArray("abc".getBytes())
      val content: HttpData[Any, Nothing] = HttpData.CompleteData(data)
      val request                         = Request(endpoint = Method.GET -> URL(Path("/")), content = content)
      val encoded                         = request.getBodyAsString()
      val actual                          = Option(data.map(_.toChar).mkString)
      assert(actual)(equalTo(encoded))
    },
  )
}
