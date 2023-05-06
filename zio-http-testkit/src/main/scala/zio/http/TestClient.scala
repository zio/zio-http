package zio.http

import zio._

import zio.http.ChannelEvent.{ChannelUnregistered, UserEvent}
import zio.http.socket.{SocketApp, WebSocketChannel, WebSocketChannelEvent, WebSocketFrame}
import zio.http.{Headers, Method, Scheme, Status, Version}

/**
 * Enables tests that use a client without needing a live Server
 *
 * @param behavior
 *   Contains the user-specified behavior that takes the place of the usual
 *   Server
 */
final case class TestClient(behavior: Ref[HttpApp[Any, Throwable]], serverSocketBehavior: Ref[SocketApp[Any]])
    extends Client {

  /**
   * Adds an exact 1-1 behavior
   * @param expectedRequest
   *   The request that will trigger the response
   * @param response
   *   The response to be returned when a user submits the response
   *
   * @example
   *   {{{
   *    TestClient.addRequestResponse(Request.get(URL.root), Response.ok)
   *   }}}
   */
  def addRequestResponse(
    expectedRequest: Request,
    response: Response,
  ): ZIO[Any, Nothing, Unit] = {
    val handler: PartialFunction[Request, ZIO[Any, Throwable, Response]] = {

      case realRequest if {
            // The way that the Client breaks apart and re-assembles the request prevents a straightforward
            //    expectedRequest == realRequest
            expectedRequest.url.relative == realRequest.url &&
            expectedRequest.method == realRequest.method &&
            expectedRequest.headers.toSet.forall(expectedHeader => realRequest.headers.toSet.contains(expectedHeader))
          } =>
        ZIO.succeed(response)
    }
    addHandler(handler)
  }

  /**
   * Adds a flexible handler for requests that are submitted by test cases
   * @param handler
   *   New behavior to be added to the TestClient
   * @tparam R
   *   Environment of the new handler's effect.
   *
   * @example
   *   {{{
   *    TestClient.addHandler{case request  if request.method == Method.GET => ZIO.succeed(Response.ok)}
   *   }}}
   */
  def addHandler[R](
    handler: PartialFunction[Request, ZIO[R, Throwable, Response]],
  ): ZIO[R, Nothing, Unit] =
    for {
      r                <- ZIO.environment[R]
      previousBehavior <- behavior.get
      newBehavior                  = handler.andThen(_.provideEnvironment(r))
      app: HttpApp[Any, Throwable] = Http.collectZIO(newBehavior)
      _ <- behavior.set(previousBehavior.defaultWith(app))
    } yield ()

  def headers: Headers = Headers.empty

  def method: Method = Method.GET

  def sslConfig: Option[ClientSSLConfig] = None

  def url: URL = URL(Path.root)

  def version: Version = Version.Http_1_1

  override def request(
    version: Version,
    method: Method,
    url: URL,
    headers: Headers,
    body: Body,
    sslConfig: Option[zio.http.ClientSSLConfig],
  )(implicit trace: Trace): ZIO[Any, Throwable, Response] =
    for {
      currentBehavior <- behavior.get
      request = Request(
        body = body,
        headers = headers,
        method = method,
        url = url.relative,
        version = version,
        remoteAddress = None,
      )
      response <- currentBehavior.runZIO(request).catchAll {
        case Some(value) => ZIO.succeed(Response(status = Status.BadRequest, body = Body.fromString(value.toString)))
        case None        => ZIO.succeed(Response.status(Status.NotFound))
      }
    } yield response

  def socket[Env1](
    version: Version,
    url: URL,
    headers: Headers,
    app: SocketApp[Env1],
  )(implicit trace: Trace): ZIO[Env1 with Scope, Throwable, Response] = {
    for {
      env                   <- ZIO.environment[Env1]
      currentSocketBehavior <- serverSocketBehavior.get
      serverToClient        <- Queue.unbounded[WebSocketChannelEvent]
      clientToServer        <- Queue.unbounded[WebSocketChannelEvent]
      testChannelClient     <- TestChannel.make(serverToClient, clientToServer)
      testChannelServer     <- TestChannel.make(clientToServer, serverToClient)
      _ <- eventLoop("Server", testChannelClient, currentSocketBehavior, testChannelServer).forkDaemon
      _ <- eventLoop("Client", testChannelServer, app.provideEnvironment(env), testChannelClient).forkDaemon
    } yield Response.status(Status.SwitchingProtocols)
  }

  // private val warnLongRunning =
  //   ZIO
  //     .log("Socket Application is taking a long time to run. You might have logic that does not terminate.")
  //     .delay(15.seconds)
  //     .withClock(Clock.ClockLive) *> ZIO.never

  private def eventLoop(name: String, channel: TestChannel, app: SocketApp[Any], otherChannel: TestChannel) = {
    val _ = (name, otherChannel)
    app.run(channel)
  }

  // private def shouldContinue(event: WebSocketChannelEvent) =
  //   event match {
  //     case ChannelEvent.ExceptionCaught(_)            => false
  //     case ChannelEvent.ChannelRead(message)          =>
  //       message match {
  //         case WebSocketFrame.Close(_, _) => false
  //         case _                          => true
  //       }
  //     case ChannelEvent.UserEventTriggered(userEvent) =>
  //       userEvent match {
  //         case UserEvent.HandshakeTimeout  => false
  //         case UserEvent.HandshakeComplete => true
  //       }
  //     case ChannelEvent.ChannelRegistered             => true
  //     case ChannelEvent.ChannelUnregistered           => false
  //   }

  def installSocketApp[Env1](
    app: Http[Any, Throwable, WebSocketChannel, Unit],
  ): ZIO[Env1, Nothing, Unit] =
    for {
      env <- ZIO.environment[Env1]
      _   <- serverSocketBehavior.set(
        app
          .toHandler(Handler.response(Response(Status.NotFound)))
          .toSocketApp
          .provideEnvironment(env),
      )
    } yield ()
}

