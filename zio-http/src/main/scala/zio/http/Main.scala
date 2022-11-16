package zio.http

import zio.http.model.Method
import zio.{Scope, ZIO, ZIOAppArgs, ZIOAppDefault}
import zio.http._
object Main extends ZIOAppDefault {

  override def run: ZIO[Any with ZIOAppArgs with Scope, Any, Any] = {
    val a = Http
      .collect[Request] { case Method.GET -> !! / "owls" / d ?  => req }
z
    Server.serve(a)

  }
}
