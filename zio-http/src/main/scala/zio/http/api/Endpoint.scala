package zio.http.api

import zio._
import zio.http.api.CodecType.Path
import zio.http.model.{Header, Headers, Method, Status}
import zio.schema._
import zio.stream.ZStream
import zio.stacktracer.TracingImplicits.disableAutoTrace // scalafix:ok;

/**
 * An [[zio.http.api.Endpoint]] represents an API endpoint for the HTTP
 * protocol. Every `API` has an input, which comes from a combination of the
 * HTTP path, query string parameters, and headers, and an output, which is the
 * data computed by the handler of the API.
 *
 * MiddlewareInput : Example: A subset of `HttpCodec[Input]` that doesn't give
 * access to `Input` MiddlewareOutput: Example: A subset of `Out[Output]` that
 * doesn't give access to `Output` Input: Example: Int Output: Example: User
 *
 * As [[zio.http.api.Endpoint]] is a purely declarative encoding of an endpoint,
 * it is possible to use this model to generate a [[zio.http.HttpApp]] (by
 * supplying a handler for the endpoint), to generate OpenAPI documentation, to
 * generate a type-safe Scala client for the endpoint, and possibly, to generate
 * client libraries in other programming languages.
 */
final case class Endpoint[Input, Err, Output, Middleware <: EndpointMiddleware](
  input: HttpCodec[CodecType.RequestType, Input],
  output: HttpCodec[CodecType.ResponseType, Output],
  error: HttpCodec[CodecType.ResponseType, Err],
  doc: Doc,
  middleware: Middleware,
) { self =>
  import self.{middleware => mw}

  def apply(input: Input): Invocation[Input, Err, Output, Middleware] =
    Invocation(self, input)

  def apply[A, B](a: A, b: B)(implicit
    ev: (A, B) <:< Input,
  ): Invocation[Input, Err, Output, Middleware] =
    Invocation(self, ev((a, b)))

  def apply[A, B, C](a: A, b: B, c: C)(implicit
    ev: (A, B, C) <:< Input,
  ): Invocation[Input, Err, Output, Middleware] =
    Invocation(self, ev((a, b, c)))

  def apply[A, B, C, D](a: A, b: B, c: C, d: D)(implicit
    ev: (A, B, C, D) <:< Input,
  ): Invocation[Input, Err, Output, Middleware] =
    Invocation(self, ev((a, b, c, d)))

  def apply[A, B, C, D, E](a: A, b: B, c: C, d: D, e: E)(implicit
    ev: (A, B, C, D, E) <:< Input,
  ): Invocation[Input, Err, Output, Middleware] =
    Invocation(self, ev((a, b, c, d, e)))

  def apply[A, B, C, D, E, F](a: A, b: B, c: C, d: D, e: E, f: F)(implicit
    ev: (A, B, C, D, E, F) <:< Input,
  ): Invocation[Input, Err, Output, Middleware] =
    Invocation(self, ev((a, b, c, d, e, f)))

  def apply[A, B, C, D, E, F, G](a: A, b: B, c: C, d: D, e: E, f: F, g: G)(implicit
    ev: (A, B, C, D, E, F, G) <:< Input,
  ): Invocation[Input, Err, Output, Middleware] =
    Invocation(self, ev((a, b, c, d, e, f, g)))

  def apply[A, B, C, D, E, F, G, H](a: A, b: B, c: C, d: D, e: E, f: F, g: G, h: H)(implicit
    ev: (A, B, C, D, E, F, G, H) <:< Input,
  ): Invocation[Input, Err, Output, Middleware] =
    Invocation(self, ev((a, b, c, d, e, f, g, h)))

  def apply[A, B, C, D, E, F, G, H, I](a: A, b: B, c: C, d: D, e: E, f: F, g: G, h: H, i: I)(implicit
    ev: (A, B, C, D, E, F, G, H, I) <:< Input,
  ): Invocation[Input, Err, Output, Middleware] =
    Invocation(self, ev((a, b, c, d, e, f, g, h, i)))

  def apply[A, B, C, D, E, F, G, H, I, J](a: A, b: B, c: C, d: D, e: E, f: F, g: G, h: H, i: I, j: J)(implicit
    ev: (A, B, C, D, E, F, G, H, I, J) <:< Input,
  ): Invocation[Input, Err, Output, Middleware] =
    Invocation(self, ev((a, b, c, d, e, f, g, h, i, j)))

  def apply[A, B, C, D, E, F, G, H, I, J, K](a: A, b: B, c: C, d: D, e: E, f: F, g: G, h: H, i: I, j: J, k: K)(implicit
    ev: (A, B, C, D, E, F, G, H, I, J, K) <:< Input,
  ): Invocation[Input, Err, Output, Middleware] =
    Invocation(self, ev((a, b, c, d, e, f, g, h, i, j, k)))

  def apply[A, B, C, D, E, F, G, H, I, J, K, L](a: A, b: B, c: C, d: D, e: E, f: F, g: G, h: H, i: I, j: J, k: K, l: L)(
    implicit ev: (A, B, C, D, E, F, G, H, I, J, K, L) <:< Input,
  ): Invocation[Input, Err, Output, Middleware] =
    Invocation(self, ev((a, b, c, d, e, f, g, h, i, j, k, l)))

  /**
   * Returns a new API that is derived from this one, but which includes
   * additional documentation that will be included in OpenAPI generation.
   */
  def ??(that: Doc): Endpoint[Input, Err, Output, Middleware] = copy(doc = self.doc + that)

  /**
   * Returns a new endpoint that requires the specified headers to be present.
   */
  def header[A](codec: HeaderCodec[A])(implicit
    combiner: Combiner[Input, A],
  ): Endpoint[combiner.Out, Err, Output, Middleware] =
    copy(input = self.input ++ codec)

  /**
   * Converts this endpoint, which is an abstract description of an endpoint,
   * into a route, which maps a path to a handler for that path. In order to
   * convert an endpoint into a route, you must specify a function which handles
   * the input, and returns the output.
   */
  def implement[Env](f: Input => ZIO[Env, Err, Output]): Routes[Env, Err, Middleware] =
    Routes.Single[Env, Err, Input, Output, Middleware](self, f)

  /**
   * Returns a new endpoint derived from this one, whose request content must
   * satisfy the specified schema.
   */
  def in[Input2: Schema]: Endpoint[Input2, Err, Output, Middleware] =
    copy(input = HttpCodec.Body(implicitly[Schema[Input2]]))

  /**
   * Returns a new endpoint derived from this one whose middleware is composed
   * from the existing middleware of this endpoint, and the specified
   * middleware.
   */
  def @@[M2 <: EndpointMiddleware](that: M2)(implicit
    inCombiner: Combiner[middleware.In, that.In],
    outCombiner: Combiner[middleware.Out, that.Out],
    errAlternator: Alternator[mw.Err, that.Err],
  ): Endpoint[Input, Err, Output, EndpointMiddleware.Typed[inCombiner.Out, errAlternator.Out, outCombiner.Out]] =
    Endpoint(input, output, error, doc, mw ++ that)

  /**
   * Returns a new endpoint derived from this one, whose output type is the
   * specified type for the ok status code.
   */
  def out[Output2: Schema](implicit alt: Alternator[Output, Output2]): Endpoint[Input, Err, alt.Out, Middleware] =
    out[Output2](Status.Ok)

  /**
   * Returns a new endpoint derived from this one, whose output type is the
   * specified type for the specified status code.
   */
  def out[Output2: Schema](
    status: Status,
  )(implicit alt: Alternator[Output, Output2]): Endpoint[Input, Err, alt.Out, Middleware] =
    Endpoint(
      input,
      output = (self.output | HttpCodec.Body(implicitly[Schema[Output2]])) ++ StatusCodec.status(status),
      error,
      doc,
      mw,
    )

  /**
   * Returns a new endpoint derived from this one, whose output type is a stream
   * of the specified type for the ok status code.
   */
  def outStream[Output2: Schema](implicit
    alt: Alternator[Output, ZStream[Any, Throwable, Output2]],
  ): Endpoint[Input, Err, alt.Out, Middleware] =
    outStream[Output2](Status.Ok)

  /**
   * Returns a new endpoint derived from this one, whose output type is a stream
   * of the specified type for the specified status code.
   */
  def outStream[Output2: Schema](
    status: Status,
  )(implicit alt: Alternator[Output, ZStream[Any, Throwable, Output2]]): Endpoint[Input, Err, alt.Out, Middleware] =
    Endpoint(
      input,
      output = (self.output | HttpCodec.BodyStream(implicitly[Schema[Output2]])) ++ StatusCodec.status(status),
      error,
      doc,
      mw,
    )

  /**
   * Returns a new endpoint with the specified path appended.
   */
  def path[A](codec: PathCodec[A])(implicit
    combiner: Combiner[Input, A],
  ): Endpoint[combiner.Out, Err, Output, Middleware] =
    copy(input = self.input ++ codec)

  /**
   * Returns a new endpoint that requires the specified query.
   */
  def query[A](codec: QueryCodec[A])(implicit
    combiner: Combiner[Input, A],
  ): Endpoint[combiner.Out, Err, Output, Middleware] =
    copy(input = self.input ++ codec)
}

