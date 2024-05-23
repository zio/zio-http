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

import scala.annotation.nowarn
import scala.reflect.ClassTag

import zio._

import zio.stream.ZStream

import zio.schema._

import zio.http.Header.Accept.MediaTypeWithQFactor
import zio.http._
import zio.http.codec._
import zio.http.endpoint.Endpoint.{OutErrors, defaultMediaTypes}

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
 * endpoint, it is possible to use this model to generate a [[zio.http.Route]]
 * (by supplying a handler for the endpoint), to generate OpenAPI documentation,
 * to generate a type-safe Scala client for the endpoint, and possibly, to
 * generate client libraries in other programming languages.
 */
@nowarn("msg=type parameter .* defined")
final case class Endpoint[PathInput, Input, Err, Output, Middleware <: EndpointMiddleware](
  route: RoutePattern[PathInput],
  input: HttpCodec[HttpCodecType.RequestType, Input],
  output: HttpCodec[HttpCodecType.ResponseType, Output],
  error: HttpCodec[HttpCodecType.ResponseType, Err],
  codecError: HttpCodec[HttpCodecType.ResponseType, HttpCodecError],
  doc: Doc,
  middleware: Middleware,
  tags: List[String],
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
  def alternatives: Chunk[(Endpoint[PathInput, Input, Err, Output, Middleware], HttpCodec.Fallback.Condition)] =
    self.input.alternatives.map { case (input, condition) =>
      self.copy(input = input) -> condition
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

  /**
   * Hides any details of codec errors from the user.
   */
  def emptyErrorResponse: Endpoint[PathInput, Input, Err, Output, Middleware] =
    self.copy(codecError =
      StatusCodec.BadRequest
        .transformOrFail[HttpCodecError](_ => Right(HttpCodecError.CustomError("Empty", "empty")))(_ => Right(())),
    )

  def examplesIn(examples: (String, Input)*): Endpoint[PathInput, Input, Err, Output, Middleware] =
    copy(input = self.input.examples(examples))

  def examplesIn: Map[String, Input] = self.input.examples

  def examplesOut(examples: (String, Output)*): Endpoint[PathInput, Input, Err, Output, Middleware] =
    copy(output = self.output.examples(examples))

  def examplesOut: Map[String, Output] = self.output.examples

  /**
   * Returns a new endpoint that requires the specified headers to be present.
   */
  def header[A](codec: HeaderCodec[A])(implicit
    combiner: Combiner[Input, A],
  ): Endpoint[PathInput, combiner.Out, Err, Output, Middleware] =
    copy(input = self.input ++ codec)

  def implement[Env](original: Handler[Env, Err, Input, Output])(implicit trace: Trace): Route[Env, Nothing] = {
    import HttpCodecError.asHttpCodecError

    val handlers = self.alternatives.map { case (endpoint, condition) =>
      Handler.fromFunctionZIO { (request: zio.http.Request) =>
        val outputMediaTypes =
          NonEmptyChunk
            .fromChunk(
              request.headers
                .getAll(Header.Accept)
                .flatMap(_.mimeTypes),
            )
            .getOrElse(defaultMediaTypes)
        endpoint.input.decodeRequest(request).orDie.flatMap { value =>
          original(value).map(endpoint.output.encodeResponse(_, outputMediaTypes)).catchAll { error =>
            ZIO.succeed(endpoint.error.encodeResponse(error, outputMediaTypes))
          }
        }
      } -> condition
    }

    // TODO: What to do if there are no endpoints??
    val handlers2 =
      NonEmptyChunk
        .fromChunk(handlers)
        .getOrElse(
          NonEmptyChunk(
            Handler.fail(zio.http.Response(status = Status.NotFound)) -> HttpCodec.Fallback.Condition.IsHttpCodecError,
          ),
        )

    val handler =
      handlers.tail
        .foldLeft(handlers2.head._1) { case (acc, (handler, condition)) =>
          acc.catchAllCause { cause =>
            if (condition(cause)) {
              handler
            } else {
              Handler.failCause(cause)
            }
          }
        }
        .catchAllCause { cause =>
          asHttpCodecError(cause) match {
            case Some(_) =>
              Handler.fromFunctionZIO { (request: zio.http.Request) =>
                val error    = cause.defects.head.asInstanceOf[HttpCodecError]
                val log      = ZIO.unit
                val response = {
                  val outputMediaTypes =
                    NonEmptyChunk
                      .fromChunk(
                        request.headers
                          .getAll(Header.Accept)
                          .flatMap(_.mimeTypes) :+ MediaTypeWithQFactor(MediaType.application.`json`, Some(0.0)),
                      )
                      .getOrElse(defaultMediaTypes)
                  codecError.encodeResponse(error, outputMediaTypes)
                }
                log.as(response)
              }
            case None    =>
              Handler.failCause(cause)
          }
        }

    Route.handled(self.route)(handler)
  }

  /**
   * Returns a new endpoint derived from this one, whose request content must
   * satisfy the specified schema.
   */
  def in[Input2: HttpContentCodec](implicit
    combiner: Combiner[Input, Input2],
  ): Endpoint[PathInput, combiner.Out, Err, Output, Middleware] =
    copy(input = input ++ HttpCodec.content[Input2])

  /**
   * Returns a new endpoint derived from this one, whose request content must
   * satisfy the specified schema and is documented.
   */
  def in[Input2: HttpContentCodec](doc: Doc)(implicit
    combiner: Combiner[Input, Input2],
  ): Endpoint[PathInput, combiner.Out, Err, Output, Middleware] =
    copy(input = input ++ HttpCodec.content[Input2] ?? doc)

  /**
   * Returns a new endpoint derived from this one, whose request content must
   * satisfy the specified schema and is documented.
   */
  def in[Input2: HttpContentCodec](name: String)(implicit
    combiner: Combiner[Input, Input2],
  ): Endpoint[PathInput, combiner.Out, Err, Output, Middleware] =
    copy(input = input ++ HttpCodec.content[Input2](name))

  /**
   * Returns a new endpoint derived from this one, whose request content must
   * satisfy the specified schema and is documented.
   */
  def in[Input2: HttpContentCodec](name: String, doc: Doc)(implicit
    combiner: Combiner[Input, Input2],
  ): Endpoint[PathInput, combiner.Out, Err, Output, Middleware] =
    copy(input = input ++ (HttpCodec.content[Input2](name) ?? doc))

  def in[Input2: HttpContentCodec](mediaType: MediaType)(implicit
    combiner: Combiner[Input, Input2],
  ): Endpoint[PathInput, combiner.Out, Err, Output, Middleware] =
    copy(input = input ++ HttpCodec.content[Input2](mediaType))

  def in[Input2: HttpContentCodec](mediaType: MediaType, doc: Doc)(implicit
    combiner: Combiner[Input, Input2],
  ): Endpoint[PathInput, combiner.Out, Err, Output, Middleware] =
    copy(input = input ++ (HttpCodec.content(mediaType) ?? doc))

  def in[Input2: HttpContentCodec](mediaType: MediaType, name: String)(implicit
    combiner: Combiner[Input, Input2],
  ): Endpoint[PathInput, combiner.Out, Err, Output, Middleware] =
    copy(input = input ++ HttpCodec.content(name, mediaType))

  def in[Input2: HttpContentCodec](mediaType: MediaType, name: String, doc: Doc)(implicit
    combiner: Combiner[Input, Input2],
  ): Endpoint[PathInput, combiner.Out, Err, Output, Middleware] =
    copy(input = input ++ (HttpCodec.content(name, mediaType) ?? doc))

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
  def inStream[Input2: HttpContentCodec](implicit
    combiner: Combiner[Input, ZStream[Any, Nothing, Input2]],
  ): Endpoint[PathInput, combiner.Out, Err, Output, Middleware] =
    Endpoint(
      route,
      input = self.input ++ ContentCodec.contentStream[Input2],
      output,
      error,
      codecError,
      doc,
      mw,
      tags,
    )

  /**
   * Returns a new endpoint derived from this one, whose input type is a stream
   * of the specified type and is documented.
   */
  def inStream[Input2: HttpContentCodec](doc: Doc)(implicit
    combiner: Combiner[Input, ZStream[Any, Nothing, Input2]],
  ): Endpoint[PathInput, combiner.Out, Err, Output, Middleware] =
    Endpoint(
      route,
      input = self.input ++ (ContentCodec.contentStream[Input2] ?? doc),
      output,
      error,
      codecError,
      doc,
      mw,
      tags,
    )

  /**
   * Returns a new endpoint derived from this one, whose input type is a stream
   * of the specified type.
   */
  def inStream[Input2: HttpContentCodec](name: String)(implicit
    combiner: Combiner[Input, ZStream[Any, Nothing, Input2]],
  ): Endpoint[PathInput, combiner.Out, Err, Output, Middleware] =
    Endpoint(
      route,
      input = self.input ++ ContentCodec.contentStream[Input2](name),
      output,
      error,
      codecError,
      doc,
      mw,
      tags,
    )

  /**
   * Returns a new endpoint derived from this one, whose input type is a stream
   * of the specified type and is documented.
   */
  def inStream[Input2: HttpContentCodec](name: String, doc: Doc)(implicit
    combiner: Combiner[Input, ZStream[Any, Nothing, Input2]],
  ): Endpoint[PathInput, combiner.Out, Err, Output, Middleware] =
    Endpoint(
      route,
      input = self.input ++ (ContentCodec.contentStream[Input2](name) ?? doc),
      output,
      error,
      codecError,
      doc,
      mw,
      tags,
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
    Endpoint(
      route,
      input,
      output,
      error,
      codecError,
      doc,
      mw ++ that,
      tags,
    )

  /**
   * Returns a new endpoint derived from this one, whose output type is the
   * specified type for the ok status code.
   */
  def out[Output2: HttpContentCodec](implicit
    alt: Alternator[Output2, Output],
  ): Endpoint[PathInput, Input, Err, alt.Out, Middleware] =
    Endpoint(
      route,
      input,
      output = (HttpCodec.content[Output2] ++ StatusCodec.status(Status.Ok)) | self.output,
      error,
      codecError,
      doc,
      mw,
      tags,
    )

  /**
   * Returns a new endpoint derived from this one, whose output type is the
   * specified type for the ok status code and is documented.
   */
  def out[Output2: HttpContentCodec](doc: Doc)(implicit
    alt: Alternator[Output2, Output],
  ): Endpoint[PathInput, Input, Err, alt.Out, Middleware] =
    out[Output2](Status.Ok, doc)

  /**
   * Returns a new endpoint derived from this one, whose output type is the
   * specified type for the ok status code.
   */
  def out[Output2: HttpContentCodec](
    mediaType: MediaType,
  )(implicit alt: Alternator[Output2, Output]): Endpoint[PathInput, Input, Err, alt.Out, Middleware] =
    out[Output2](Status.Ok, mediaType)

  /**
   * Returns a new endpoint derived from this one, whose output type is the
   * specified type for the specified status code.
   */
  def out[Output2: HttpContentCodec](
    status: Status,
  )(implicit alt: Alternator[Output2, Output]): Endpoint[PathInput, Input, Err, alt.Out, Middleware] =
    Endpoint(
      route,
      input,
      output = (HttpCodec.content[Output2] ++ StatusCodec.status(status)) | self.output,
      error,
      codecError,
      doc,
      mw,
      tags,
    )

  /**
   * Returns a new endpoint derived from this one, whose output type is the
   * specified type for the specified status code and is documented.
   */
  def out[Output2: HttpContentCodec](
    status: Status,
    doc: Doc,
  )(implicit alt: Alternator[Output2, Output]): Endpoint[PathInput, Input, Err, alt.Out, Middleware] =
    Endpoint(
      route,
      input,
      output = ((HttpCodec.content[Output2] ++ StatusCodec.status(status)) ?? doc) | self.output,
      error,
      codecError,
      Doc.empty,
      mw,
      tags,
    )

  /**
   * Returns a new endpoint derived from this one, whose output type is the
   * specified type for the ok status code and is documented.
   */
  def out[Output2: HttpContentCodec](
    mediaType: MediaType,
    doc: Doc,
  )(implicit alt: Alternator[Output2, Output]): Endpoint[PathInput, Input, Err, alt.Out, Middleware] =
    Endpoint(
      route,
      input,
      output = (HttpCodec.content[Output2](mediaType) ++ StatusCodec.Ok ?? doc) | self.output,
      error,
      codecError,
      doc,
      mw,
      tags,
    )

  /**
   * Returns a new endpoint derived from this one, whose output type is the
   * specified type for the specified status code and is documented.
   */
  def out[Output2: HttpContentCodec](
    status: Status,
    mediaType: MediaType,
    doc: Doc,
  )(implicit alt: Alternator[Output2, Output]): Endpoint[PathInput, Input, Err, alt.Out, Middleware] =
    Endpoint(
      route,
      input,
      output = ((HttpCodec.content[Output2](mediaType) ++ StatusCodec.status(status)) ?? doc) | self.output,
      error,
      codecError,
      doc,
      mw,
      tags,
    )

  /**
   * Returns a new endpoint derived from this one, whose output type is the
   * specified type for the specified status code.
   */
  def out[Output2: HttpContentCodec](
    status: Status,
    mediaType: MediaType,
  )(implicit alt: Alternator[Output2, Output]): Endpoint[PathInput, Input, Err, alt.Out, Middleware] =
    Endpoint(
      route,
      input,
      output = (HttpCodec.content[Output2](mediaType) ++ StatusCodec.status(status)) | self.output,
      error,
      codecError,
      doc,
      mw,
      tags,
    )

  /**
   * Converts a codec error into a specific error type. The given media types
   * are sorted by q-factor. Beginning with the highest q-factor.
   */
  def outCodecError(
    codec: HttpCodec[HttpCodecType.ResponseType, HttpCodecError],
  ): Endpoint[PathInput, Input, Err, Output, Middleware] =
    self.copy(codecError = codec | self.codecError)

  /**
   * Returns a new endpoint that can fail with the specified error type for the
   * specified status code.
   */
  def outError[Err2: HttpContentCodec](status: Status)(implicit
    alt: Alternator[Err2, Err],
  ): Endpoint[PathInput, Input, alt.Out, Output, Middleware] =
    copy[PathInput, Input, alt.Out, Output, Middleware](
      error = (ContentCodec.content[Err2]("error-response") ++ StatusCodec.status(status)) | self.error,
    )

  /**
   * Returns a new endpoint that can fail with the specified error type for the
   * specified status code and is documented.
   */
  def outError[Err2: HttpContentCodec](status: Status, doc: Doc)(implicit
    alt: Alternator[Err2, Err],
  ): Endpoint[PathInput, Input, alt.Out, Output, Middleware] =
    copy[PathInput, Input, alt.Out, Output, Middleware](
      error = ((ContentCodec.content[Err2]("error-response") ++ StatusCodec.status(status)) ?? doc) | self.error,
    )

  def outErrors[Err2]: OutErrors[PathInput, Input, Err, Output, Middleware, Err2] = OutErrors(self)

  /**
   * Returns a new endpoint derived from this one, whose response must satisfy
   * the specified codec.
   */
  def outCodec[Output2](codec: HttpCodec[HttpCodecType.ResponseType, Output2])(implicit
    alt: Alternator[Output2, Output],
  ): Endpoint[PathInput, Input, Err, alt.Out, Middleware] =
    copy(output = codec | self.output)

  /**
   * Returns a new endpoint derived from this one, whose output type is a stream
   * of the specified type for the ok status code.
   */
  def outStream[Output2: HttpContentCodec](implicit
    alt: Alternator[ZStream[Any, Nothing, Output2], Output],
  ): Endpoint[PathInput, Input, Err, alt.Out, Middleware] = {
    val contentCodec =
      if (implicitly[HttpContentCodec[Output2]].choices.forall(_._2.schema == Schema[Byte]))
        ContentCodec
          .binaryStream(MediaType.application.`octet-stream`)
          .asInstanceOf[ContentCodec[ZStream[Any, Nothing, Output2]]]
      else ContentCodec.contentStream[Output2]
    Endpoint(
      route,
      input,
      output = (contentCodec ++ StatusCodec.status(Status.Ok)) | self.output,
      error,
      codecError,
      doc,
      mw,
      tags,
    )
  }

  /**
   * Returns a new endpoint derived from this one, whose output type is a stream
   * of the specified type for the ok status code.
   */
  def outStream[Output2: HttpContentCodec](doc: Doc)(implicit
    alt: Alternator[ZStream[Any, Nothing, Output2], Output],
  ): Endpoint[PathInput, Input, Err, alt.Out, Middleware] = {
    val contentCodec =
      if (implicitly[HttpContentCodec[Output2]].choices.forall(_._2.schema == Schema[Byte]))
        ContentCodec
          .binaryStream(MediaType.application.`octet-stream`)
          .asInstanceOf[ContentCodec[ZStream[Any, Nothing, Output2]]]
      else ContentCodec.contentStream[Output2]
    Endpoint(
      route,
      input,
      output = (contentCodec ++ StatusCodec.status(Status.Ok) ?? doc) | self.output,
      error,
      codecError,
      doc,
      mw,
      tags,
    )
  }

  /**
   * Returns a new endpoint derived from this one, whose output type is a stream
   * of the specified type for the specified status code and is documented.
   */
  def outStream[Output2: HttpContentCodec](status: Status, doc: Doc)(implicit
    alt: Alternator[ZStream[Any, Nothing, Output2], Output],
  ): Endpoint[PathInput, Input, Err, alt.Out, Middleware] = {
    val contentCodec =
      if (implicitly[HttpContentCodec[Output2]].choices.forall(_._2.schema == Schema[Byte]))
        ContentCodec
          .binaryStream(MediaType.application.`octet-stream`)
          .asInstanceOf[ContentCodec[ZStream[Any, Nothing, Output2]]]
      else ContentCodec.contentStream[Output2]
    Endpoint(
      route,
      input,
      output = (contentCodec ++ StatusCodec.status(status) ?? doc) | self.output,
      error,
      codecError,
      doc,
      mw,
      tags,
    )
  }

  def outStream[Output2: HttpContentCodec](
    mediaType: MediaType,
  )(implicit
    alt: Alternator[ZStream[Any, Nothing, Output2], Output],
  ): Endpoint[PathInput, Input, Err, alt.Out, Middleware] =
    outStream(Status.Ok, mediaType)

  def outStream[Output2: HttpContentCodec](
    mediaType: MediaType,
    doc: Doc,
  )(implicit
    alt: Alternator[ZStream[Any, Nothing, Output2], Output],
  ): Endpoint[PathInput, Input, Err, alt.Out, Middleware] =
    outStream(Status.Ok, mediaType, doc)

  def outStream[Output2: HttpContentCodec](
    status: Status,
    mediaType: MediaType,
  )(implicit
    alt: Alternator[ZStream[Any, Nothing, Output2], Output],
  ): Endpoint[PathInput, Input, Err, alt.Out, Middleware] = {
    val contentCodec =
      if (mediaType.binary)
        ContentCodec.binaryStream(mediaType).asInstanceOf[ContentCodec[ZStream[Any, Nothing, Output2]]]
      else ContentCodec.contentStream[Output2](mediaType)
    Endpoint(
      route,
      input,
      output = (contentCodec ++ StatusCodec.status(status)) | self.output,
      error,
      codecError,
      doc,
      mw,
      tags,
    )
  }

  def outStream[Output2: HttpContentCodec](status: Status, mediaType: MediaType, doc: Doc)(implicit
    alt: Alternator[ZStream[Any, Nothing, Output2], Output],
  ): Endpoint[PathInput, Input, Err, alt.Out, Middleware] = {
    val contentCodec =
      if (implicitly[HttpContentCodec[Output2]].choices.forall(_._2.schema == Schema[Byte]))
        ContentCodec.binaryStream(mediaType).asInstanceOf[ContentCodec[ZStream[Any, Nothing, Output2]]]
      else ContentCodec.contentStream[Output2](mediaType)
    Endpoint(
      route,
      input,
      output = ((contentCodec ++ StatusCodec.status(status)) ?? doc) | self.output,
      error,
      codecError,
      doc,
      mw,
      tags,
    )
  }

  /**
   * Returns a new endpoint that requires the specified query.
   */
  def query[A](codec: QueryCodec[A])(implicit
    combiner: Combiner[Input, A],
  ): Endpoint[PathInput, combiner.Out, Err, Output, Middleware] =
    copy(input = self.input ++ codec)

  /**
   * Transforms the input of this endpoint using the specified functions. This
   * is useful to build from different http inputs a domain specific input.
   *
   * For example
   * {{{
   *   case class ChangeUserName(userId: UUID, name: String)
   *   val endpoint =
   *   Endpoint(Method.POST / "user" / uuid("userId") / "changeName").in[String]
   *     .transformIn { case (userId, name) => ChangeUserName(userId, name) } {
   *       case ChangeUserName(userId, name) => (userId, name)
   *     }
   * }}}
   */
  def transformIn[Input1](f: Input => Input1)(
    g: Input1 => Input,
  ): Endpoint[PathInput, Input1, Err, Output, Middleware] =
    copy(input = self.input.transform(f)(g))

  /**
   * Transforms the output of this endpoint using the specified functions.
   */
  def transformOut[Output1](f: Output => Output1)(
    g: Output1 => Output,
  ): Endpoint[PathInput, Input, Err, Output1, Middleware] =
    copy(output = self.output.transform(f)(g))

  /**
   * Transforms the error of this endpoint using the specified functions.
   */
  def transformError[Err1](f: Err => Err1)(
    g: Err1 => Err,
  ): Endpoint[PathInput, Input, Err1, Output, Middleware] =
    copy(error = self.error.transform(f)(g))

  /**
   * Returns a new API that is derived from this one, which includes a tag that
   * will be included in OpenAPI generation.
   */
  def tag(that: String): Endpoint[PathInput, Input, Err, Output, Middleware] = copy(tags = self.tags :+ that)

  /**
   * Returns a new API that is derived from this one, which includes a the list
   * of tags that will be included in OpenAPI generation.
   */
  def tags(that: List[String]): Endpoint[PathInput, Input, Err, Output, Middleware] = copy(tags = self.tags ++ that)
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
      HttpContentCodec.responseErrorCodec,
      Doc.empty,
      EndpointMiddleware.None,
      List.empty,
    )

  @nowarn("msg=type parameter .* defined")
  final case class OutErrors[PathInput, Input, Err, Output, Middleware <: EndpointMiddleware, Err2](
    self: Endpoint[PathInput, Input, Err, Output, Middleware],
  ) extends AnyVal {

    def apply[Sub1 <: Err2: ClassTag, Sub2 <: Err2: ClassTag](
      codec1: HttpCodec[HttpCodecType.Status & HttpCodecType.Content, Sub1],
      codec2: HttpCodec[HttpCodecType.Status & HttpCodecType.Content, Sub2],
    )(implicit alt: Alternator[Err2, Err]): Endpoint[PathInput, Input, alt.Out, Output, Middleware] = {
      val codec = HttpCodec.enumeration.f2(codec1, codec2)
      self.copy[PathInput, Input, alt.Out, Output, Middleware](error = codec | self.error)
    }

    def apply[Sub1 <: Err2: ClassTag, Sub2 <: Err2: ClassTag, Sub3 <: Err2: ClassTag](
      codec1: HttpCodec[HttpCodecType.Status & HttpCodecType.Content, Sub1],
      codec2: HttpCodec[HttpCodecType.Status & HttpCodecType.Content, Sub2],
      codec3: HttpCodec[HttpCodecType.Status & HttpCodecType.Content, Sub3],
    )(implicit alt: Alternator[Err2, Err]): Endpoint[PathInput, Input, alt.Out, Output, Middleware] = {
      val codec = HttpCodec.enumeration.f3(codec1, codec2, codec3)
      self.copy[PathInput, Input, alt.Out, Output, Middleware](error = codec | self.error)
    }

    def apply[Sub1 <: Err2: ClassTag, Sub2 <: Err2: ClassTag, Sub3 <: Err2: ClassTag, Sub4 <: Err2: ClassTag](
      codec1: HttpCodec[HttpCodecType.Status & HttpCodecType.Content, Sub1],
      codec2: HttpCodec[HttpCodecType.Status & HttpCodecType.Content, Sub2],
      codec3: HttpCodec[HttpCodecType.Status & HttpCodecType.Content, Sub3],
      codec4: HttpCodec[HttpCodecType.Status & HttpCodecType.Content, Sub4],
    )(implicit alt: Alternator[Err2, Err]): Endpoint[PathInput, Input, alt.Out, Output, Middleware] = {
      val codec = HttpCodec.enumeration.f4(codec1, codec2, codec3, codec4)
      self.copy[PathInput, Input, alt.Out, Output, Middleware](error = codec | self.error)
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
    )(implicit alt: Alternator[Err2, Err]): Endpoint[PathInput, Input, alt.Out, Output, Middleware] = {
      val codec = HttpCodec.enumeration.f5(codec1, codec2, codec3, codec4, codec5)
      self.copy[PathInput, Input, alt.Out, Output, Middleware](error = codec | self.error)
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
    )(implicit alt: Alternator[Err2, Err]): Endpoint[PathInput, Input, alt.Out, Output, Middleware] = {
      val codec = HttpCodec.enumeration.f6(codec1, codec2, codec3, codec4, codec5, codec6)
      self.copy[PathInput, Input, alt.Out, Output, Middleware](error = codec | self.error)
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
    )(implicit alt: Alternator[Err2, Err]): Endpoint[PathInput, Input, alt.Out, Output, Middleware] = {
      val codec = HttpCodec.enumeration.f7(codec1, codec2, codec3, codec4, codec5, codec6, codec7)
      self.copy[PathInput, Input, alt.Out, Output, Middleware](error = codec | self.error)
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
    )(implicit alt: Alternator[Err2, Err]): Endpoint[PathInput, Input, alt.Out, Output, Middleware] = {
      val codec = HttpCodec.enumeration.f8(codec1, codec2, codec3, codec4, codec5, codec6, codec7, codec8)
      self.copy[PathInput, Input, alt.Out, Output, Middleware](error = codec | self.error)
    }
  }

  private[endpoint] val defaultMediaTypes =
    NonEmptyChunk(MediaTypeWithQFactor(MediaType.application.`json`, Some(1)))
}
