package test.api.v1.zoo.list

import test.component._
import zio.Chunk

object Species {
  import zio.http._
  import zio.http.endpoint._
  import zio.http.codec._
  import test.components.Species
  val list_by_species =
    Endpoint(Method.GET / "api" / "v1" / "zoo" / "list" / string("species").transform(Species.wrap)(Species.unwrap))
      .in[Unit]
      .out[Chunk[Animal]](status = Status.Ok)

}