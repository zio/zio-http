package zio.http.datastar

import zio.{Scope, ZEnvironment}

import zio.stream.ZStream

import zio.http.{Handler, Header, Headers, Response, handler}

trait DatastarPackageBase extends Attributes {
  private val headers = Headers(
    Header.CacheControl.NoCache,
    Header.Connection.KeepAlive,
  )

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
}
