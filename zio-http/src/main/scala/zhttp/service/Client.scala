package zhttp.service

import io.netty.bootstrap.Bootstrap
import io.netty.buffer.{ByteBuf, ByteBufUtil}
import io.netty.channel.{Channel, ChannelFactory => JChannelFactory, ChannelHandlerContext, ChannelInitializer, EventLoopGroup => JEventLoopGroup}
import io.netty.handler.codec.http.websocketx.WebSocketClientProtocolHandler
import io.netty.handler.codec.http.{FullHttpRequest, HttpClientCodec, HttpObjectAggregator, HttpVersion}
import zhttp.http._
import zhttp.http.headers.HeaderExtension
import zhttp.service
import zhttp.service.Client.{ClientRequest, ClientResponse}
import zhttp.service.client.ClientSSLHandler.ClientSSLOptions
import zhttp.service.client.content.handlers.ClientResponseHandler
import zhttp.service.client.{ClientInboundHandler, ClientSSLHandler}
import zhttp.socket.{Socket, SocketApp}
import zio.{Chunk, Promise, Task, ZIO}

import java.net.{InetAddress, InetSocketAddress, URI}

final case class Client[R](rtm: HttpRuntime[R], cf: JChannelFactory[Channel], el: JEventLoopGroup)
    extends HttpMessageCodec {

  def request(
    request: Client.ClientRequest,
    sslOption: ClientSSLOptions = ClientSSLOptions.DefaultSSL,
  ): Task[Client.ClientResponse] =
    for {
      promise <- Promise.make[Throwable, Client.ClientResponse]
      jReq    <- encodeClientParams(HttpVersion.HTTP_1_1, request)
      _       <- Task(unsafeRequest(request, jReq, promise, sslOption)).catchAll(cause => promise.fail(cause))
      res     <- promise.await
    } yield res

  def socket(
    url: String,
    headers: Headers = Headers.empty,
    socketApp: SocketApp[R],
    sslOptions: ClientSSLOptions = ClientSSLOptions.DefaultSSL,
  ): ZIO[R, Throwable, ClientResponse] = for {
    env <- ZIO.environment[R]
    res <- request(
      ClientRequest(url, Method.GET, headers, attribute = Client.Attribute(socketApp = Some(socketApp.provide(env)))),
      sslOptions,
    )
  } yield res

  private def unsafeRequest(
    request: ClientRequest,
    jReq: FullHttpRequest,
    promise: Promise[Throwable, ClientResponse],
    sslOption: ClientSSLOptions,
  ): Unit = {

    // TODO: Remove `println`
    println("JRequest: " + jReq.uri())
    println("Request: " + request.url)
    try {
      val url  = new URI(jReq.uri())
      val host = if (url.getHost == null) jReq.headers().get(HeaderNames.host) else url.getHost

      assert(host != null, "Host name is required")
      println("Host: " + host)

      val port   = if (url.getPort < 0) 80 else url.getPort
      println("Port: " + port)
      val scheme = url.getScheme

      val isWebSocket = scheme == "ws" || scheme == "wss"
      val isSSL       = scheme == "https" || scheme == "wss"

      println("Connecting to " + host + ":" + port)
      val initializer = new ChannelInitializer[Channel]() {
        override def initChannel(ch: Channel): Unit = {
          println("Initializing channel")

          val pipeline = ch.pipeline()

          // If a https or wss request is made we need to add the ssl handler at the starting of the pipeline.
          if (isSSL) pipeline.addLast("SSLHandler", ClientSSLHandler.ssl(sslOption).newHandler(ch.alloc))

          // Adding default client channel handlers
          pipeline.addLast(HTTP_CLIENT_CODEC, new HttpClientCodec)

          // ObjectAggregator is used to work with FullHttpRequests and FullHttpResponses
          // This is also required to make WebSocketHandlers work
          pipeline.addLast(HTTP_OBJECT_AGGREGATOR, new HttpObjectAggregator(Int.MaxValue))

          // Response handler is added to convert Netty responses to our own responses
          pipeline.addLast("ZHttpClientResponseHandler", new ClientResponseHandler())

          // Add WebSocketHandlers if it's a `ws` or `wss` request
          if (isWebSocket) {
            println("Adding WebSocketHandler")
            val headers = request.getHeaders.encode
            val app     = request.attribute.socketApp.getOrElse(Socket.empty.toSocketApp)
            val config  = app.protocol.clientBuilder.customHeaders(headers).build()

            // Handles the heavy lifting required to
            pipeline.addLast("ZHttpWebSocketClientProtocolHandler", new WebSocketClientProtocolHandler(config))
            pipeline.addLast("ZHttpWebSocketAppHandler", new WebSocketAppHandler(rtm, app))
          } else {
            pipeline.addLast("ZHttpClientInboundHandler", new ClientInboundHandler(rtm, jReq, promise))
          }

          ()
        }
      }

      val jBoo = new Bootstrap().channelFactory(cf).group(el).handler(initializer)

      jBoo.remoteAddress(new InetSocketAddress(host, port))

      // TODO: handle connection failures
      jBoo.connect(): Unit
    } catch {
      case err: Throwable =>
        if (jReq.refCnt() > 0) {
          jReq.release(jReq.refCnt()): Unit
        }
        throw err
    }
  }
}

