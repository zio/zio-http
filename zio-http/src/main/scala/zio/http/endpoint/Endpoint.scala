/*
 * Copyright 2021 - 2023 Sporta Technologies PVT LTD & the ZIO HTTP contributors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package zio.http.endpoint

import scala.reflect.ClassTag

import zio._

import zio.stream.ZStream

import zio.schema._

import zio.http.codec._
import zio.http.endpoint.Endpoint.OutErrors
import zio.http.{Handler, MediaType, Route, RoutePattern, Status}

/**
 * An [[zio.http.endpoint.Endpoint]] represents an API endpoint for the HTTP
 * protocol. Every `API` has an input, which comes from a combination of the
 * HTTP route, query string parameters, and headers, and an output, which is the
 * data computed by the handler of the API.
 *
 * MiddlewareInput : Example: A subset of `HttpCodec[Input]` that doesn't give
 * access to `Input` MiddlewareOutput: Example: A subset of `Out[Output]` that
 * doesn't give access to `Output` Input: Example: Int Output: Example: User
 *
 * As [[zio.http.endpoint.Endpoint]] is a purely declarative encoding of an
 * endpoint, it is possible to use this model to generate a [[zio.http.Route]] (by
 * supplying a handler for the endpoint), to generate OpenAPI documentation, to
 * generate a type-safe Scala client for the endpoint, and possibly, to generate
 * client libraries in other programming languages.
 */
