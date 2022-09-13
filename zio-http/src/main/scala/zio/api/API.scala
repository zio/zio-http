package zhttp.api

import zhttp.http.{Method, Headers => _, Path => _}
import zio.schema.Schema
import zio.stream.ZStream

/**
 *   - Input and Output as Schemas.
 *   - Support wide range of Codecs including Json
 *     - Dynamically decide response format based upon Request Header
 */

// final case class API[Input, Error, Output]
// final case class API[Route, Params, Input, Error, Output]
// - optimization
// - maintainability
// - getting something really good out there
final case class API[Params, Input, Output](
  method: Method,
  requestCodec: RequestCodec[Params], // Path / QueryParams / Headers
  doc: Doc,
  inputType: InputType[Input],        // Generate any sort of codec, generate OpenAPI docs.
  outputSchema: Schema[Output],
) { self =>
  type Id

  def query[A](queryParams: Query[A])(implicit
    zipper: Zipper[Params, A],
  ): API[zipper.Out, Input, Output] =
    copy(requestCodec = requestCodec ++ queryParams)

  def header[A](headers: Header[A])(implicit zipper: Zipper[Params, A]): API[zipper.Out, Input, Output] =
    copy(requestCodec = requestCodec ++ headers)

  def input[Input2](implicit schema: Schema[Input2]): API[Params, Input2, Output] =
    copy(inputType = InputType.ZIOInput(schema))

  def inputStream: API[Params, ZStream[Any, Throwable, Byte], Output] =
    copy(inputType = InputType.StreamInput)

  def output[Output2](implicit schema: Schema[Output2]): API[Params, Input, Output2] =
    copy(outputSchema = schema)

  def ++(that: API[_, _, _]): APIs =
    APIs(self) ++ that
}

object API {

  type WithId[Params, Input, Output, Id0] = API[Params, Input, Output] { type Id = Id0 }

  trait NotUnit[A]

  object NotUnit {
    implicit def notUnit[A]: NotUnit[A] = new NotUnit[A] {}

    implicit val notUnitUnit1: NotUnit[Unit] = new NotUnit[Unit] {}
    implicit val notUnitUnit2: NotUnit[Unit] = new NotUnit[Unit] {}
  }

  /**
   * Creates an API for DELETE request at the given route.
   */
  def delete[A](route: Route[A]): API[A, Unit, Unit] =
    method(Method.DELETE, route)

  /**
   * Creates an API for a GET request at the given route.
   */
  def get[A](route: Route[A]): API[A, Unit, Unit] =
    method(Method.GET, route)

  /**
   * Creates an API for a POST request at the given route.
   */
  def post[A](route: Route[A]): API[A, Unit, Unit] =
    method(Method.POST, route)

  /**
   * Creates an API for a PUT request at the given route.
   */
  def put[A](route: Route[A]): API[A, Unit, Unit] =
    method(Method.PUT, route)

  /**
   * Creates an API with the given method and route.
   */
  private def method[Params](method: Method, route: Route[Params]): API[Params, Unit, Unit] =
    API(
      method = method,
      requestCodec = route,
      doc = Doc.empty,
      inputType = InputType.ZIOInput(Schema[Unit]),
      outputSchema = Schema[Unit],
    )

}
