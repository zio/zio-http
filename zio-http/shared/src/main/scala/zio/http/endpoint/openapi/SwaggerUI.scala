package zio.http.endpoint.openapi

import java.net.URLEncoder

import zio.http._
import zio.http.codec.PathCodec

object SwaggerUI {

  val DefaultSwaggerUIVersion: String = "5.10.3"

  //format: off
  /**
   * Creates routes for serving the Swagger UI at the given path.
   *
   * Example:
   * {{{
   *  val routes: Routes[Any, Response] = ???
   *  val openAPIv1: OpenAPI = ???
   *  val openAPIv2: OpenAPI = ???
   *  val swaggerUIRoutes = SwaggerUI.routes("docs" / "openapi", openAPIv1, openAPIv2)
   *  val routesWithSwagger = routes ++ swaggerUIRoutes
   * }}}
   *
   * With this middleware in place, a request to `https://www.domain.com/[path]`
   * would serve the Swagger UI. The different OpenAPI specifications are served
   * at `https://www.domain.com/[path]/[title].json`. Where `title` is the title
   * of the OpenAPI specification and is url encoded.
   */
  //format: on
  def routes(path: PathCodec[Unit], api: OpenAPI, apis: OpenAPI*): Routes[Any, Response] = {
    routes(path, DefaultSwaggerUIVersion, api, apis: _*)
  }

  //format: off
  /**
   * Creates a middleware for serving the Swagger UI at the given path and with
   * the given swagger ui version.
   *
   * Example:
   * {{{
   *  val routes: Routes[Any, Response] = ???
   *  val openAPIv1: OpenAPI = ???
   *  val openAPIv2: OpenAPI = ???
   *  val swaggerUIRoutes = SwaggerUI.routes("docs" / "openapi", openAPIv1, openAPIv2)
   *  val routesWithSwagger = routes ++ swaggerUIRoutes
   * }}}
   *
   * With this middleware in place, a request to `https://www.domain.com/[path]`
   * would serve the Swagger UI. The different OpenAPI specifications are served
   * at `https://www.domain.com/[path]/[title].json`. Where `title` is the title
   * of the OpenAPI specification and is url encoded.
   */
  //format: on
  def routes(path: PathCodec[Unit], version: String, api: OpenAPI, apis: OpenAPI*): Routes[Any, Response] = {
    import zio.http.template._
    val basePath   = Method.GET / path
    val jsonRoutes = (api +: apis).map { api =>
      basePath / s"${URLEncoder.encode(api.info.title, Charsets.Utf8.name())}.json" -> handler { (_: Request) =>
        Response.json(api.toJson)
      }
    }
    val jsonPaths  = jsonRoutes.map(_.routePattern.pathCodec.render)
    val jsonTitles = (api +: apis).map(_.info.title)
    val jsonUrls   = jsonTitles.zip(jsonPaths).map { case (title, path) => s"""{url: "$path", name: "$title"}""" }
    val uiRoute    = basePath -> handler { (_: Request) =>
      Response.html(
        html(
          head(
            meta(charsetAttr := "utf-8"),
            meta(nameAttr    := "viewport", contentAttr    := "width=device-width, initial-scale=1"),
            meta(nameAttr    := "description", contentAttr := "SwaggerUI"),
            title("SwaggerUI"),
            link(relAttr := "stylesheet", href := s"https://unpkg.com/swagger-ui-dist@$version/swagger-ui.css"),
            link(
              relAttr    := "icon",
              typeAttr   := "image/png",
              href       := s"https://unpkg.com/swagger-ui-dist@$version/favicon-32x32.png",
            ),
          ),
          body(
            div(id         := "swagger-ui"),
            script(srcAttr := s"https://unpkg.com/swagger-ui-dist@$version/swagger-ui-bundle.js"),
            script(srcAttr := s"https://unpkg.com/swagger-ui-dist@$version/swagger-ui-standalone-preset.js"),
            Dom.raw(s"""<script>
                       |window.onload = () => {
                       |  window.ui = SwaggerUIBundle({
                       |    urls: ${jsonUrls.mkString("[\n", ",\n", "\n]")},
                       |    dom_id: '#swagger-ui',
                       |    presets: [
                       |      SwaggerUIBundle.presets.apis,
                       |      SwaggerUIStandalonePreset
                       |    ],
                       |    layout: "StandaloneLayout",
                       |  });
                       |};
                       |</script>""".stripMargin),
          ),
        ),
      )
    }
    Routes.fromIterable(jsonRoutes) :+ uiRoute
  }

}