object Client {
  def make[R]: ZIO[R with EventLoopGroup with ChannelFactory, Nothing, Client[R]] = for {
    cf <- ZIO.access[ChannelFactory](_.get)
    el <- ZIO.access[EventLoopGroup](_.get)
    zx <- HttpRuntime.default[R]
  } yield service.Client(zx, cf, el)

  def request(
    url: String,
    method: Method = Method.GET,
    headers: Headers = Headers.empty,
    content: HttpData = HttpData.empty,
    ssl: ClientSSLOptions = ClientSSLOptions.DefaultSSL,
  ): ZIO[EventLoopGroup with ChannelFactory, Throwable, ClientResponse] =
    make[Any].flatMap(_.request(ClientRequest(url, method, headers, content), ssl))

  def socket[R](
    url: String,
    app: SocketApp[R],
    headers: Headers = Headers.empty,
    sslOptions: ClientSSLOptions = ClientSSLOptions.DefaultSSL,
  ): ZIO[R with EventLoopGroup with ChannelFactory, Throwable, ClientResponse] =
    make[R].flatMap(_.socket(url, headers, app, sslOptions))

  final case class ClientRequest(
    url: String,
    method: Method = Method.GET,
    getHeaders: Headers = Headers.empty,
    data: HttpData = HttpData.empty,
    private[zhttp] val attribute: Attribute = Attribute.empty,
    private val channelContext: ChannelHandlerContext = null,
  ) extends HeaderExtension[ClientRequest] {
    self =>

    def getBodyAsString: Task[String] = getBodyAsByteBuf.map(_.toString(getHeaders.getCharset))

    def remoteAddress: Option[InetAddress] = {
      if (channelContext != null && channelContext.channel().remoteAddress().isInstanceOf[InetSocketAddress])
        Some(channelContext.channel().remoteAddress().asInstanceOf[InetSocketAddress].getAddress)
      else
        None
    }

    /**
     * Updates the headers using the provided function
     */
    override def updateHeaders(update: Headers => Headers): ClientRequest =
      self.copy(getHeaders = update(self.getHeaders))

    private[zhttp] def getBodyAsByteBuf: Task[ByteBuf] = data.toByteBuf
  }

  final case class ClientResponse(status: Status, headers: Headers, private[zhttp] val buffer: ByteBuf)
      extends HeaderExtension[ClientResponse] {
    self =>

    def getBody: Task[Chunk[Byte]] = Task(Chunk.fromArray(ByteBufUtil.getBytes(buffer)))

    def getBodyAsByteBuf: Task[ByteBuf] = Task(buffer)

    def getBodyAsString: Task[String] = Task(buffer.toString(self.getCharset))

    override def getHeaders: Headers = headers

    override def updateHeaders(update: Headers => Headers): ClientResponse = self.copy(headers = update(headers))
  }

  case class Attribute(socketApp: Option[SocketApp[Any]] = None) {}
  object Attribute                                               {
    def empty: Attribute = Attribute()
  }
}
