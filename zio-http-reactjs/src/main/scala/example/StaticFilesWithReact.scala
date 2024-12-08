package example

import zio._

import zio.http._

object StaticFilesWithReact extends ZIOAppDefault {

  /**
   * Creates an HTTP app that only serves static files from resources via
   * "/static". For paths other than the resources directory, see
   * [[zio.http.Middleware.serveDirectory]].
   */
  val routes = Routes.empty @@ Middleware.serveResources(Path.empty / "build", "build")

  override def run = Server.serve(routes).provide(Server.defaultWithPort(8090))
}
