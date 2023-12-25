package test.api.v1

import test.component._

object Users {
  import zio.http._
  import zio.http.endpoint._
  import zio.http.codec._
  val post = Endpoint(Method.POST / "api" / "v1" / "users")
    .in[Unit]
    .out[User](status = Status.Ok)

}
