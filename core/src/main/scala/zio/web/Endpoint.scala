package zio.web

import zio.schema.Schema
import zio.web.docs._
import zio.web.internal.Combine

/**
 * A `Endpoint[M, P, I, O]` represents an endpoint that requires parameters `P` produced by metadata `M`
 * with input `I` and returns output `O`.
 */
final case class Endpoint[+M[+_], P, I, O](
  endpointName: String,
  doc: Doc,
  request: Schema[I],
  response: Schema[O],
  annotations: Annotations[M, P]
) { self =>
  type Params = P
  type Input  = I
  type Output = O
  type Id

  def +[M1[+_] >: M[_]](that: Endpoint[M1, _, _, _]): Endpoints[M1, Id with that.Id] =
    Endpoints(self, that)

  /**
   * Adds an annotation to the endpoint.
   */
  def @@[M1[+_] >: M[_], P2](metadata: M1[P2])(implicit c: Combine[P2, P]): Endpoint[M1, c.Out, I, O] =
    copy(annotations = self.annotations.+[M1, P2](metadata)(c))

  /**
   * Returns a new endpoint that attaches additional documentation to this
   * endpoint.
   */
  def ??(details: Doc): Endpoint[M, P, I, O] = copy(doc = doc <> details)

  /**
   * Returns a new endpoint that attaches additional (string) documentation
   * to this endpoint.
   */
  def ??(details: String): Endpoint[M, P, I, O] = copy(doc = doc <> Doc(details))

  def mapRequest[I0](f: Schema[I] => Schema[I0]): Endpoint[M, P, I0, O] =
    copy(request = f(request))

  def mapResponse[O0](f: Schema[O] => Schema[O0]): Endpoint[M, P, I, O0] =
    copy(response = f(response))

  /**
   * Returns a new endpoint that adds the specified request information
   * into the request required by this endpoint.
   */
  def request[I0](request2: Schema[I0]): Endpoint[M, P, (I, I0), O] =
    copy(request = request.zip(request2))

  /**
   * Returns a new endpoint that adds the specified response information
   * into the response produced by this endpoint.
   */
  def response[O0](response2: Schema[O0]): Endpoint[M, P, I, (O, O0)] =
    copy(response = response.zip(response2))

  def withRequest[I0](request: Schema[I0]): Endpoint[M, P, I0, O] =
    mapRequest(_ => request)

  def withResponse[O0](response: Schema[O0]): Endpoint[M, P, I, O0] =
    mapResponse(_ => response)
}

object Endpoint {

  type Aux[+M[+_], P, I, O, Id0] =
    Endpoint[M, P, I, O] {
      type Id = Id0
    }
}
