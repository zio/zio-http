package zio.http

import zio.{Scope, ZIO}
import zio.test._

object JSClientSpec extends ZIOSpecDefault {
  override def spec: Spec[TestEnvironment with Scope, Any] =
    suite("ClientSpec")(
      test("test") {
        for {
          response <- ZIO.serviceWithZIO[Client]{_.get("https://www.google.com") }
          string <- response.body.asString
        } yield assertTrue(response.status.isSuccess, string.startsWith("<!doctype html>"))
      }
    ).provideSome[Scope](ZClient.default)
}
