package zio.web

import zio._
import zio.web.docs._

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
trait EndpointModule extends codec.CodecModule { endpointModule =>
  type Handler[-R, -A, +B]        = A => zio.RIO[R, B]
  type Handler2[-R, -A1, -A2, +B] = (A1, A2) => zio.RIO[R, B]

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
    request: Codec[Request],
    response: Codec[Response],
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

    def mapRequest[Request2](f: Codec[Request] => Codec[Request2]): Endpoint[Metadata, Request2, Response, Handler] =
      copy(request = f(request))

    def mapResponse[Response2](
      f: Codec[Response] => Codec[Response2]
    ): Endpoint[Metadata, Request, Response2, Handler] =
      copy(response = f(response))

    /**
     * Returns a new endpoint that adds the specified request information
     * into the request required by this endpoint.
     */
    def request[Request2](request2: Codec[Request2]): Endpoint[Metadata, (Request, Request2), Response, Handler] =
      copy(request = zipCodec(request, request2))

    /**
     * Returns a new endpoint that adds the specified response information
     * into the response produced by this endpoint.
     */
    def response[Response2](response2: Codec[Response2]): Endpoint[Metadata, Request, (Response, Response2), Handler] =
      copy(response = zipCodec(response, response2))

    def withRequest[Request2](r: Codec[Request2]): Endpoint[Metadata, Request2, Response, Handler] =
      mapRequest(_ => r)

    def withResponse[Response2](r: Codec[Response2]): Endpoint[Metadata, Request, Response2, Handler] =
      mapResponse(_ => r)
  }

  type Endpoint2[+M, I, O] = Endpoint[M, I, O, Handler[Any, I, O]]

  /**
   * Constructs a new endpoint with the specified name.
   */
  final def endpoint(name: String): Endpoint[Any, Unit, Unit, Unit] =
    Endpoint(name, Doc.Empty, unitCodec, unitCodec, (), Annotations.none)

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
      tagA: zio.Tag[A]
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
