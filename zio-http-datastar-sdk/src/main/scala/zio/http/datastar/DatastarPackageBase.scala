package zio.http.datastar

import scala.language.implicitConversions

import zio._

import zio.stream.ZStream

import zio.http._
import zio.http.codec.HttpCodec
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

  def eventStream[R, R1, In](
    e: ZIO[R, Nothing, Unit],
  )(implicit ev: R <:< R1 with Datastar): ZIO[R1 with Scope, Nothing, ZStream[Any, Nothing, ServerSentEvent[String]]] =
    for {
      datastar <- Datastar.make
      queue = datastar.queue
      _ <- (e.provideSomeEnvironment[R1 with Scope](
        _.add[Datastar](datastar).asInstanceOf[ZEnvironment[R with Scope]],
      ) *> queue.offer(Datastar.done)).forkScoped
    } yield ZStream.fromQueue(queue).takeWhile(_ ne Datastar.done)
}
