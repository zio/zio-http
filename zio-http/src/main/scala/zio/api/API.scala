package zhttp.api

import zhttp.http.{Method, Headers => _, Path => _}
import zio.schema.Schema

/**
 *   - Input and Output as Schemas.
 *   - Support wide range of Codecs including Json
 *     - Dynamically decide response format based upon Request Header
 */
final case class API[Params, Input, Output](
  method: Method,
  requestParser: RequestParser[Params], // Path / QueryParams / Headers
  doc: Doc,
  inputSchema: Schema[Input],           // Generate any sort of codec, generate OpenAPI docs.
  outputSchema: Schema[Output],
) { self =>
  type Id

  def query[A](queryParams: Query[A])(implicit
    zippable: Zipper[Params, A],
  ): API[zippable.Out, Input, Output] =
    copy(requestParser = requestParser ++ queryParams)

  def header[A](headers: Header[A])(implicit zippable: Zipper[Params, A]): API[zippable.Out, Input, Output] =
    copy(requestParser = requestParser ++ headers)

  def input[Input2](implicit schema: Schema[Input2]): API[Params, Input2, Output] =
    copy(inputSchema = schema)

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
   * Creates an API for DELETE request at the given path.
   */
  def delete[A](path: Route[A]): API[A, Unit, Unit] =
    method(Method.DELETE, path)

  /**
   * Creates an API for a GET request at the given path.
   */
  def get[A](path: Route[A]): API[A, Unit, Unit] =
    method(Method.GET, path)

  /**
   * Creates an API for a POST request at the given path.
   */
  def post[A](path: Route[A]): API[A, Unit, Unit] =
    method(Method.POST, path)

  /**
   * Creates an API for a PUT request at the given path.
   */
  def put[A](path: Route[A]): API[A, Unit, Unit] =
    method(Method.PUT, path)

  /**
   * Creates an API with the given method and path.
   */
  private def method[Params](method: Method, path: Route[Params]): API[Params, Unit, Unit] =
    API(
      method = method,
      requestParser = path,
      doc = Doc.empty,
      inputSchema = Schema[Unit],
      outputSchema = Schema[Unit],
    )

}
