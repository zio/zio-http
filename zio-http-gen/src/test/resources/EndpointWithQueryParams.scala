package test.api.v1

import test.component._

object Users {
  import zio.http._
  import zio.http.endpoint._
  import zio.http.codec._
  val get = Endpoint(Method.GET / "api" / "v1" / "users")
    .query(HttpCodec.query[Int]("limit"))
    .query(HttpCodec.query[String]("name"))
    .in[Unit]

}
