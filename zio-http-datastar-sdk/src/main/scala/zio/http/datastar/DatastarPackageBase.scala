package zio.http.datastar

import scala.language.implicitConversions

import zio._

import zio.stream.ZStream

import zio.http._
import zio.http.codec.{Doc, HttpCodec, HttpContentCodec}
import zio.http.endpoint.{AuthType, Endpoint}
import zio.http.template2._

trait DatastarPackageBase extends Attributes {
  private val headers = Headers(
    Header.CacheControl.NoCache,
    Header.Connection.KeepAlive,
  )

  val Signal: zio.http.datastar.signal.Signal.type             = zio.http.datastar.signal.Signal
  val SignalUpdate: zio.http.datastar.signal.SignalUpdate.type = zio.http.datastar.signal.SignalUpdate
  val SignalName: zio.http.datastar.signal.SignalName.type     = zio.http.datastar.signal.SignalName

  type Signal[A]       = zio.http.datastar.signal.Signal[A]
  type SignalUpdate[A] = zio.http.datastar.signal.SignalUpdate[A]
  type SignalName      = zio.http.datastar.signal.SignalName

  val datastarCodec =
    HttpCodec.contentStream[ServerSentEvent[String]] ++
      HttpCodec
        .header(Header.ContentType)
        .const(Header.ContentType(MediaType.text.`event-stream`)) ++
      HttpCodec.header(Header.CacheControl).const(Header.CacheControl.NoCache) ++
      HttpCodec.header(Header.Connection).const(Header.Connection.KeepAlive)

  val datastarEventCodec =
    HttpCodec.content[Dom] ++
      HttpCodec
        .header(Header.ContentType)
        .const(Header.ContentType(MediaType.text.`html`)) ++
      HttpCodec.header(Header.CacheControl).const(Header.CacheControl.NoCache) ++
      HttpCodec.headerAs[CssSelector]("datastar-selector").optional ++
      HttpCodec.headerAs[ElementPatchMode]("datastar-mode").optional ++
      HttpCodec.headerAs[Boolean]("datastar-use-view-transition").optional

  implicit class EndpointExtensions(endpoint: Endpoint.type) {
    def datastarEvents[Input](
      route: RoutePattern[Input],
    ): Endpoint[Input, Input, ZNothing, ZStream[Any, Nothing, DatastarEvent], AuthType.None.type] =
      Endpoint(
        route,
        route.toHttpCodec,
        datastarCodec.transformOrFailLeft(_ => Left("Not implemented"))(_.map(_.toServerSentEvent)),
        HttpCodec.unused,
        HttpContentCodec.responseErrorCodec,
        Doc.empty,
        AuthType.None,
      )

    def datastarEvent[Input](
      route: RoutePattern[Input],
    ): Endpoint[Input, Input, ZNothing, DatastarEvent.PatchElements, AuthType.None.type] =
      Endpoint(
        route,
        route.toHttpCodec,
        datastarEventCodec.transformOrFailLeft[DatastarEvent.PatchElements](_ => Left("Not implemented"))(event =>
          (
            event.elements,
            event.selector,
            if (event.mode == ElementPatchMode.Outer) None else Some(event.mode),
            if (event.useViewTransition) Some(true) else None,
          ),
        ),
        HttpCodec.unused,
        HttpContentCodec.responseErrorCodec,
        Doc.empty,
        AuthType.None,
      )

    def datastar[Input](
      route: RoutePattern[Input],
    ): Endpoint[Input, Input, ZNothing, ZStream[Any, Nothing, ServerSentEvent[String]], AuthType.None.type] =
      Endpoint(
        route,
        route.toHttpCodec,
        datastarCodec,
        HttpCodec.unused,
        HttpContentCodec.responseErrorCodec,
        Doc.empty,
        AuthType.None,
      )
  }

  implicit class EndpointRequestExtensions[PathInput, Input, Err, Output, Auth <: AuthType](
    endpoint: Endpoint[PathInput, Input, Err, Output, Auth],
  ) {
    /**
     * Creates a builder for Datastar request expressions from this endpoint.
     */
    def toDatastarRequest: zio.http.datastar.EndpointRequest.EndpointRequestBuilder[PathInput, Input, Err, Output, Auth] =
      zio.http.datastar.EndpointRequest.fromEndpoint(endpoint)
  }

  implicit def signalUpdateToModifier[A](signalUpdate: SignalUpdate[A]): Modifier =
    dataSignals(signalUpdate.signal)(signalUpdate.signal.schema) := signalUpdate.toExpression

  /**
   * Create a streaming SSE response, wiring a Datastar instance into the
   * handler environment so the handler can enqueue server-sent events.
   */
  def events[R, R1, In](
    h: Handler[R, Nothing, In, Unit],
  )(implicit ev: R <:< R1 with Datastar): Handler[R1, Nothing, In, Response] =
    Handler.scoped[R1] {
      handler { (in: In) =>
        for {
          datastar <- Datastar.make
          queue    = datastar.queue
          response =
            Response
              .fromServerSentEvents(ZStream.fromQueue(queue).takeWhile(_ ne Datastar.done))
              .addHeaders(headers)
          _ <- (h(in).provideSomeEnvironment[R1 with Scope](
            _.add[Datastar](datastar).asInstanceOf[ZEnvironment[R with Scope]],
          ) *> queue.offer(Datastar.done)).forkScoped
        } yield response
      }
    }

  @deprecated("Use events instead", "3.6.0")
  def eventStream[R, R1](
    e: ZIO[R, Nothing, Unit],
  )(implicit ev: R <:< R1 with Datastar): ZIO[R1 with Scope, Nothing, ZStream[Any, Nothing, ServerSentEvent[String]]] =
    events[R, R1](e)

  def events[R, R1](
    e: ZIO[R, Nothing, Unit],
  )(implicit ev: R <:< R1 with Datastar): ZIO[R1 with Scope, Nothing, ZStream[Any, Nothing, ServerSentEvent[String]]] =
    for {
      datastar <- Datastar.make
      queue = datastar.queue
      _ <- (e.provideSomeEnvironment[R1 with Scope](
        _.add[Datastar](datastar).asInstanceOf[ZEnvironment[R with Scope]],
      ) *> queue.offer(Datastar.done)).forkScoped
    } yield ZStream.fromQueue(queue).takeWhile(_ ne Datastar.done)

  def events[R](
    e: ZStream[R, Nothing, DatastarEvent],
  ): ZIO[R, Nothing, ZStream[Any, Nothing, ServerSentEvent[String]]] =
    ZIO.environmentWith[R](e.map(_.toServerSentEvent).provideEnvironment(_))

  def events[R, R1, In](
    h: Handler[R, Nothing, In, ZStream[R1, Nothing, DatastarEvent]],
  ): Handler[R with R1, Nothing, In, Response] =
    Handler.scoped[R with R1] {
      handler { (in: In) =>
        for {
          r      <- ZIO.environment[Scope & R]
          r1     <- ZIO.environment[R1]
          stream <- h(in).provideEnvironment(r)
          sseStream: ZStream[Any, Nothing, ServerSentEvent[String]] =
            stream.map(_.toServerSentEvent).provideEnvironment(r1)
        } yield Response
          .fromServerSentEvents(sseStream)
          .addHeaders(headers)
      }
    }
}