final case class Endpoint[PathInput, Input, Err, Output, Middleware <: EndpointMiddleware](
  route: RoutePattern[PathInput],
  input: HttpCodec[HttpCodecType.RequestType, Input],
  output: HttpCodec[HttpCodecType.ResponseType, Output],
  error: HttpCodec[HttpCodecType.ResponseType, Err],
  doc: Doc,
  middleware: Middleware,
) { self =>
  import self.{middleware => mw}

  /**
   * Returns a new API that is derived from this one, but which includes
   * additional documentation that will be included in OpenAPI generation.
   */
  def ??(that: Doc): Endpoint[PathInput, Input, Err, Output, Middleware] = copy(doc = self.doc + that)

  /**
   * Flattens out this endpoint to a chunk of alternatives. Each alternative is
   * guaranteed to not have any alternatives itself.
   */
  def alternatives: Chunk[Endpoint[PathInput, Input, Err, Output, Middleware]] =
    self.input.alternatives.map { input =>
      self.copy(input = input)
    }

  def apply(input: Input): Invocation[PathInput, Input, Err, Output, Middleware] =
    Invocation(self, input)

  def apply[A, B](a: A, b: B)(implicit
    ev: (A, B) <:< Input,
  ): Invocation[PathInput, Input, Err, Output, Middleware] =
    Invocation(self, ev((a, b)))

  def apply[A, B, C](a: A, b: B, c: C)(implicit
    ev: (A, B, C) <:< Input,
  ): Invocation[PathInput, Input, Err, Output, Middleware] =
    Invocation(self, ev((a, b, c)))

  def apply[A, B, C, D](a: A, b: B, c: C, d: D)(implicit
    ev: (A, B, C, D) <:< Input,
  ): Invocation[PathInput, Input, Err, Output, Middleware] =
    Invocation(self, ev((a, b, c, d)))

  def apply[A, B, C, D, E](a: A, b: B, c: C, d: D, e: E)(implicit
    ev: (A, B, C, D, E) <:< Input,
  ): Invocation[PathInput, Input, Err, Output, Middleware] =
    Invocation(self, ev((a, b, c, d, e)))

  def apply[A, B, C, D, E, F](a: A, b: B, c: C, d: D, e: E, f: F)(implicit
    ev: (A, B, C, D, E, F) <:< Input,
  ): Invocation[PathInput, Input, Err, Output, Middleware] =
    Invocation(self, ev((a, b, c, d, e, f)))

  def apply[A, B, C, D, E, F, G](a: A, b: B, c: C, d: D, e: E, f: F, g: G)(implicit
    ev: (A, B, C, D, E, F, G) <:< Input,
  ): Invocation[PathInput, Input, Err, Output, Middleware] =
    Invocation(self, ev((a, b, c, d, e, f, g)))

  def apply[A, B, C, D, E, F, G, H](a: A, b: B, c: C, d: D, e: E, f: F, g: G, h: H)(implicit
    ev: (A, B, C, D, E, F, G, H) <:< Input,
  ): Invocation[PathInput, Input, Err, Output, Middleware] =
    Invocation(self, ev((a, b, c, d, e, f, g, h)))

  def apply[A, B, C, D, E, F, G, H, I](a: A, b: B, c: C, d: D, e: E, f: F, g: G, h: H, i: I)(implicit
    ev: (A, B, C, D, E, F, G, H, I) <:< Input,
  ): Invocation[PathInput, Input, Err, Output, Middleware] =
    Invocation(self, ev((a, b, c, d, e, f, g, h, i)))

  def apply[A, B, C, D, E, F, G, H, I, J](a: A, b: B, c: C, d: D, e: E, f: F, g: G, h: H, i: I, j: J)(implicit
    ev: (A, B, C, D, E, F, G, H, I, J) <:< Input,
  ): Invocation[PathInput, Input, Err, Output, Middleware] =
    Invocation(self, ev((a, b, c, d, e, f, g, h, i, j)))

  def apply[A, B, C, D, E, F, G, H, I, J, K](a: A, b: B, c: C, d: D, e: E, f: F, g: G, h: H, i: I, j: J, k: K)(implicit
    ev: (A, B, C, D, E, F, G, H, I, J, K) <:< Input,
  ): Invocation[PathInput, Input, Err, Output, Middleware] =
    Invocation(self, ev((a, b, c, d, e, f, g, h, i, j, k)))

  def apply[A, B, C, D, E, F, G, H, I, J, K, L](a: A, b: B, c: C, d: D, e: E, f: F, g: G, h: H, i: I, j: J, k: K, l: L)(
    implicit ev: (A, B, C, D, E, F, G, H, I, J, K, L) <:< Input,
  ): Invocation[PathInput, Input, Err, Output, Middleware] =
    Invocation(self, ev((a, b, c, d, e, f, g, h, i, j, k, l)))

  def examplesIn(examples: Input*): Endpoint[PathInput, Input, Err, Output, Middleware] =
    copy(input = self.input.examples(examples))

  def examplesIn: Chunk[Input] = self.input.examples

  def examplesOut(examples: Output*): Endpoint[PathInput, Input, Err, Output, Middleware] =
    copy(output = self.output.examples(examples))

  def examplesOut: Chunk[Output] = self.output.examples

  /**
   * Returns a new endpoint that requires the specified headers to be present.
   */
  def header[A](codec: HeaderCodec[A])(implicit
    combiner: Combiner[Input, A],
  ): Endpoint[PathInput, combiner.Out, Err, Output, Middleware] =
    copy(input = self.input ++ codec)

  def implement[Env](original: Handler[Env, Err, Input, Output])(implicit trace: Trace): Route[Env, Nothing] = {
    import HttpCodecError.isHttpCodecError

    val handlers = self.alternatives.map { endpoint =>
      Handler.fromFunctionZIO { (request: zio.http.Request) =>
        endpoint.input.decodeRequest(request).orDie.flatMap { value =>
          original(value).map(endpoint.output.encodeResponse(_)).catchAll { error =>
            ZIO.succeed(endpoint.error.encodeResponse(error))
          }
        }
      }
    }

    // TODO: What to do if there are no endpoints??
    val handlers2 =
      NonEmptyChunk
        .fromChunk(handlers)
        .getOrElse(NonEmptyChunk(Handler.fail(zio.http.Response(status = Status.NotFound))))

    val handler =
      Handler.firstSuccessOf(handlers2, isHttpCodecError(_)).catchAllCause {
        case cause if isHttpCodecError(cause) =>
          Handler.succeed(zio.http.Response(status = Status.BadRequest))

        case cause => Handler.failCause(cause)
      }

    Route.handled(self.route)(handler.contramap[(Any, zio.http.Request)](_._2))
  }

  /**
   * Returns a new endpoint derived from this one, whose request content must
   * satisfy the specified schema.
   */
  def in[Input2](implicit
    schema: Schema[Input2],
    combiner: Combiner[Input, Input2],
  ): Endpoint[PathInput, combiner.Out, Err, Output, Middleware] =
    copy(input = input ++ HttpCodec.content(schema))

  /**
   * Returns a new endpoint derived from this one, whose request content must
   * satisfy the specified schema and is documented.
   */
  def in[Input2](doc: Doc)(implicit
    schema: Schema[Input2],
    combiner: Combiner[Input, Input2],
  ): Endpoint[PathInput, combiner.Out, Err, Output, Middleware] =
    copy(input = input ++ HttpCodec.content(schema) ?? doc)

  /**
   * Returns a new endpoint derived from this one, whose request content must
   * satisfy the specified schema and is documented.
   */
  def in[Input2](name: String)(implicit
    schema: Schema[Input2],
    combiner: Combiner[Input, Input2],
  ): Endpoint[PathInput, combiner.Out, Err, Output, Middleware] =
    copy(input = input ++ HttpCodec.content(name)(schema))

  /**
   * Returns a new endpoint derived from this one, whose request content must
   * satisfy the specified schema and is documented.
   */
  def in[Input2](name: String, doc: Doc)(implicit
    schema: Schema[Input2],
    combiner: Combiner[Input, Input2],
  ): Endpoint[PathInput, combiner.Out, Err, Output, Middleware] =
    copy(input = input ++ (HttpCodec.content(name)(schema) ?? doc))

  /**
   * Returns a new endpoint derived from this one, whose request must satisfy
   * the specified codec.
   */
  def inCodec[Input2](codec: HttpCodec[HttpCodecType.RequestType, Input2])(implicit
    combiner: Combiner[Input, Input2],
  ): Endpoint[PathInput, combiner.Out, Err, Output, Middleware] =
    copy(input = input ++ codec)

  /**
   * Returns a new endpoint derived from this one, whose input type is a stream
   * of the specified typ.
   */
  def inStream[Input2: Schema](implicit
    combiner: Combiner[Input, ZStream[Any, Nothing, Input2]],
  ): Endpoint[PathInput, combiner.Out, Err, Output, Middleware] =
    Endpoint(
      route,
      input = self.input ++ ContentCodec.contentStream[Input2],
      output,
      error,
      doc,
      mw,
    )

  /**
   * Returns a new endpoint derived from this one, whose input type is a stream
   * of the specified type and is documented.
   */
  def inStream[Input2: Schema](doc: Doc)(implicit
    combiner: Combiner[Input, ZStream[Any, Nothing, Input2]],
  ): Endpoint[PathInput, combiner.Out, Err, Output, Middleware] =
    Endpoint(
      route,
      input = self.input ++ (ContentCodec.contentStream[Input2] ?? doc),
      output,
      error,
      doc,
      mw,
    )

  /**
   * Returns a new endpoint derived from this one, whose input type is a stream
   * of the specified type.
   */
  def inStream[Input2: Schema](name: String)(implicit
    combiner: Combiner[Input, ZStream[Any, Nothing, Input2]],
  ): Endpoint[PathInput, combiner.Out, Err, Output, Middleware] =
    Endpoint(
      route,
      input = self.input ++ ContentCodec.contentStream[Input2](name),
      output,
      error,
      doc,
      mw,
    )

  /**
   * Returns a new endpoint derived from this one, whose input type is a stream
   * of the specified type and is documented.
   */
  def inStream[Input2: Schema](name: String, doc: Doc)(implicit
    combiner: Combiner[Input, ZStream[Any, Nothing, Input2]],
  ): Endpoint[PathInput, combiner.Out, Err, Output, Middleware] =
    Endpoint(
      route,
      input = self.input ++ (ContentCodec.contentStream[Input2](name) ?? doc),
      output,
      error,
      doc,
      mw,
    )

  /**
   * Returns a new endpoint derived from this one whose middleware is composed
   * from the existing middleware of this endpoint, and the specified
   * middleware.
   */
  def @@[M2 <: EndpointMiddleware](that: M2)(implicit
    inCombiner: Combiner[middleware.In, that.In],
    outCombiner: Combiner[middleware.Out, that.Out],
    errAlternator: Alternator[mw.Err, that.Err],
  ): Endpoint[PathInput, Input, Err, Output, EndpointMiddleware.Typed[
    inCombiner.Out,
    errAlternator.Out,
    outCombiner.Out,
  ]] =
    Endpoint(route, input, output, error, doc, mw ++ that)

  /**
   * Returns a new endpoint derived from this one, whose output type is the
   * specified type for the ok status code.
   */
  def out[Output2: Schema](implicit
    alt: Alternator[Output, Output2],
  ): Endpoint[PathInput, Input, Err, alt.Out, Middleware] =
    Endpoint(
      route,
      input,
      output = (self.output | HttpCodec.content(implicitly[Schema[Output2]])) ++ StatusCodec.status(Status.Ok),
      error,
      doc,
      mw,
    )

  /**
   * Returns a new endpoint derived from this one, whose output type is the
   * specified type for the ok status code and is documented.
   */
  def out[Output2: Schema](doc: Doc)(implicit
    alt: Alternator[Output, Output2],
  ): Endpoint[PathInput, Input, Err, alt.Out, Middleware] =
    out[Output2](Status.Ok, doc)

  /**
   * Returns a new endpoint derived from this one, whose output type is the
   * specified type for the ok status code.
   */
  def out[Output2: Schema](
    mediaType: MediaType,
  )(implicit alt: Alternator[Output, Output2]): Endpoint[PathInput, Input, Err, alt.Out, Middleware] =
    out[Output2](Status.Ok, mediaType)

  /**
   * Returns a new endpoint derived from this one, whose output type is the
   * specified type for the specified status code.
   */
  def out[Output2: Schema](
    status: Status,
  )(implicit alt: Alternator[Output, Output2]): Endpoint[PathInput, Input, Err, alt.Out, Middleware] =
    Endpoint(
      route,
      input,
      output = self.output | (HttpCodec.content(implicitly[Schema[Output2]]) ++ StatusCodec.status(status)),
      error,
      doc,
      mw,
    )

  /**
   * Returns a new endpoint derived from this one, whose output type is the
   * specified type for the specified status code and is documented.
   */
  def out[Output2: Schema](
    status: Status,
    doc: Doc,
  )(implicit alt: Alternator[Output, Output2]): Endpoint[PathInput, Input, Err, alt.Out, Middleware] =
    Endpoint(
      route,
      input,
      output = self.output | ((HttpCodec.content(implicitly[Schema[Output2]]) ++ StatusCodec.status(status)) ?? doc),
      error,
      doc,
      mw,
    )

  /**
   * Returns a new endpoint derived from this one, whose output type is the
   * specified type for the ok status code and is documented.
   */
  def out[Output2: Schema](
    mediaType: MediaType,
    doc: Doc,
  )(implicit alt: Alternator[Output, Output2]): Endpoint[PathInput, Input, Err, alt.Out, Middleware] =
    Endpoint(
      route,
      input,
      output = self.output | (HttpCodec.content(mediaType)(implicitly[Schema[Output2]]) ?? doc),
      error,
      doc,
      mw,
    )

  /**
   * Returns a new endpoint derived from this one, whose output type is the
   * specified type for the specified status code and is documented.
   */
  def out[Output2: Schema](
    status: Status,
    mediaType: MediaType,
    doc: Doc,
  )(implicit alt: Alternator[Output, Output2]): Endpoint[PathInput, Input, Err, alt.Out, Middleware] =
    Endpoint(
      route,
      input,
      output = self.output |
        ((HttpCodec.content(mediaType)(implicitly[Schema[Output2]]) ++ StatusCodec.status(status)) ?? doc),
      error,
      doc,
      mw,
    )

  /**
   * Returns a new endpoint derived from this one, whose output type is the
   * specified type for the specified status code.
   */
  def out[Output2: Schema](
    status: Status,
    mediaType: MediaType,
  )(implicit alt: Alternator[Output, Output2]): Endpoint[PathInput, Input, Err, alt.Out, Middleware] =
    Endpoint(
      route,
      input,
      output = self.output | (HttpCodec.content(mediaType)(implicitly[Schema[Output2]]) ++ StatusCodec.status(status)),
      error,
      doc,
      mw,
    )

  /**
   * Returns a new endpoint that can fail with the specified error type for the
   * specified status code.
   */
  def outError[Err2](status: Status)(implicit
    schema: Schema[Err2],
    alt: Alternator[Err, Err2],
  ): Endpoint[PathInput, Input, alt.Out, Output, Middleware] =
    copy[PathInput, Input, alt.Out, Output, Middleware](
      error = self.error | (ContentCodec.content[Err2]("error-response") ++ StatusCodec.status(status)),
    )

  /**
   * Returns a new endpoint that can fail with the specified error type for the
   * specified status code and is documented.
   */
  def outError[Err2](status: Status, doc: Doc)(implicit
    schema: Schema[Err2],
    alt: Alternator[Err, Err2],
  ): Endpoint[PathInput, Input, alt.Out, Output, Middleware] =
    copy[PathInput, Input, alt.Out, Output, Middleware](
      error = self.error | ((ContentCodec.content[Err2]("error-response") ++ StatusCodec.status(status)) ?? doc),
    )

  def outErrors[Err2]: OutErrors[PathInput, Input, Err, Output, Middleware, Err2] = OutErrors(self)

  /**
   * Returns a new endpoint derived from this one, whose response must satisfy
   * the specified codec.
   */
  def outCodec[Output2](codec: HttpCodec[HttpCodecType.ResponseType, Output2])(implicit
    alt: Alternator[Output, Output2],
  ): Endpoint[PathInput, Input, Err, alt.Out, Middleware] =
    copy(output = self.output | codec)

  /**
   * Returns a new endpoint derived from this one, whose output type is a stream
   * of the specified type for the ok status code.
   */
  def outStream[Output2: Schema](implicit
    alt: Alternator[Output, ZStream[Any, Nothing, Output2]],
  ): Endpoint[PathInput, Input, Err, alt.Out, Middleware] =
    Endpoint(
      route,
      input,
      output = self.output | (ContentCodec.contentStream[Output2] ++ StatusCodec.status(Status.Ok)),
      error,
      doc,
      mw,
    )

  /**
   * Returns a new endpoint derived from this one, whose output type is a stream
   * of the specified type for the ok status code.
   */
  def outStream[Output2: Schema](doc: Doc)(implicit
    alt: Alternator[Output, ZStream[Any, Nothing, Output2]],
  ): Endpoint[PathInput, Input, Err, alt.Out, Middleware] =
    Endpoint(
      route,
      input,
      output = self.output | (ContentCodec.contentStream[Output2] ++ StatusCodec.status(Status.Ok) ?? doc),
      error,
      doc,
      mw,
    )

  /**
   * Returns a new endpoint derived from this one, whose output type is a stream
   * of the specified type for the specified status code and is documented.
   */
  def outStream[Output2: Schema](
    status: Status,
    doc: Doc,
  )(implicit
    alt: Alternator[Output, ZStream[Any, Nothing, Output2]],
  ): Endpoint[PathInput, Input, Err, alt.Out, Middleware] =
    Endpoint(
      route,
      input,
      output = self.output | (ContentCodec.contentStream[Output2] ++ StatusCodec.status(status) ?? doc),
      error,
      doc,
      mw,
    )

  def outStream[Output2: Schema](
    mediaType: MediaType,
  )(implicit
    alt: Alternator[Output, ZStream[Any, Nothing, Output2]],
  ): Endpoint[PathInput, Input, Err, alt.Out, Middleware] =
    outStream(Status.Ok, mediaType)

  def outStream[Output2: Schema](
    mediaType: MediaType,
    doc: Doc,
  )(implicit
    alt: Alternator[Output, ZStream[Any, Nothing, Output2]],
  ): Endpoint[PathInput, Input, Err, alt.Out, Middleware] =
    outStream(Status.Ok, mediaType, doc)

  def outStream[Output2: Schema](
    status: Status,
    mediaType: MediaType,
  )(implicit
    alt: Alternator[Output, ZStream[Any, Nothing, Output2]],
  ): Endpoint[PathInput, Input, Err, alt.Out, Middleware] =
    Endpoint(
      route,
      input,
      output = self.output | (ContentCodec.contentStream[Output2](mediaType) ++ StatusCodec.status(status)),
      error,
      doc,
      mw,
    )

  def outStream[Output2: Schema](
    status: Status,
    mediaType: MediaType,
    doc: Doc,
  )(implicit
    alt: Alternator[Output, ZStream[Any, Nothing, Output2]],
  ): Endpoint[PathInput, Input, Err, alt.Out, Middleware] =
    Endpoint(
      route,
      input,
      output = self.output | ((ContentCodec.contentStream[Output2](mediaType) ++ StatusCodec.status(status)) ?? doc),
      error,
      doc,
      mw,
    )

  /**
   * Returns a new endpoint that requires the specified query.
   */
  def query[A](codec: QueryCodec[A])(implicit
    combiner: Combiner[Input, A],
  ): Endpoint[PathInput, combiner.Out, Err, Output, Middleware] =
    copy(input = self.input ++ codec)
}

