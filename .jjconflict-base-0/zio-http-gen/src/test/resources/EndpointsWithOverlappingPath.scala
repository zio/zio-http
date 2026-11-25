package test

import test.component._

object Pets {
  import zio.http._
  import zio.http.endpoint._
  import zio.http.codec._
  val listPets = Endpoint(Method.GET / "pets")
    .query(HttpCodec.query[Int]("limit"))
    .in[Unit]
    .out[Pets](status = Status.Ok)

  val createPets = Endpoint(Method.POST / "pets")
    .in[Pet]
    .out[Unit](status = Status.Created)

}