object TestClient {

  /**
   * Adds an exact 1-1 behavior
   * @param request
   *   The request that will trigger the response
   * @param response
   *   The response to be returned when a user submits the response
   *
   * @example
   *   {{{
   *    TestClient.addRequestResponse(Request.get(URL.root), Response.ok)
   *   }}}
   */
  def addRequestResponse(
    request: Request,
    response: Response,
  ): ZIO[TestClient, Nothing, Unit] =
    ZIO.serviceWithZIO[TestClient](_.addRequestResponse(request, response))

  /**
   * Adds a flexible handler for requests that are submitted by test cases
   * @param handler
   *   New behavior to be added to the TestClient
   * @tparam R
   *   Environment of the new handler's effect.
   *
   * @example
   *   {{{
   *    TestClient.addHandler{case request  if request.method == Method.GET => ZIO.succeed(Response.ok)}
   *   }}}
   */
  def addHandler[R](
    handler: PartialFunction[Request, ZIO[R, Throwable, Response]],
  ): ZIO[R with TestClient, Nothing, Unit] =
    ZIO.serviceWithZIO[TestClient](_.addHandler(handler))

  def installSocketApp(
    app: Http[Any, Throwable, WebSocketChannel, Unit],
  ): ZIO[TestClient, Nothing, Unit] =
    ZIO.serviceWithZIO[TestClient](_.installSocketApp(app))

  val layer: ZLayer[Any, Nothing, TestClient] =
    ZLayer.scoped {
      for {
        behavior       <- Ref.make[HttpApp[Any, Throwable]](Http.empty)
        socketBehavior <- Ref.make[SocketApp[Any]](SocketApp.apply(_ => ZIO.unit))
      } yield TestClient(behavior, socketBehavior)
    }

}
