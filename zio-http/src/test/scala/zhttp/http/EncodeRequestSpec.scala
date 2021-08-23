package zhttp.http

import io.netty.handler.codec.http.HttpVersion
import zhttp.http.URL.Location
import zhttp.service.EncodeRequest
import zio.test.Assertion._
import zio.test._

object EncodeRequestSpec extends DefaultRunnableSpec with EncodeRequest {

  private def queryParamsAsString(params: Map[String, List[String]]) = params.flatMap { case (k, v) =>
    v.map(iv => k ++ "=" ++ iv)
  }.mkString("&")

  def spec = suite("EncodeRequest")(
    suite("encodeRequest")(
      test("should encode properly the request") {
        val request: Request = Request(Method.GET -> URL(Path("/"), Location.Absolute(Scheme.HTTP, "localhost", 8000)))

        val encoded = encodeRequest(jVersion = HttpVersion.HTTP_1_1, req = request)
        assert(encoded.uri())(equalTo("/"))
      },
      testM("should encode a request with query parameters") {
        val queryParamsGen =
          Gen.mapOfBounded(1, 5)(
            Gen.alphaNumericStringBounded(1, 5),
            Gen.listOfBounded(1, 5)(Gen.alphaNumericStringBounded(1, 5)),
          )
        val uriGen = Gen.zipN(Gen.alphaNumericStringBounded(1, 5), Gen.const("/"), Gen.alphaNumericStringBounded(1, 5))(
          _ ++ _ ++ _,
        )

        check(queryParamsGen, uriGen) { (queryParams, uri) =>
          val queryString                     = queryParamsAsString(queryParams)
          val requestWithQueryParams: Request =
            Request(
              Method.GET -> URL(
                Path(s"/$uri"),
                Location.Absolute(Scheme.HTTP, "localhost", 8000),
                queryParams,
              ),
            )

          val encoded = encodeRequest(jVersion = HttpVersion.HTTP_1_1, req = requestWithQueryParams)
          assert(encoded.uri())(equalTo(s"/$uri?$queryString"))
        }
      },
    ),
  )
}
