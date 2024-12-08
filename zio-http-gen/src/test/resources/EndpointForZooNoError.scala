package test.api.v1.zoo

import test.component._
import zio.Chunk

object Animal {
  import zio.http._
  import zio.http.endpoint._
  import zio.http.codec._
  val get_animal = Endpoint(Method.GET / "api" / "v1" / "zoo" / string("animal"))
    .in[Unit]
    .out[Chunk[Animal]](status = Status.Ok)

}
