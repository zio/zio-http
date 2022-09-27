package zio.http.api

import zio._
import zio.http.model.Method
import zio.schema._
import zio.stream.ZStream
import zio.stacktracer.TracingImplicits.disableAutoTrace // scalafix:ok;

/**
 * An [[zio.http.api.API]] represents an API endpoint for the HTTP protocol.
 * Every `API` has an input, which comes from a combination of the HTTP path,
 * query string parameters, and headers, and an output, which is the data
 * computed by the handler of the API.
 *
 * As [[zio.http.api.API]] is a purely declarative encoding of an endpoint, it
 * is possible to use this model to generate a [[zio.http.HttpApp]] (by
 * supplying a handler for the endpoint), to generate OpenAPI documentation, to
 * generate a type-safe Scala client for the endpoint, and possibly, to generate
 * client libraries in other programming languages.
 */
final case class API[Input, Output](
  method: Method,
  input: In[Input],
  output: Out[Output],
  doc: Doc,
) { self =>
  type Id

  /**
   * Combines this API and another group of APIs.
   */
  def ++[Ids](that: APIs[Ids]): APIs[Id with Ids] = APIs(self).++[Ids](that)

  /**
   * Combines this API with another API.
   */
  def ++(that: API[_, _]): APIs[Id with that.Id] = APIs(self).++[that.Id](APIs(that))

  def apply(input: Input): Invocation[Id, Input, Output] =
    Invocation(self, input)

  def apply[A, B](a: A, b: B)(implicit ev: (A, B) <:< Input): Invocation[Id, Input, Output] =
    Invocation(self, ev((a, b)))

  def apply[A, B, C](a: A, b: B, c: C)(implicit ev: (A, B, C) <:< Input): Invocation[Id, Input, Output] =
    Invocation(self, ev((a, b, c)))

  def apply[A, B, C, D](a: A, b: B, c: C, d: D)(implicit ev: (A, B, C, D) <:< Input): Invocation[Id, Input, Output] =
    Invocation(self, ev((a, b, c, d)))

  def apply[A, B, C, D, E](a: A, b: B, c: C, d: D, e: E)(implicit
    ev: (A, B, C, D, E) <:< Input,
  ): Invocation[Id, Input, Output] =
    Invocation(self, ev((a, b, c, d, e)))

  def apply[A, B, C, D, E, F](a: A, b: B, c: C, d: D, e: E, f: F)(implicit
    ev: (A, B, C, D, E, F) <:< Input,
  ): Invocation[Id, Input, Output] =
    Invocation(self, ev((a, b, c, d, e, f)))

  def apply[A, B, C, D, E, F, G](a: A, b: B, c: C, d: D, e: E, f: F, g: G)(implicit
    ev: (A, B, C, D, E, F, G) <:< Input,
  ): Invocation[Id, Input, Output] =
    Invocation(self, ev((a, b, c, d, e, f, g)))

  def apply[A, B, C, D, E, F, G, H](a: A, b: B, c: C, d: D, e: E, f: F, g: G, h: H)(implicit
    ev: (A, B, C, D, E, F, G, H) <:< Input,
  ): Invocation[Id, Input, Output] =
    Invocation(self, ev((a, b, c, d, e, f, g, h)))

  def apply[A, B, C, D, E, F, G, H, I](a: A, b: B, c: C, d: D, e: E, f: F, g: G, h: H, i: I)(implicit
    ev: (A, B, C, D, E, F, G, H, I) <:< Input,
  ): Invocation[Id, Input, Output] =
    Invocation(self, ev((a, b, c, d, e, f, g, h, i)))

  /**
   * Returns a new API that is derived from this one, but which includes
   * additional documentation that will be included in OpenAPI generation.
   */
  def ??(that: Doc): API[Input, Output] = copy(doc = self.doc + that)

  /**
   * Converts this API, which is an abstract description of an endpoint, into a
   * service, which is a concrete implementation of the endpoint. In order to
   * convert an API into a service, you must specify a function which handles
   * the input, and returns the output.
   */
  def handle[R, E](f: Input => ZIO[R, E, Output]): Service[R, E, Id] =
    Service.HandledAPI[R, E, Input, Output, Id](self, f).withAllIds[Id]

  /**
   * Changes the identity of the API to the specified singleton string type.
   * Currently this is only used to "prettify" type signatures and, assuming
   * each API is uniquely identified, has no effect on behavior.
   */
  def id[I <: String with Singleton](i: I): API.WithId[Input, Output, I] = {
    val _ = i
    self.asInstanceOf[API.WithId[Input, Output, I]]
  }

  /**
   * Adds a new element of input to the API, which can come from the portion of
   * the HTTP path not yet consumed, the query string parameters, or the HTTP
   * headers of the request.
   */
  def in[Input2](in2: In[Input2])(implicit combiner: Combiner[Input, Input2]): API.WithId[combiner.Out, Output, Id] =
    copy(input = self.input ++ in2).withId[Id]

  /**
   * Changes the output type of the endpoint to the specified output type.
   */
  def out[Output2: Schema]: API.WithId[Input, Output2, Id] =
    copy(output = Out.Value(implicitly[Schema[Output2]])).withId[Id]

  /**
   * Changes the output type of the endpoint to be a stream of the specified
   * output type.
   */
  def outStream[Output2: Schema]: API.WithId[Input, ZStream[Any, Throwable, Output2], Id] =
    copy(output = Out.Stream(implicitly[Schema[Output2]])).withId[Id]

  private def withId[I]: API.WithId[Input, Output, I] =
    self.asInstanceOf[API.WithId[Input, Output, I]]
}

object API {
  type WithId[I, O, X] = API[I, O] { type Id = X }

  /**
   * Constructs an API for a DELETE endpoint, given the specified input. It is
   * not necessary to specify the full input to the endpoint upfront, as the
   * `API#in` method can be used to incrementally append additional input to the
   * definition of the API.
   */
  def delete[Input](route: In[Input]): API[Input, Unit] =
    API(Method.DELETE, route, Out.unit, Doc.empty)

  /**
   * Constructs an API for a GET endpoint, given the specified input. It is not
   * necessary to specify the full input to the endpoint upfront, as the
   * `API#in` method can be used to incrementally append additional input to the
   * definition of the API.
   */
  def get[Input](route: In[Input]): API[Input, Unit] =
    API(Method.GET, route, Out.unit, Doc.empty)

  /**
   * Constructs an API for a POST endpoint, given the specified input. It is not
   * necessary to specify the full input to the endpoint upfront, as the
   * `API#in` method can be used to incrementally append additional input to the
   * definition of the API.
   */
  def post[Input](route: In[Input]): API[Input, Unit] =
    API(Method.POST, route, Out.unit, Doc.empty)

  /**
   * Constructs an API for a PUT endpoint, given the specified input. It is not
   * necessary to specify the full input to the endpoint upfront, as the
   * `API#in` method can be used to incrementally append additional input to the
   * definition of the API.
   */
  def put[Input](route: In[Input]): API[Input, Unit] =
    API(Method.PUT, route, Out.unit, Doc.empty)
}
