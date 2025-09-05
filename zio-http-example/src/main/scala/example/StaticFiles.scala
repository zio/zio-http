//> using dep "dev.zio::zio-http:3.4.1"

package example

import zio._

import zio.http._

object StaticFiles extends ZIOAppDefault {

  /**
   * Creates an HTTP app that only serves static files from resources via
   * "/static". For paths other than the resources directory, see
   * [[zio.http.Middleware.serveDirectory]].
   */
  val routes = Routes.empty @@ Middleware.serveResources(Path.empty / "static")

  override def run = Server.serve(routes).provide(Server.default)
}
