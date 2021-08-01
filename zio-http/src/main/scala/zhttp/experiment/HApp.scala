package zhttp.experiment

import io.netty.buffer.Unpooled
import io.netty.channel.{ChannelHandler, ChannelHandlerContext, ChannelInboundHandlerAdapter}
import io.netty.handler.codec.http._
import zhttp.core.Util
import zhttp.experiment.Check.AutoCheck
import zhttp.experiment.HttpMessage.{AnyRequest, HResponse}
import zhttp.http._
import zhttp.service.UnsafeChannelExecutor
import zio.stream.ZStream
import zio.{Chunk, UIO, ZIO, ZQueue}

sealed trait HApp[-R, +E] { self =>
  def combine[R1 <: R, E1 >: E](other: HApp[R1, E1]): HApp[R1, E1] = HApp.Combine(self, other)
  def +++[R1 <: R, E1 >: E](other: HApp[R1, E1]): HApp[R1, E1]     = self combine other

  private[zhttp] def compile[R1 <: R](zExec: UnsafeChannelExecutor[R1])(implicit ev: E <:< Throwable): ChannelHandler =
    new ChannelInboundHandlerAdapter { adapter =>
      private var data: Chunk[Byte]                                                          = Chunk.empty
      private var completeHttp: Http[R, Throwable, CompleteRequest, HResponse[R, Throwable]] = Http.empty
      private var anyRequest: AnyRequest                                                     = _
      private var isComplete: Boolean                                                        = false
      override def channelRead(jCtx: ChannelHandlerContext, msg: Any): Unit = {
        def writeResponse(res: HResponse[R, Throwable]): ZIO[R, Option[Throwable], Unit] = {
          res.content match {
            case HContent.Empty             => UIO(jCtx.flush(): Unit)
            case HContent.Complete(data)    => UIO(jCtx.writeAndFlush(data): Unit)
            case HContent.Streaming(stream) =>
              stream.foreachChunk(bytes => UIO(jCtx.writeAndFlush(Unpooled.copiedBuffer(bytes.toArray)): Unit))
            case HContent.FromChannel(ch)   =>
              UIO(jCtx.pipeline().replace(adapter, "zhttp.streaming", ch.compile): Unit)
          }
        }

        zExec.unsafeExecute_(jCtx) {

          (msg match {
            case msg: HttpRequest =>
              val req = AnyRequest.from(msg)
              adapter.anyRequest = req
              def find(app: HApp[R, Throwable]): ZIO[R, Option[Throwable], Any] = {
                app match {
                  case HApp.Empty => ZIO.fail(None)

                  case HApp.Combine(self, other) => find(self) *> find(other)

                  case HApp.Complete(check, http) =>
                    if (adapter.isComplete) {
                      println("Completed")
                      http.executeAsZIO(anyRequest.toCompleteRequest(data))
                    } else if (check.is(req)) UIO(adapter.completeHttp = http)
                    else ZIO.unit

                  case HApp.Partial(check, http) =>
                    if (check.is(req)) for {
                      res <- http.executeAsZIO(req)
                      _   <- UIO(jCtx.writeAndFlush(res.asJava))
                      _   <- writeResponse(res)
                    } yield ()
                    else ZIO.unit
                }
              }

              find(self.asInstanceOf[HApp[R, Throwable]])

            case msg: LastHttpContent =>
              for {
                _   <- UIO {
                  adapter.data = data ++ Chunk.fromArray(msg.content().array())
                  adapter.isComplete = true
                }
                res <- completeHttp.executeAsZIO(anyRequest.toCompleteRequest(data))
                _   <- writeResponse(res)
              } yield ()
            case msg: HttpContent     =>
              UIO {
                data = data ++ Chunk.fromArray(msg.content().array())
              }
            case msg                  => UIO(jCtx.fireChannelRead(msg))
          }).catchAll({
            case Some(cause) =>
              UIO {
                val status  = HttpResponseStatus.INTERNAL_SERVER_ERROR
                val version = HttpVersion.HTTP_1_1
                val content = Unpooled.copiedBuffer(Util.prettyPrint(cause).getBytes())
                val res     = new DefaultFullHttpResponse(version, status, content)

                jCtx.writeAndFlush(res)
              }
            case None        =>
              UIO {
                UIO {
                  val status  = HttpResponseStatus.NOT_FOUND
                  val version = HttpVersion.HTTP_1_1
                  val content = Unpooled.copiedBuffer(s"${anyRequest.url.path.toString}".getBytes())
                  val res     = new DefaultFullHttpResponse(version, status, content)

                  jCtx.writeAndFlush(res)
                }
              }
          })
        }
      }
    }
}

object HApp {
  case object Empty                                                                       extends HApp[Any, Nothing]
  case class Combine[R, E](self: HApp[R, E], other: HApp[R, E])                           extends HApp[R, E]
  case class Complete[R, E](check: Check[AnyRequest], http: RHttp[R, E, CompleteRequest]) extends HApp[R, E]
  case class Partial[R, E](check: Check[AnyRequest], http: RHttp[R, E, AnyRequest])       extends HApp[R, E]

  // App Generators
  sealed trait HAppGen[+A] {
    def make[R, E](check: Check[AnyRequest], http: RHttp[R, E, A]): HApp[R, E]
  }

  implicit object CompleteRequestHAppGen extends HAppGen[CompleteRequest] {
    override def make[R, E](check: Check[AnyRequest], http: RHttp[R, E, CompleteRequest]): HApp[R, E] =
      HApp.complete(check, http)
  }

  implicit object PartialRequestHAppGen extends HAppGen[AnyRequest] {
    override def make[R, E](check: Check[AnyRequest], http: RHttp[R, E, AnyRequest]): HApp[R, E] =
      HApp.partial(check, http)
  }

  implicit object BufferedRequestHAppGen extends HAppGen[BufferedRequest] {
    override def make[R, E](check: Check[AnyRequest], http: RHttp[R, E, BufferedRequest]): HApp[R, E] =
      HApp.streaming(check, http)
  }

  // Constructor
  def apply[R, E, A, C](c: Check[AnyRequest], http: RHttp[R, E, A])(implicit hl: HAppGen[A]): HApp[R, E] =
    hl.make(c, http)

  def apply[R, E, A, C](c: C)(http: RHttp[R, E, A])(implicit ch: AutoCheck[C, AnyRequest], hl: HAppGen[A]): HApp[R, E] =
    HApp(ch.toCheck(c), http)

  def apply[R, E, A](http: RHttp[R, E, A])(implicit hl: HAppGen[A]): HApp[R, E] =
    HApp(Check.isTrue, http)

  def empty: HApp[Any, Nothing] = HApp.Empty

  // Helper
  private[zhttp] def partial[E, R](check: Check[AnyRequest], http: RHttp[R, E, AnyRequest]): HApp[R, E] =
    Partial(check, http)

  private[zhttp] def streaming[E, R](check: Check[AnyRequest], http: RHttp[R, E, BufferedRequest]): HApp[R, E] =
    HApp.partial(
      check,
      http.contramapM { req: AnyRequest =>
        ZQueue
          .bounded[Chunk[Byte]](1)
          .map(q => req.toBufferedRequest(ZStream.fromChunkQueueWithShutdown(q)))
      },
    )

  private def complete[E, R](check: Check[AnyRequest], http: RHttp[R, E, CompleteRequest]) = {
    HApp.Complete(check, http)
  }

}
