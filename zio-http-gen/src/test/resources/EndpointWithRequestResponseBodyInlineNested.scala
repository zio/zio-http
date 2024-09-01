package test.api.v1

import test.component._

object Users {
  import zio.http._
  import zio.http.endpoint._
  import zio.http.codec._
  val post = Endpoint(Method.POST / "api" / "v1" / "users")
    .in[POST.RequestBody]
    .out[POST.ResponseBody](status = Status.Ok)

  object POST {

    case class RequestBody(
      address: Option[Address],
      id: Int,
      name: String,
    )
    object RequestBody  {
      implicit val codec: Schema[RequestBody] = DeriveSchema.gen[RequestBody]
    }
    case class Address(
      number: Option[Int],
      street: Option[String],
    )
    object Address      {
      implicit val codec: Schema[Address] = DeriveSchema.gen[Address]
    }
    case class ResponseBody(
      id: Int,
      name: String,
    )
    object ResponseBody {
      implicit val codec: Schema[ResponseBody] = DeriveSchema.gen[ResponseBody]
    }
  }
}
