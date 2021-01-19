package zio.web

import zio.URIO
import zio.schema.Schema
import zio.web.docs._

sealed trait Endpoint[+M, I, O] {

  def endpointName: String
  def doc: Doc
  def request: Schema[I]
  def response: Schema[O]
  def annotations: Annotations[M]
}

object Endpoint {

  sealed case class Api[-R, +M, I, O] private [web](
    endpointName: String,
    doc: Doc,
    request: Schema[I],
    response: Schema[O],
    annotations: Annotations[M],
    handler: I => URIO[R, O]
  ) extends Endpoint[M, I, O]

  sealed case class Def[+M, I, O] private [web](
    endpointName: String,
    doc: Doc,
    request: Schema[I],
    response: Schema[O],
    annotations: Annotations[M]
  ) extends Endpoint[M, I, O] { self =>

    /**
     * Adds an annotation to the endpoint.
     */
    def @@[M0](metadata: M0): Def[M with M0, I, O] =
      copy(annotations = self.annotations.add[M0](metadata))

    /**
     * Returns a new endpoint that attaches additional documentation to this
     * endpoint.
     */
    def ??(details: Doc): Def[M, I, O] = copy(doc = doc <> details)

    /**
     * Returns a new endpoint that attaches additional (string) documentation
     * to this endpoint.
     */
    def ??(details: String): Def[M, I, O] = copy(doc = doc <> Doc(details))

    def mapRequest[I0](f: Schema[I] => Schema[I0]): Def[M, I0, O] =
      copy(request = f(request))

    def mapResponse[O0](f: Schema[O] => Schema[O0]): Def[M, I, O0] =
      copy(response = f(response))

    /**
     * Returns a new endpoint that adds the specified request information
     * into the request required by this endpoint.
     */
    def request[I0](request2: Schema[I0]): Def[M, (I, I0), O] =
      copy(request = request.zip(request2))

    /**
     * Returns a new endpoint that adds the specified response information
     * into the response produced by this endpoint.
     */
    def response[O0](response2: Schema[O0]): Def[M, I, (O, O0)] =
      copy(response = response.zip(response2))

    def withRequest[I0](request: Schema[I0]): Def[M, I0, O] =
      mapRequest(_ => request)

    def withResponse[O0](response: Schema[O0]): Def[M, I, O0] =
      mapResponse(_ => response)

    /**
     * Converts this into a handled endpoint.
     */
    def handler[R](handler: I => URIO[R, O]): Api[R, M, I, O] =
      Api(endpointName, doc, request, response, annotations, handler)
  }
}