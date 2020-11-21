package zio.web

import zio.schema.Schema
import _root_.zio.web.docs._
import _root_.zio.{ Has, RIO, Tag, Task, ZIO }

/*

In gRPC, services are described by 1 or more "methods" with a name, e.g.:

  rpc HelloWorld(HelloRequest) returns (HelloResponse);

  rpc GetUserProfile(UserIdRequest) returns (UserProfileResponse)

In HTTP, services are described by 1 or more routes, consisting of HTTP Method, URL pattern, header pattern, e.g.:

  GET /users/{id}
  Content-type: application/json

  POST /users-service/get-user-profile?userId=123

In Thrift, services are described by 1 or more "methods" with a name, e.g.:

  string helloWorld(string input)

 */
trait EndpointModule { endpointModule =>
  type Handler[-R, -A, +B]        = A => RIO[R, B]
  type Handler2[-R, -A1, -A2, +B] = (A1, A2) => RIO[R, B]

  trait ClientService[A] {

    def invoke[M, Request, Response, H](endpoint: Endpoint[M, Request, Response, H], request: Request)(
      implicit ev: A <:< Endpoint[M, Request, Response, H]
    ): Task[Response]
  }

  sealed trait Annotations[+A] { self =>
    def +[M](that: M): Annotations[M with A] = Annotations.Cons[M, A](that, self)

    def add[M](that: M): Annotations[M with A] = Annotations.Cons[M, A](that, self)
  }

  object Annotations {
    case object None                                                   extends Annotations[Any]
    sealed case class Cons[M, +Tail](head: M, tail: Annotations[Tail]) extends Annotations[M with Tail]

    val none: Annotations[Any] = None
  }

  sealed case class Endpoint[+Metadata, Request, Response, +Handler](
    endpointName: String,
    doc: Doc,
    request: Schema[Request],
    response: Schema[Response],
    handler: Handler,
    annotations: Annotations[Metadata]
  ) { self =>

    /**
     * Adds an annotation to the endpoint.
     */
    def @@[M2](metadata: M2): Endpoint[Metadata with M2, Request, Response, Handler] =
      copy(annotations = self.annotations.add[M2](metadata))

    /**
     * Returns a new endpoint that attaches additional documentation to this
     * endpoint.
     */
    def ??(details: Doc): Endpoint[Metadata, Request, Response, Handler] = copy(doc = doc <> details)

    /**
     * Returns a new endpoint that attaches additional (string) documentation
     * to this endpoint.
     */
    def ??(details: String): Endpoint[Metadata, Request, Response, Handler] = copy(doc = doc <> Doc(details))

    def handler(h: endpointModule.Handler[Any, Request, Response]): Endpoint2[Metadata, Request, Response] =
      copy(handler = h)

    def mapRequest[Request2](f: Schema[Request] => Schema[Request2]): Endpoint[Metadata, Request2, Response, Handler] =
      copy(request = f(request))

    def mapResponse[Response2](
      f: Schema[Response] => Schema[Response2]
    ): Endpoint[Metadata, Request, Response2, Handler] =
      copy(response = f(response))

    /**
     * Returns a new endpoint that adds the specified request information
     * into the request required by this endpoint.
     */
    def request[Request2](request2: Schema[Request2]): Endpoint[Metadata, (Request, Request2), Response, Handler] =
      copy(request = request.zip(request2))

    /**
     * Returns a new endpoint that adds the specified response information
     * into the response produced by this endpoint.
     */
    def response[Response2](response2: Schema[Response2]): Endpoint[Metadata, Request, (Response, Response2), Handler] =
      copy(response = response.zip(response2))

    def withRequest[Request2](r: Schema[Request2]): Endpoint[Metadata, Request2, Response, Handler] =
      mapRequest(_ => r)

    def withResponse[Response2](r: Schema[Response2]): Endpoint[Metadata, Request, Response2, Handler] =
      mapResponse(_ => r)
  }

  type Endpoint2[+M, I, O] = Endpoint[M, I, O, Handler[Any, I, O]]

  /**
   * Constructs a new endpoint with the specified name.
   */
  final def endpoint(name: String): Endpoint[Any, Unit, Unit, Unit] =
    Endpoint(name, Doc.Empty, Schema[Unit], Schema[Unit], (), Annotations.none)

  /**
   * Constructs a new endpoint with the specified name and text documentation.
   */
  final def endpoint(name: String, text: String): Endpoint[Any, Unit, Unit, Unit] =
    endpoint(name) ?? text

  trait Endpoints[+M, A] { self =>

    /**
     * Invokes the endpoint by using a client service from the environment.
     */
    def invoke[M, Request, Response](endpoint: Endpoint2[M, Request, Response])(request: Request)(
      implicit ev: A <:< Endpoint2[M, Request, Response],
      tagA: Tag[A]
    ): ZIO[Has[ClientService[A]], Throwable, Response] = {
      val _ = tagA
      ZIO.accessM[Has[ClientService[A]]](_.get[ClientService[A]].invoke(endpoint, request))
    }

    def ::[M1 >: M, I, O](that: Endpoint2[M1, I, O]): Endpoints[M1, Endpoint2[M1, I, O] with A] =
      Endpoints.Cons[M1, I, O, A](that, self)
  }

  object Endpoints {
    private[web] case object Empty extends Endpoints[Nothing, Any]
    sealed private[web] case class Cons[M, I, O, X](head: Endpoint2[M, I, O], tail: Endpoints[M, X])
        extends Endpoints[M, Endpoint2[M, I, O] with X]

    val empty: Endpoints[Nothing, Any] = Empty
  }
}
