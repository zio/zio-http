package test.api.v1.zoo.info

import test.component._

object Id {
  import zio.http._
  import zio.http.endpoint._
  import zio.http.codec._
  val get_animal_info = Endpoint(Method.GET / "api" / "v1" / "zoo" / "info" / int("id"))
    .in[Unit]
    .out[Animal](status = Status.Ok)

}
