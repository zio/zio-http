package zhttp.service.server

import io.netty.buffer.{Unpooled => JUnpooled}
import io.netty.handler.codec.http.multipart._
import io.netty.handler.codec.http.{HttpContent => JHttpContent, LastHttpContent => JLastHttpContent, _}
import io.netty.util.AttributeKey
import zhttp.core.{JChannelHandlerContext, JSharable, JSimpleChannelInboundHandler}
import zhttp.http.{HttpData, _}
import zhttp.service.Server.Settings
import zhttp.service.{ChannelFuture, HttpMessageCodec, UnsafeChannelExecutor}
import zio.Exit

@JSharable
final case class ServerMultipartRequestHandler[R](
  zExec: UnsafeChannelExecutor[R],
  settings: Settings[R, Throwable],
) extends JSimpleChannelInboundHandler[HttpObject](true)
    with HttpMessageCodec {

  self =>

  private val factory                                          = new DefaultHttpDataFactory(DefaultHttpDataFactory.MINSIZE)
  private val decoderKey: AttributeKey[HttpPostRequestDecoder] = AttributeKey.valueOf("decoder")
  private val uriKey: AttributeKey[String]                     = AttributeKey.valueOf("uri")
  private val headersKey: AttributeKey[HttpHeaders]            = AttributeKey.valueOf("headers")
  private val protocolVersionKey: AttributeKey[HttpVersion]    = AttributeKey.valueOf("protocolVersion")

  // TODO: nearly the samve as the one in `ServerRequestHandler`. Generalize and lift?
  private def executeAsync(ctx: JChannelHandlerContext, jReq: Either[Throwable, Request])(
    cb: Response[R, Throwable] => Unit,
  ): Unit =
    jReq match {
      case Left(err)  => cb(HttpError.InternalServerError("Request decoding failure", Option(err)).toResponse)
      case Right(req) =>
        settings.http.execute(req).evaluate match {
          case HttpResult.Empty      => cb(Response.fromHttpError(HttpError.NotFound(req.url.path)))
          case HttpResult.Success(a) => cb(a)
          case HttpResult.Failure(e) => cb(SilentResponse[Throwable].silent(e))
          case HttpResult.Effect(z)  =>
            zExec.unsafeExecute(ctx, z) {
              case Exit.Success(res)   => cb(res)
              case Exit.Failure(cause) =>
                cause.failureOption match {
                  case Some(Some(e)) => cb(SilentResponse[Throwable].silent(e))
                  case Some(None)    => cb(Response.fromHttpError(HttpError.NotFound(req.url.path)))
                  case None          => {
                    ctx.close()
                    ()
                  }
                }
            }
        }
    }

  private def removeHandler(ctx: JChannelHandlerContext, httpObject: HttpObject): Unit = {
    ctx.channel().pipeline().remove(this)
    ctx.fireChannelRead(httpObject)
    ()
  }

  private def cleanupDecoder(ctx: JChannelHandlerContext): Unit = {
    val decoder = ctx.channel().attr(decoderKey).get()
    try {
      decoder.cleanFiles()
      decoder.destroy()
    } catch {
      case _: Throwable =>
    }
  }

  override def channelRead0(ctx: JChannelHandlerContext, httpObject: HttpObject): Unit = httpObject match {
    case httpRequest: HttpRequest if httpRequest.method() != HttpMethod.POST =>
      // Stop handling this message and ignore all
      // following httpObjects for this request
      removeHandler(ctx, httpObject)
      ()
    case httpRequest: HttpRequest if httpRequest.method() == HttpMethod.POST =>
      val httpPostDecoder = new HttpPostRequestDecoder(factory, httpRequest)
      ctx.channel().attr(decoderKey).set(httpPostDecoder)

      if (!httpPostDecoder.isMultipart) {
        // Stop handling this message and ignore all
        // following httpObjects for this request
        removeHandler(ctx, httpObject)
        ()
      } else {
        ctx.channel().attr(uriKey).set(httpRequest.uri())
        ctx.channel().attr(headersKey).set(httpRequest.headers())
        ctx.channel().attr(protocolVersionKey).set(httpRequest.protocolVersion())

        if (HttpUtil.is100ContinueExpected(httpRequest)) {
          ctx
            .channel()
            .writeAndFlush(
              new DefaultFullHttpResponse(httpRequest.protocolVersion(), HttpResponseStatus.CONTINUE),
              ctx.channel().voidPromise(),
            )
        }
        ()
      }

    case httpContent: JLastHttpContent =>
      val decoder = ctx.channel().attr(decoderKey).get()
      decoder.offer(httpContent)
      ctx.channel().attr(headersKey).get().add(httpContent.trailingHeaders())
      val req     = decodeMultipart(
        ctx.channel().attr(uriKey).get(),
        ctx.channel().attr(headersKey).get(),
        decoder,
      )

      executeAsync(ctx, req) {
        case res @ Response.HttpResponse(_, _, _) =>
          // TODO: share this logic with ServerRequestHandler
          ctx.write(encodeResponse(ctx.channel().attr(protocolVersionKey).get(), res), ctx.channel().voidPromise())
          res.content match {
            case HttpData.StreamData(data)        =>
              zExec.unsafeExecute_(ctx) {
                for {
                  _ <- data.foreachChunk(c => ChannelFuture.unit(ctx.writeAndFlush(JUnpooled.copiedBuffer(c.toArray))))
                  _ <- ChannelFuture.unit(ctx.writeAndFlush(JLastHttpContent.EMPTY_LAST_CONTENT))
                } yield ()
              }
            case HttpData.CompleteData(data)      =>
              ctx.write(JUnpooled.copiedBuffer(data.toArray), ctx.channel().voidPromise())
              ctx.writeAndFlush(JLastHttpContent.EMPTY_LAST_CONTENT)
            case HttpData.MultipartFormData(_, _) => ctx.writeAndFlush(JLastHttpContent.EMPTY_LAST_CONTENT)
            case HttpData.Empty                   => ctx.writeAndFlush(JLastHttpContent.EMPTY_LAST_CONTENT)
          }

          cleanupDecoder(ctx)
          ()
        case _                                    =>
          // We don't deal with Websockets here
          ()
      }

    case httpContent: JHttpContent =>
      ctx.channel().attr(decoderKey).get().offer(httpContent)
      ()

    case _ =>
      ()
  }

  override def channelInactive(ctx: JChannelHandlerContext): Unit = {
    cleanupDecoder(ctx)
  }

  override def handlerRemoved(ctx: JChannelHandlerContext): Unit = {
    cleanupDecoder(ctx)
  }

  /**
   * Handles exceptions that throws
   */
  override def exceptionCaught(ctx: JChannelHandlerContext, cause: Throwable): Unit = {
    settings.error match {
      case Some(v) => zExec.unsafeExecute_(ctx)(v(cause).uninterruptible)
      case None    => {
        ctx.fireExceptionCaught(cause)
        ()
      }
    }
  }
}
