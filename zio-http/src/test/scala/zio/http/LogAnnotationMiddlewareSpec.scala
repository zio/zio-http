package zio.http

import zio._
import zio.test._

object LogAnnotationMiddlewareSpec extends ZIOSpecDefault {
  override def spec: Spec[TestEnvironment with Scope, Any] =
    suite("LogAnnotationMiddlewareSpec")(
      test("add static log annotation") {
        val response = Routes
          .singleton(
            handler(ZIO.logWarning("Oh!") *> ZIO.succeed(Response.text("Hey logging!"))),
          )
          .@@(Middleware.logAnnotate("label", "value"))
          .toHttpApp
          .runZIO(Request.get("/"))

        for {
          _    <- response
          logs <- ZTestLogger.logOutput
          log = logs.filter(_.message() == "Oh!").head
        } yield assertTrue(log.annotations.get("label").contains("value"))

      },
      test("add request method and path as annotation") {
        val response = Routes
          .singleton(
            handler(ZIO.logWarning("Oh!") *> ZIO.succeed(Response.text("Hey logging!"))),
          )
          .@@(
            Middleware.logAnnotate(req =>
              Set(LogAnnotation("method", req.method.name), LogAnnotation("path", req.path.encode)),
            ),
          )
          .toHttpApp
          .runZIO(Request.get("/"))

        for {
          _    <- response
          logs <- ZTestLogger.logOutput
          log = logs.filter(_.message() == "Oh!").head
        } yield assertTrue(
          log.annotations.get("method").contains("GET"),
          log.annotations.get("path").contains("/"),
        )
      },
      test("add headers as annotation") {
        val response = Routes
          .singleton(
            handler(ZIO.logWarning("Oh!") *> ZIO.succeed(Response.text("Hey logging!"))),
          )
          .@@(Middleware.logAnnotateHeaders("header"))
          .@@(Middleware.logAnnotateHeaders(Header.UserAgent.name))
          .toHttpApp
          .runZIO {
            Request
              .get("/")
              .addHeader("header", "value")
              .addHeader(Header.UserAgent.Product("zio-http", Some("3.0.0")))
          }

        for {
          _    <- response
          logs <- ZTestLogger.logOutput
          log = logs.filter(_.message() == "Oh!").head
        } yield assertTrue(
          log.annotations.get("header").contains("value"),
          log.annotations.get(Header.UserAgent.name).contains("zio-http/3.0.0"),
        )
      },
    )
}
