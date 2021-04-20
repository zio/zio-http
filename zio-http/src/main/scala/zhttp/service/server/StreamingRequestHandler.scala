package zhttp.service.server

import io.netty.buffer.{Unpooled => JUnpooled}
import io.netty.handler.codec.http.websocketx.{WebSocketServerProtocolHandler => JWebSocketServerProtocolHandler}
import io.netty.handler.codec.http.{HttpContent => JHttpContent, LastHttpContent => JLastHttpContent, _}
import io.netty.util.AttributeKey
import zhttp.core.{JChannelHandlerContext, JSharable, JSimpleChannelInboundHandler}
import zhttp.http._
import zhttp.service.{ChannelFuture, HttpMessageCodec, UnsafeChannelExecutor, WEB_SOCKET_HANDLER}
import zio.stream.ZStream
import zio.{Chunk, Exit, Queue}

@JSharable
final case class StreamingRequestHandler[R](
  zExec: UnsafeChannelExecutor[R],
  app: RHttp[R],
) extends JSimpleChannelInboundHandler[HttpObject](true)
    with HttpMessageCodec
    with ServerHttpExceptionHandler {

  self =>

  // State information
  // Do we still want to handle messages or ignore them?
  private val handleMessages: AttributeKey[Boolean]   = AttributeKey.valueOf("ignoreMessages")
  // How many content messages did we handle?
  private val httpContentHandled: AttributeKey[Int]   = AttributeKey.valueOf("messagesEnqueued")
  // Interface between this Netty handler and the ZIO stream
  private val queue: AttributeKey[Queue[Chunk[Byte]]] = AttributeKey.valueOf("queue")

  // Static information extracted from the first HttpRequest message
  private val storedHeaders: AttributeKey[List[Header]]                 = AttributeKey.valueOf("headers")
  private val storedEndpoint: AttributeKey[Either[HttpError, Endpoint]] = AttributeKey.valueOf("endpoint")
  private val storedProtocolVersion: AttributeKey[HttpVersion]          = AttributeKey.valueOf("protocolVersion")

  private def buildRequest(ctx: JChannelHandlerContext, content: HttpData[Any, Nothing]): Either[HttpError, Request] =
    for {
      endpoint <- ctx.channel().attr(storedEndpoint).get()
      headers = ctx.channel().attr(storedHeaders).get()
    } yield Request(endpoint, headers, content)

  // TODO: nearly the samve as the one in `ServerRequestHandler`. Generalize and lift?
  private def executeAsync(ctx: JChannelHandlerContext, jReq: Either[HttpError, Request])(
    cb: Response[R, Throwable] => Unit,
  ): Unit = {
    jReq match {
      case Left(err)  => cb(err.toResponse)
      case Right(req) =>
        app.eval(req) match {
          case HttpResult.Success(a)  => cb(a)
          case HttpResult.Failure(e)  => cb(SilentResponse[Throwable].silent(e))
          case HttpResult.Continue(z) =>
            zExec.unsafeExecute(ctx, z) {
              case Exit.Success(res)   => cb(res)
              case Exit.Failure(cause) =>
                cause.failureOption match {
                  case Some(e) => cb(SilentResponse[Throwable].silent(e))
                  case None    => ()
                }
            }
        }
    }
  }

  private def writeResponse(ctx: JChannelHandlerContext, message: JHttpContent)(
    res: Response[R, Throwable],
  ): Unit = res match {
    case res @ Response.HttpResponse(_, _, content) =>
      message.release()
      // Ignore all next messages passing by. This makes it possible to skip consuming the entire request
      // and still send out a response without blocking.
      ctx.channel().attr(handleMessages).set(false)
      ctx.write(encodeResponse(ctx.channel().attr(storedProtocolVersion).get, res), ctx.channel().voidPromise())
      content match {
        case HttpData.StreamData(data)   =>
          zExec.unsafeExecute_(ctx) {
            for {
              _ <- data.foreachChunk(c => ChannelFuture.unit(ctx.writeAndFlush(JUnpooled.copiedBuffer(c.toArray))))
              _ <- ChannelFuture.unit(ctx.writeAndFlush(JLastHttpContent.EMPTY_LAST_CONTENT))
            } yield ()
          }
        case HttpData.CompleteData(data) =>
          ctx.write(JUnpooled.copiedBuffer(data.toArray), ctx.channel().voidPromise())
          ctx.writeAndFlush(JLastHttpContent.EMPTY_LAST_CONTENT)
        case HttpData.Empty              =>
          ctx.writeAndFlush(JLastHttpContent.EMPTY_LAST_CONTENT)
      }
      ()
    case res @ Response.SocketResponse(_)           =>
      ctx
        .channel()
        .pipeline()
        .addLast(new JWebSocketServerProtocolHandler(res.socket.settings.protocolConfig))
        .addLast(WEB_SOCKET_HANDLER, ServerSocketHandler(zExec, res.socket.settings))
      ctx.fireChannelRead(message)
      ()
  }

  private def enqueueContent(ctx: JChannelHandlerContext, httpContent: JHttpContent, isLastElement: Boolean) = {
    val q = ctx.channel().attr(queue).get()
    if (isLastElement) {
      if (httpContent.content().isReadable) {
        // Chunk.fromByteBuffer instead of Chunk.fromArray uses way less memory
        val offer = q.offerAll(Seq(Chunk.fromByteBuffer(httpContent.content().nioBuffer()), Chunk.empty))
        zExec.runtime.unsafeRunSync(offer *> q.shutdown)
      } else {
        zExec.runtime.unsafeRunSync(q.offer(Chunk.empty) *> q.shutdown)
      }
    } else {
      if (httpContent.content().isReadable) {
        // Chunk.fromByteBuffer instead of Chunk.fromArray uses way less memory
        val defaultOffer = q.offer(Chunk.fromByteBuffer(httpContent.content().nioBuffer()))
        zExec.runtime.unsafeRunSync(defaultOffer)
      }
    }
  }

  override def channelRead0(ctx: JChannelHandlerContext, httpObject: HttpObject): Unit = httpObject match {
    case httpRequest: HttpRequest =>
      // Toggling AUTO_READ will block reading next messages from the socket
      ctx.channel().config().setAutoRead(false)
      // Initialize state
      ctx.channel().attr(handleMessages).set(true)
      ctx.channel().attr(httpContentHandled).set(0)

      // Extract request metadata and store for later access
      val parsedUrl = URL.fromString(httpRequest.uri())
      val method    = Method.fromJHttpMethod(httpRequest.method())
      ctx.channel().attr(storedProtocolVersion).set(httpRequest.protocolVersion())
      ctx.channel().attr(storedHeaders).set(Header.make(httpRequest.headers()))
      ctx.channel().attr(storedEndpoint).set(parsedUrl.map(url => method -> url))
      ctx.channel().config().setAutoRead(true)
      ()

    case lastHttpContent: JLastHttpContent if ctx.channel().attr(handleMessages).get() =>
      // NOTE: we don't support trailing headers in this setup.
      // Toggling AUTO_READ will block reading next messages from the socket
      ctx.channel().config().setAutoRead(false)

      if (ctx.channel().attr(httpContentHandled).get() > 0) {
        // Actual request content has been pushed into the stream already so we have to do this as well here
        enqueueContent(ctx, lastHttpContent, isLastElement = true)
      } else {
        // No request content has been pushed into the stream so we can take a shortcut and
        // create the ZStream from one Chunk instead of a Queue for better performance.

        // Chunk.fromByteBuffer instead of Chunk.fromArray uses way less memory
        val byteStream = ZStream.fromChunk(Chunk.fromByteBuffer(lastHttpContent.content().nioBuffer()))
        val request    = buildRequest(ctx, HttpData.StreamData(byteStream))
        // Retain this message since it will only be released in writeResponse
        lastHttpContent.retain()
        executeAsync(ctx, request)(writeResponse(ctx, lastHttpContent))
      }
      ctx.channel().config().setAutoRead(true)
      ()

    case httpContent: JHttpContent if ctx.channel().attr(handleMessages).get() =>
      // Toggling AUTO_READ will block reading next messages from the socket
      ctx.channel().config().setAutoRead(false)
      val contentHandledCounter = ctx.channel().attr(httpContentHandled)
      if (contentHandledCounter.get() == 0) {
        // Actual request content is arriving. We have to create a queue now to start
        // offering data to the ZIO stream.
        val q       = zExec.runtime.unsafeRun(Queue.bounded[Chunk[Byte]](1))
        ctx.channel().attr(queue).set(q)
        val request = buildRequest(ctx, HttpData.StreamData(ZStream.fromChunkQueue(q)))
        // Retain this message since it will only be released in writeResponse
        httpContent.retain()
        executeAsync(ctx, request)(writeResponse(ctx, httpContent))
      }

      enqueueContent(ctx, httpContent, isLastElement = false)
      contentHandledCounter.set(contentHandledCounter.get() + 1)
      ctx.channel().config().setAutoRead(true)
      ()

    case _ =>
      // We don't process this message
      ()
  }

  override def exceptionCaught(ctx: JChannelHandlerContext, cause: Throwable): Unit = {
    if (self.canThrowException(cause)) {
      super.exceptionCaught(ctx, cause)
    }
  }

}