object Endpoint {

  /**
   * Constructs an endpoint for an HTTP DELETE endpoint, whose path is described
   * by the specified path codec.
   */
  def delete[Input](route: PathCodec[Input]): Endpoint[Input, ZNothing, ZNothing, EndpointMiddleware.None] = {
    Endpoint(
      route ++ MethodCodec.delete,
      HttpCodec.unused,
      HttpCodec.unused,
      Doc.empty,
      EndpointMiddleware.None,
    )
  }

  /**
   * Constructs an endpoint for an HTTP GET endpoint, whose path is described by
   * the specified path codec.
   */
  def get[Input](route: PathCodec[Input]): Endpoint[Input, ZNothing, ZNothing, EndpointMiddleware.None] =
    Endpoint(
      route ++ MethodCodec.get,
      HttpCodec.unused,
      HttpCodec.unused,
      Doc.empty,
      EndpointMiddleware.None,
    )

  /**
   * Constructs an endpoint for an HTTP POST endpoint, whose path is described
   * by the specified path codec.
   */
  def post[Input](route: PathCodec[Input]): Endpoint[Input, ZNothing, ZNothing, EndpointMiddleware.None] =
    Endpoint(
      route ++ MethodCodec.post,
      HttpCodec.unused,
      HttpCodec.unused,
      Doc.empty,
      EndpointMiddleware.None,
    )

  /**
   * Constructs an endpoint for an HTTP PUT endpoint, whose path is described by
   * the specified path codec.
   */
  def put[Input](route: PathCodec[Input]): Endpoint[Input, ZNothing, ZNothing, EndpointMiddleware.None] =
    Endpoint(
      route ++ MethodCodec.put,
      HttpCodec.unused,
      HttpCodec.unused,
      Doc.empty,
      EndpointMiddleware.None,
    )
}
