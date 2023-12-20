package test.api.v1.users

import test.component._

object UserId {
  import zio.http._
  import zio.http.endpoint._
  import zio.http.codec._
  val get = Endpoint(Method.GET / "api" / "v1" / "users" / int("userId"))
    .in[Unit]

}