object Endpoint {

  /**
   * Constructs an endpoint for a route pattern.
   */
  def apply[Input](route: RoutePattern[Input]): Endpoint[Input, Input, ZNothing, ZNothing, EndpointMiddleware.None] =
    Endpoint(
      route,
      route.toHttpCodec,
      HttpCodec.unused,
      HttpCodec.unused,
      Doc.empty,
      EndpointMiddleware.None,
    )

  final case class OutErrors[PathInput, Input, Err, Output, Middleware <: EndpointMiddleware, Err2](
    self: Endpoint[PathInput, Input, Err, Output, Middleware],
  ) extends AnyVal {

    def apply[Sub1 <: Err2: ClassTag, Sub2 <: Err2: ClassTag](
      codec1: HttpCodec[HttpCodecType.Status & HttpCodecType.Content, Sub1],
      codec2: HttpCodec[HttpCodecType.Status & HttpCodecType.Content, Sub2],
    )(implicit alt: Alternator[Err, Err2]): Endpoint[PathInput, Input, alt.Out, Output, Middleware] = {
      val codec = HttpCodec.enumeration(codec1, codec2)
      self.copy[PathInput, Input, alt.Out, Output, Middleware](error = self.error | codec)
    }

    def apply[Sub1 <: Err2: ClassTag, Sub2 <: Err2: ClassTag, Sub3 <: Err2: ClassTag](
      codec1: HttpCodec[HttpCodecType.Status & HttpCodecType.Content, Sub1],
      codec2: HttpCodec[HttpCodecType.Status & HttpCodecType.Content, Sub2],
      codec3: HttpCodec[HttpCodecType.Status & HttpCodecType.Content, Sub3],
    )(implicit alt: Alternator[Err, Err2]): Endpoint[PathInput, Input, alt.Out, Output, Middleware] = {
      val codec = HttpCodec.enumeration(codec1, codec2, codec3)
      self.copy[PathInput, Input, alt.Out, Output, Middleware](error = self.error | codec)
    }

    def apply[Sub1 <: Err2: ClassTag, Sub2 <: Err2: ClassTag, Sub3 <: Err2: ClassTag, Sub4 <: Err2: ClassTag](
      codec1: HttpCodec[HttpCodecType.Status & HttpCodecType.Content, Sub1],
      codec2: HttpCodec[HttpCodecType.Status & HttpCodecType.Content, Sub2],
      codec3: HttpCodec[HttpCodecType.Status & HttpCodecType.Content, Sub3],
      codec4: HttpCodec[HttpCodecType.Status & HttpCodecType.Content, Sub4],
    )(implicit alt: Alternator[Err, Err2]): Endpoint[PathInput, Input, alt.Out, Output, Middleware] = {
      val codec = HttpCodec.enumeration(codec1, codec2, codec3, codec4)
      self.copy[PathInput, Input, alt.Out, Output, Middleware](error = self.error | codec)
    }

    def apply[
      Sub1 <: Err2: ClassTag,
      Sub2 <: Err2: ClassTag,
      Sub3 <: Err2: ClassTag,
      Sub4 <: Err2: ClassTag,
      Sub5 <: Err2: ClassTag,
    ](
      codec1: HttpCodec[HttpCodecType.Status & HttpCodecType.Content, Sub1],
      codec2: HttpCodec[HttpCodecType.Status & HttpCodecType.Content, Sub2],
      codec3: HttpCodec[HttpCodecType.Status & HttpCodecType.Content, Sub3],
      codec4: HttpCodec[HttpCodecType.Status & HttpCodecType.Content, Sub4],
      codec5: HttpCodec[HttpCodecType.Status & HttpCodecType.Content, Sub5],
    )(implicit alt: Alternator[Err, Err2]): Endpoint[PathInput, Input, alt.Out, Output, Middleware] = {
      val codec = HttpCodec.enumeration(codec1, codec2, codec3, codec4, codec5)
      self.copy[PathInput, Input, alt.Out, Output, Middleware](error = self.error | codec)
    }

    def apply[
      Sub1 <: Err2: ClassTag,
      Sub2 <: Err2: ClassTag,
      Sub3 <: Err2: ClassTag,
      Sub4 <: Err2: ClassTag,
      Sub5 <: Err2: ClassTag,
      Sub6 <: Err2: ClassTag,
    ](
      codec1: HttpCodec[HttpCodecType.Status & HttpCodecType.Content, Sub1],
      codec2: HttpCodec[HttpCodecType.Status & HttpCodecType.Content, Sub2],
      codec3: HttpCodec[HttpCodecType.Status & HttpCodecType.Content, Sub3],
      codec4: HttpCodec[HttpCodecType.Status & HttpCodecType.Content, Sub4],
      codec5: HttpCodec[HttpCodecType.Status & HttpCodecType.Content, Sub5],
      codec6: HttpCodec[HttpCodecType.Status & HttpCodecType.Content, Sub6],
    )(implicit alt: Alternator[Err, Err2]): Endpoint[PathInput, Input, alt.Out, Output, Middleware] = {
      val codec = HttpCodec.enumeration(codec1, codec2, codec3, codec4, codec5, codec6)
      self.copy[PathInput, Input, alt.Out, Output, Middleware](error = self.error | codec)
    }

    def apply[
      Sub1 <: Err2: ClassTag,
      Sub2 <: Err2: ClassTag,
      Sub3 <: Err2: ClassTag,
      Sub4 <: Err2: ClassTag,
      Sub5 <: Err2: ClassTag,
      Sub6 <: Err2: ClassTag,
      Sub7 <: Err2: ClassTag,
    ](
      codec1: HttpCodec[HttpCodecType.Status & HttpCodecType.Content, Sub1],
      codec2: HttpCodec[HttpCodecType.Status & HttpCodecType.Content, Sub2],
      codec3: HttpCodec[HttpCodecType.Status & HttpCodecType.Content, Sub3],
      codec4: HttpCodec[HttpCodecType.Status & HttpCodecType.Content, Sub4],
      codec5: HttpCodec[HttpCodecType.Status & HttpCodecType.Content, Sub5],
      codec6: HttpCodec[HttpCodecType.Status & HttpCodecType.Content, Sub6],
      codec7: HttpCodec[HttpCodecType.Status & HttpCodecType.Content, Sub7],
    )(implicit alt: Alternator[Err, Err2]): Endpoint[PathInput, Input, alt.Out, Output, Middleware] = {
      val codec = HttpCodec.enumeration(codec1, codec2, codec3, codec4, codec5, codec6, codec7)
      self.copy[PathInput, Input, alt.Out, Output, Middleware](error = self.error | codec)
    }

    def apply[
      Sub1 <: Err2: ClassTag,
      Sub2 <: Err2: ClassTag,
      Sub3 <: Err2: ClassTag,
      Sub4 <: Err2: ClassTag,
      Sub5 <: Err2: ClassTag,
      Sub6 <: Err2: ClassTag,
      Sub7 <: Err2: ClassTag,
      Sub8 <: Err2: ClassTag,
    ](
      codec1: HttpCodec[HttpCodecType.Status & HttpCodecType.Content, Sub1],
      codec2: HttpCodec[HttpCodecType.Status & HttpCodecType.Content, Sub2],
      codec3: HttpCodec[HttpCodecType.Status & HttpCodecType.Content, Sub3],
      codec4: HttpCodec[HttpCodecType.Status & HttpCodecType.Content, Sub4],
      codec5: HttpCodec[HttpCodecType.Status & HttpCodecType.Content, Sub5],
      codec6: HttpCodec[HttpCodecType.Status & HttpCodecType.Content, Sub6],
      codec7: HttpCodec[HttpCodecType.Status & HttpCodecType.Content, Sub7],
      codec8: HttpCodec[HttpCodecType.Status & HttpCodecType.Content, Sub8],
    )(implicit alt: Alternator[Err, Err2]): Endpoint[PathInput, Input, alt.Out, Output, Middleware] = {
      val codec = HttpCodec.enumeration(codec1, codec2, codec3, codec4, codec5, codec6, codec7, codec8)
      self.copy[PathInput, Input, alt.Out, Output, Middleware](error = self.error | codec)
    }
  }
}
