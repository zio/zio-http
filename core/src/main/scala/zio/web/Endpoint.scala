package zio.web

import zio.schema.Schema
import zio.web.docs._

final case class Endpoint[M, I, O] (
  endpointName: String,
  doc: Doc,
  request: Schema[I],
  response: Schema[O],
  annotations: Annotations[M]
) { self =>
  type Metadata = M 
  type Request  = I 
  type Response = O 

  type Identity

  /**
   * Adds an annotation to the endpoint.
   */
  def @@[M0](metadata: M0): Endpoint[M with M0, I, O] =
    copy(annotations = self.annotations.add[M0](metadata))

  /**
   * Returns a new endpoint that attaches additional documentation to this
   * endpoint.
   */
  def ??(details: Doc): Endpoint[M, I, O] = copy(doc = doc <> details)

  /**
   * Returns a new endpoint that attaches additional (string) documentation
   * to this endpoint.
   */
  def ??(details: String): Endpoint[M, I, O] = copy(doc = doc <> Doc(details))

  def mapRequest[I0](f: Schema[I] => Schema[I0]): Endpoint[M, I0, O] =
    copy(request = f(request))

  def mapResponse[O0](f: Schema[O] => Schema[O0]): Endpoint[M, I, O0] =
    copy(response = f(response))

  /**
   * Returns a new endpoint that adds the specified request information
   * into the request required by this endpoint.
   */
  def request[I0](request2: Schema[I0]): Endpoint[M, (I, I0), O] =
    copy(request = request.zip(request2))

  /**
   * Returns a new endpoint that adds the specified response information
   * into the response produced by this endpoint.
   */
  def response[O0](response2: Schema[O0]): Endpoint[M, I, (O, O0)] =
    copy(response = response.zip(response2))

  def withRequest[I0](request: Schema[I0]): Endpoint[M, I0, O] =
    mapRequest(_ => request)

  def withResponse[O0](response: Schema[O0]): Endpoint[M, I, O0] =
    mapResponse(_ => response)

  // /**
  //  * Converts this into a handled endpoint.
  //  */
  // def handler[R](handler: I => URIO[R, O]): Api[R, M, I, O] =
  //   Api(endpointName, doc, request, response, annotations, handler)
}
object Endpoint {
  type Aux[M, I, O, Identity0] = 
    Endpoint[M, I, O] {
      type Identity = Identity0
    }
}