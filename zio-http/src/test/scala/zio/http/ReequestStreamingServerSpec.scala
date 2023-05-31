import zio.test.Assertion.equalTo
import zio.test.{assert, checkM, suite, testM}
import zio.{Chunk, ZIO}

import zio.http._

object CLITestSuite extends DefaultRunnableSpec {

  val methods: List[Method] = List(Method.GET, Method.PUT, Method.POST, Method.DELETE)
  val paths: List[String] = List("/", "/users", "/products")
  val queryParams: List[Map[String, String]] = List(
    Map.empty[String, String],
    Map("page" -> "1", "limit" -> "10"),
    Map("sort" -> "name", "filter" -> "category")
  )
  val headers: List[List[Header]] = List(
    List.empty[Header],
    List(Header.contentType(MediaType.ApplicationJson), Header.authorization("Bearer token")),
    List(Header.userAgent("Mozilla/5.0"), Header.accept(MediaType.TextPlain))
  )
  val bodyContents: List[Option[HttpEntity]] = List(
    None,
    Some(HttpEntity.fromString("Request body")),
    Some(HttpEntity.fromByteArray(Chunk.fromArray(Array[Byte](1, 2, 3))))
  )

  val cliTestSuite = suite("CLI Test Suite")(
    testM("test CLI representation for all combinations") {
      checkM(methods, paths, queryParams, headers, bodyContents) { (method, path, queryParams, headers, bodyContent) =>
        val request = Request.make(method, path).flatMap { req =>
          val reqWithQueryParams = queryParams.foldLeft(req) { case (accReq, (key, value)) =>
            accReq.flatMap(_.withQueryParam(key, value))
          }
          val reqWithHeaders = headers.foldLeft(reqWithQueryParams) { case (accReq, header) =>
            accReq.flatMap(_.withHeaders(header))
          }
          val reqWithBody = bodyContent.foldLeft(reqWithHeaders) { case (accReq, entity) =>
            accReq.flatMap { req =>
              entity match {
                case Some(body) => req.withEntity(body)
                case None       => ZIO.succeed(req)
              }
            }
          }
          reqWithBody
        }
        val cliRepresentation = request.flatMap(Server.routeToCLI)
        assert(cliRepresentation)(equalTo(expectedCLIRepresentation(method, path, queryParams, headers, bodyContent)))
      }
    }
  )

  def expectedCLIRepresentation(
    method: Method,
    path: String,
    queryParams: Map[String, String],
    headers: List[Header],
    bodyContent: Option[HttpEntity]
  ): String = {
    // Generate the expected CLI representation based on the provided inputs
    // Return the expected CLI representation as a String
    ???
  }

  override def spec = cliTestSuite
}
