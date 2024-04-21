package zio.http.endpoint.openapi

import zio._
import zio.test._

import zio.http._
import zio.http.codec.HttpCodec.query
import zio.http.codec.PathCodec.path
import zio.http.endpoint.Endpoint

object SwaggerUISpec extends ZIOSpecDefault {

  override def spec: Spec[TestEnvironment with Scope, Any] =
    suite("SwaggerUI")(
      test("should return the swagger ui page") {
        val getUser = Endpoint(Method.GET / "users" / int("userId")).out[Int]

        val getUserRoute = getUser.implement { Handler.fromFunction[Int] { id => id } }

        val getUserPosts =
          Endpoint(Method.GET / "users" / int("userId") / "posts" / int("postId"))
            .query(query("name"))
            .out[List[String]]

        val getUserPostsRoute =
          getUserPosts.implement[Any] {
            Handler.fromFunctionZIO[(Int, Int, String)] { case (id1: Int, id2: Int, query: String) =>
              ZIO.succeed(List(s"API2 RESULT parsed: users/$id1/posts/$id2?name=$query"))
            }
          }

        val openAPIv1 = OpenAPIGen.fromEndpoints(title = "Endpoint Example", version = "1.0", getUser, getUserPosts)
        val openAPIv2 =
          OpenAPIGen.fromEndpoints(title = "Another Endpoint Example", version = "2.0", getUser, getUserPosts)

        val routes =
          HttpApp(getUserRoute, getUserPostsRoute) ++ SwaggerUI.app("docs" / "openapi", openAPIv1, openAPIv2)

        val response = routes.apply(Request(method = Method.GET, url = url"/docs/openapi"))

        val expectedHtml =
          """<!DOCTYPE html><html><head><meta charset="utf-8"/><meta name="viewport" content="width=device-width, initial-scale=1"/><meta name="description" content="SwaggerUI"/><title>SwaggerUI</title><link rel="stylesheet" href="https://unpkg.com/swagger-ui-dist@5.10.3/swagger-ui.css"/><link rel="icon" type="image/png" href="https://unpkg.com/swagger-ui-dist@5.10.3/favicon-32x32.png"/></head><body><div id="swagger-ui"></div><script src="https://unpkg.com/swagger-ui-dist@5.10.3/swagger-ui-bundle.js"></script><script src="https://unpkg.com/swagger-ui-dist@5.10.3/swagger-ui-standalone-preset.js"></script><script>
            |window.onload = () => {
            |  window.ui = SwaggerUIBundle({
            |    urls: [
            |{url: "/docs/openapi/Endpoint+Example.json", name: "Endpoint Example"},
            |{url: "/docs/openapi/Another+Endpoint+Example.json", name: "Another Endpoint Example"}
            |],
            |    dom_id: '#swagger-ui',
            |    presets: [
            |      SwaggerUIBundle.presets.apis,
            |      SwaggerUIStandalonePreset
            |    ],
            |    layout: "StandaloneLayout",
            |  });
            |};
            |</script></body></html>""".stripMargin

        for {
          res  <- response
          body <- res.body.asString
        } yield {
          assertTrue(body == expectedHtml)
        }
      },
    )
}
