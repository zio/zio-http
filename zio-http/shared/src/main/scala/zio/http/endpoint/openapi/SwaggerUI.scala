package zio.http.endpoint.openapi

import java.net.URLEncoder

import zio.http._
import zio.http.codec.PathCodec

/**
 * Serves the Swagger UI, a web page that shows OpenAPI documents, together with
 * the documents as JSON.
 */
object SwaggerUI {

  /**
   * The Swagger UI version that `routes` uses when no version is given:
   * `5.10.3`.
   */
  val DefaultSwaggerUIVersion: String = "5.10.3"

  //format: off
  /**
   * Creates routes that serve the Swagger UI for the given OpenAPI documents,
   * with Swagger UI version `DefaultSwaggerUIVersion`.
   *
   * A `GET` request to `path` returns the Swagger UI HTML page. Each document
   * is served as JSON at `path` followed by one more segment: its `info.title`,
   * URL-encoded with `java.net.URLEncoder` (UTF-8), and `.json`. The title
   * `Shop API` gives the segment `Shop+API.json`. The page lists the documents
   * in the order given, and it loads the Swagger UI CSS and scripts from
   * unpkg.com. When two documents have the same title, the page lists both, and
   * their URL serves the last one.
   *
   * {{{
   * import zio.http._
   * import zio.http.codec.PathCodec
   * import zio.http.endpoint.openapi.{OpenAPI, SwaggerUI}
   *
   * val routes: Routes[Any, Response] = Routes.empty
   * val openAPIv1: OpenAPI = OpenAPI.empty.title("Shop API").version("1.0")
   * val openAPIv2: OpenAPI = OpenAPI.empty.title("Shop API v2").version("2.0")
   * val swaggerUIRoutes = SwaggerUI.routes(PathCodec.literal("docs") / "openapi", openAPIv1, openAPIv2)
   * // GET /docs/openapi, /docs/openapi/Shop+API.json, /docs/openapi/Shop+API+v2.json
   * val routesWithSwagger = routes ++ swaggerUIRoutes
   * }}}
   */
  //format: on
  def routes(path: PathCodec[Unit], api: OpenAPI, apis: OpenAPI*): Routes[Any, Response] = {
    routes(path, DefaultSwaggerUIVersion, api, apis: _*)
  }

  //format: off
  /**
   * Creates routes that serve the Swagger UI for the given OpenAPI documents,
   * with Swagger UI version `version`: the page loads the CSS and scripts of
   * `swagger-ui-dist` at that version from unpkg.com.
   *
   * A `GET` request to `path` returns the Swagger UI HTML page. Each document
   * is served as JSON at `path` followed by one more segment: its `info.title`,
   * URL-encoded with `java.net.URLEncoder` (UTF-8), and `.json`. When two
   * documents have the same title, the page lists both, and their URL serves
   * the last one.
   *
   * {{{
   * import zio.http._
   * import zio.http.codec.PathCodec
   * import zio.http.endpoint.openapi.{OpenAPI, SwaggerUI}
   *
   * val routes: Routes[Any, Response] = Routes.empty
   * val openAPIv1: OpenAPI = OpenAPI.empty.title("Shop API").version("1.0")
   * val swaggerUIRoutes = SwaggerUI.routes(PathCodec.literal("docs") / "openapi", "5.17.14", openAPIv1)
   * val routesWithSwagger = routes ++ swaggerUIRoutes
   * }}}
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
