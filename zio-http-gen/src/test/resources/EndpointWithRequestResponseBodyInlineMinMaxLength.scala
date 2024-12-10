package test.api.v1

import test.component._
import zio.schema._

object Entries {
  import zio.http._
  import zio.http.endpoint._
  import zio.http.codec._
  val post = Endpoint(Method.POST / "api" / "v1" / "entries")
    .in[POST.RequestBody]
    .out[POST.ResponseBody](status = Status.Ok)

  object POST {
    import zio.schema.annotation.validate
    import zio.schema.validation.Validation

    case class RequestBody(
      id: Int,
      @validate[String](Validation.maxLength(255) && Validation.minLength(1)) name: String,
    )
    object RequestBody  {
      implicit val codec: Schema[RequestBody] = DeriveSchema.gen[RequestBody]
    }
    case class ResponseBody(
      id: Int,
      @validate[String](Validation.maxLength(255) && Validation.minLength(1)) name: String,
    )
    object ResponseBody {
      implicit val codec: Schema[ResponseBody] = DeriveSchema.gen[ResponseBody]
    }
  }
}
