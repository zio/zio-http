package zio.http.api

import zio.http.Method

import zio._
import zio.schema._
import zio.stream.ZStream

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
  def ??(that: Doc): API[Input, Output] = copy(doc = self.doc + that)

  def handle[R, E](f: Input => ZIO[R, E, Output]): HandledAPI[R, E, Input, Output] =
    HandledAPI(self, f)

  /**
   * Adds a new element of input to the API, which can come from the portion of
   * the HTTP path not yet consumed, the query string parameters, or the HTTP
   * headers of the request.
   */
  def in[Input2](in2: In[Input2])(implicit combiner: Combiner[Input, Input2]): API[combiner.Out, Output] =
    copy(input = self.input ++ in2)

  /**
   * Changes the output type of the endpoint to the specified output type.
   */
  def out[Output2: Schema]: API[Input, Output2] =
    copy(output = Out.Value(implicitly[Schema[Output2]]))

  /**
   * Changes the output type of the endpoint to be a stream of the specified
   * output type.
   */
  def outStream[Output2: Schema]: API[Input, ZStream[Any, Throwable, Output2]] =
    copy(output = Out.Stream(implicitly[Schema[Output2]]))
}

object API {

  /**
   * Constructs an API for a DELETE endpoint, given the specified input. It is
   * not necessary to specify the full input to the endpoint upfront, as the
   * `API#in` method can be used to incrementally append additional input to the
   * definition of the API.
   */
  def delete[Input](route: In[Input]): API[Input, Unit] =
    API(Method.GET, route, Out.unit, Doc.empty)

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
