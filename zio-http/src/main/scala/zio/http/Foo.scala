package zio.http

import zio.http.codec.QueryCodec
import zio.http.endpoint.Endpoint
import zio.{Scope, ZIO, ZIOAppArgs, ZIOAppDefault}

object Foo extends ZIOAppDefault {

  val app = Endpoint(
    Method.GET / "foo" / int("bar")
  ).query(
    QueryCodec.paramStr("baz")
  )
    .out[String]
    .implement(
    Handler.fromFunctionZIO { case (bar, baz) =>
      ZIO.succeed(s"bar: $bar, baz: $baz")
    }
  )
    .toHttpApp

  override def run: ZIO[Any with ZIOAppArgs with Scope, Any, Any] =
    Server.serve(app).provide(Server.default)

}
