package zio.http

import zio._
import zio.test._

import zio.http.Header.UserAgent
import zio.http.Header.UserAgent.ProductOrComment

object LogAnnotationMiddlewareSpec extends ZIOSpecDefault {
  override def spec: Spec[TestEnvironment with Scope, Any] =
    suite("LogAnnotationMiddlewareSpec")(
      test("add static log annotation") {
        val response = Routes
          .singleton(
            handler(ZIO.logWarning("Oh!") *> ZIO.succeed(Response.text("Hey logging!"))),
          )
          .@@(Middleware.logAnnotate("label", "value"))
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
          .@@(Middleware.logAnnotateHeaders(UserAgent.name))
          .runZIO {
            Request
              .get("/")
              .addHeader("header", "value")
              .addHeader(UserAgent(ProductOrComment.Product("zio-http", Some("3.0.0"))))
          }

        for {
          _    <- response
          logs <- ZTestLogger.logOutput
          log = logs.filter(_.message() == "Oh!").head
        } yield assertTrue(
          log.annotations.get("header").contains("value"),
          log.annotations.get(UserAgent.name).contains("zio-http/3.0.0"),
        )
      },
    )
}
