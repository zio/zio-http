package test.api.v1.zoo.list

import test.component._
import zio.Chunk

object Species {
  import zio.http._
  import zio.http.endpoint._
  import zio.http.codec._
  val list_by_species = Endpoint(Method.GET / "api" / "v1" / "zoo" / "list" / string("species"))
    .query(HttpCodec.query[Int]("max-age"))
    .in[Unit]
    .out[Chunk[Animal]](status = Status.Ok)

}
