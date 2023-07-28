package zio.http

import zio.http.codec.QueryCodec
import zio.http.endpoint.Endpoint
import zio.{Scope, ZIO, ZIOAppArgs, ZIOAppDefault}

object Foo extends ZIOAppDefault {

  val app = Endpoint(
    Method.GET / "foo"
  ).query(
    QueryCodec.queryMany("baz").optional
  )
    .out[String]
    .implement(
    Handler.fromFunctionZIO { case (baz) =>
      println(s"baz: $baz")
      ZIO.succeed(s"baz: ${baz.map(_.map(_.toCharArray.map(_.toInt).toList))}")
    }
  )
    .toHttpApp

  override def run: ZIO[Any with ZIOAppArgs with Scope, Any, Any] =
    Server.serve(app).provide(Server.default)

}
