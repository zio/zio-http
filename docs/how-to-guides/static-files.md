---
id: static-files
title: "Serving Static Files"
sidebar_label: "Static Files"
---

how to host static resources like images, CSS and JavaScript files using ZIO HTTP's built-in middleware.

```scala mdoc:silent
import zio._
import zio.http._

object StaticFiles extends ZIOAppDefault {

  /**
   * Creates an HTTP app that only serves static files from resources via
   * "/static". For paths other than the resources directory, see
   * [[Middleware.serveDirectory]].
   */
  val app = Routes.empty.toHttpApp @@ Middleware.serveResources(Path.empty / "static")

  override def run = Server.serve(app).provide(Server.default)
}

```