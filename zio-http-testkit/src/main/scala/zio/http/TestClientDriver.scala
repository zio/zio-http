package zio.http

import zio._

import zio.http.ChannelEvent.{ChannelUnregistered, UserEvent}
import zio.http.model.{Headers, Method, Scheme, Status, Version}
import zio.http.socket.{SocketApp, WebSocketChannelEvent, WebSocketFrame}

/**
 * Enables tests that use a client without needing a live Server
 *
 * @param behavior
 *   Contains the user-specified behavior that takes the place of the usual
 *   Server
 */
final case class TestClientDriver(behavior: Ref[HttpApp[Any, Throwable]], serverSocketBehavior: Ref[SocketApp[Any]])
    extends ZClient.Driver[Any, Throwable] {

  /**
   * Adds an exact 1-1 behavior
   * @param expectedRequest
   *   The request that will trigger the response
   * @param response
   *   The response to be returned when a user submits the response
   *
   * @example
   *   {{{
   *    TestClientDriver.addRequestResponse(Request.get(URL.root), Response.ok)
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
   *   New behavior to be added to the TestClientDriver
   * @tparam R
   *   Environment of the new handler's effect.
   *
   * @example
   *   {{{
   *    TestClientDriver.addHandler{case request  if request.method == Method.GET => ZIO.succeed(Response.ok)}
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

  override def socket[Env1](
    app: SocketApp[Env1],
    headers: Headers,
    url: URL,
    version: Version,
  )(implicit trace: Trace): ZIO[Env1 with Scope, Throwable, Response] = {
    for {
      env                   <- ZIO.environment[Env1]
      currentSocketBehavior <- serverSocketBehavior.get
      testChannelClient     <- TestChannel.make
      testChannelServer     <- TestChannel.make
      _ <- eventLoop("Server", testChannelClient, currentSocketBehavior, testChannelServer).forkDaemon
      _ <- eventLoop("Client", testChannelServer, app.provideEnvironment(env), testChannelClient).forkDaemon
    } yield Response.status(Status.SwitchingProtocols)
  }

  private val warnLongRunning =
    ZIO
      .log("Socket Application is taking a long time to run. You might have logic that does not terminate.")
      .delay(15.seconds)
      .withClock(Clock.ClockLive) *> ZIO.never

  private def eventLoop(name: String, channel: TestChannel, app: SocketApp[Any], otherChannel: TestChannel) =
    (for {
      pendEvent <- channel.pending race warnLongRunning
      _         <- app.message.get
        .apply(ChannelEvent(otherChannel, pendEvent))
        .tapError(e => ZIO.debug(s"Unexpected WebSocket $name error: " + e) *> otherChannel.close)
      _         <- ZIO.when(pendEvent == ChannelUnregistered) {
        otherChannel.close
      }
    } yield pendEvent).repeatWhile(event => shouldContinue(event))

  private def shouldContinue(event: ChannelEvent.Event[WebSocketFrame]) =
    event match {
      case ChannelEvent.ExceptionCaught(_)            => false
      case ChannelEvent.ChannelRead(message)          =>
        message match {
          case WebSocketFrame.Close(_, _) => false
          case _                          => true
        }
      case ChannelEvent.UserEventTriggered(userEvent) =>
        userEvent match {
          case UserEvent.HandshakeTimeout  => false
          case UserEvent.HandshakeComplete => true
        }
      case ChannelEvent.ChannelRegistered             => true
      case ChannelEvent.ChannelUnregistered           => false
    }

  def installSocketApp[Env1](
    app: Http[Any, Throwable, WebSocketChannelEvent, Unit],
  ): ZIO[Env1, Nothing, Unit] =
    for {
      env <- ZIO.environment[Env1]
      _   <- serverSocketBehavior.set(
        app
          .defaultWith(TestClientDriver.warnOnUnrecognizedEvent)
          .toHandler(Handler.response(Response(Status.NotFound)))
          .toSocketApp
          .provideEnvironment(env),
      )
    } yield ()
}

object TestClientDriver {

  /**
   * Adds an exact 1-1 behavior
   * @param request
   *   The request that will trigger the response
   * @param response
   *   The response to be returned when a user submits the response
   *
   * @example
   *   {{{
   *    TestClientDriver.addRequestResponse(Request.get(URL.root), Response.ok)
   *   }}}
   */
  def addRequestResponse(
    request: Request,
    response: Response,
  ): ZIO[TestClientDriver, Nothing, Unit] =
    ZIO.serviceWithZIO[TestClientDriver](_.addRequestResponse(request, response))

  /**
   * Adds a flexible handler for requests that are submitted by test cases
   * @param handler
   *   New behavior to be added to the TestClientDriver
   * @tparam R
   *   Environment of the new handler's effect.
   *
   * @example
   *   {{{
   *    TestClientDriver.addHandler{case request  if request.method == Method.GET => ZIO.succeed(Response.ok)}
   *   }}}
   */
  def addHandler[R](
    handler: PartialFunction[Request, ZIO[R, Throwable, Response]],
  ): ZIO[R with TestClientDriver, Nothing, Unit] =
    ZIO.serviceWithZIO[TestClientDriver](_.addHandler(handler))

  def installSocketApp(
    app: Http[Any, Throwable, WebSocketChannelEvent, Unit],
  ): ZIO[TestClientDriver, Nothing, Unit] =
    ZIO.serviceWithZIO[TestClientDriver](_.installSocketApp(app))

  val layer: ZLayer[Any, Nothing, TestClientDriver & Client] =
    ZLayer.scopedEnvironment {
      for {
        behavior       <- Ref.make[HttpApp[Any, Throwable]](Http.empty)
        socketBehavior <- Ref.make[SocketApp[Any]](SocketApp.apply(_ => ZIO.unit))
        testClient = TestClientDriver(behavior, socketBehavior): TestClientDriver
      } yield ZEnvironment[TestClientDriver, Client](testClient, ZClient.fromDriver(testClient))
    }

  private val warnOnUnrecognizedEvent = Http.collectZIO[WebSocketChannelEvent] { case other =>
    ZIO.fail(new Exception("Test Server received Unexpected event: " + other))
  }

}
