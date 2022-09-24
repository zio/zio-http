package zio.http.netty.server

import io.netty.channel.ChannelHandlerContext
import io.netty.handler.codec.http.{HttpRequest, _}
import zio.Trace
import zio.http._
import zio.http.model.Method._
import zio.http.model._
import zio.http.netty._

import java.net.{InetAddress, InetSocketAddress}

private[zio] sealed abstract class NettyServerRequest(
  val ctx: ChannelHandlerContext,
  val nettyReq: HttpRequest,
  currentHeaders: Option[Headers],
  currentUrl: Option[URL],
  currentVersion: Option[Version],
) extends Request { self: NettyServerRequest.Update =>

  override def remoteAddress: Option[InetAddress] =
    ctx.channel().remoteAddress() match {
      case address: InetSocketAddress => Some(address.getAddress)
      case _                          => None
    }

  override def headers: Headers = currentHeaders match {
    case None          => Headers.make(nettyReq.headers())
    case Some(headers) => headers
  }

  override def path: Path = url.path

  override def updateUrl(newUrl: URL): Request = withUpdatedUrl(newUrl)

  override def updateVersion(version: Version): Request = withUpdatedVersion(version)

  override def updateHeaders(update: Headers => Headers)(implicit trace: Trace): Request = withUpdatedHeaders(
    update(headers),
  )

  override def updateMethod(newMethod: Method): Request = newMethod match {
    case TRACE        => NettyServerRequest.trace(ctx, nettyReq, currentHeaders, currentUrl, currentVersion)
    case POST         => NettyServerRequest.post(ctx, nettyReq, currentHeaders, currentUrl, currentVersion)
    case HEAD         => NettyServerRequest.head(ctx, nettyReq, currentHeaders, currentUrl, currentVersion)
    case CUSTOM(name) => NettyServerRequest.custom(ctx, name, nettyReq, currentHeaders, currentUrl, currentVersion)
    case CONNECT      => NettyServerRequest.connect(ctx, nettyReq, currentHeaders, currentUrl, currentVersion)
    case DELETE       => NettyServerRequest.delete(ctx, nettyReq, currentHeaders, currentUrl, currentVersion)
    case PUT          => NettyServerRequest.put(ctx, nettyReq, currentHeaders, currentUrl, currentVersion)
    case GET          => NettyServerRequest.get(ctx, nettyReq, currentHeaders, currentUrl, currentVersion)
    case OPTIONS      => NettyServerRequest.options(ctx, nettyReq, currentHeaders, currentUrl, currentVersion)
    case PATCH        => NettyServerRequest.patch(ctx, nettyReq, currentHeaders, currentUrl, currentVersion)
  }

  override def url: URL = currentUrl match {
    case None =>
      URL.fromString(nettyReq.uri()) match {
        case Left(_)    => URL.empty
        case Right(url) => url
      }

    case Some(url) => url
  }

  override def version: Version =
    currentVersion match {
      case None          =>
        val nettyHttpVersion = nettyReq.protocolVersion()

        nettyHttpVersion match {
          case HttpVersion.HTTP_1_0 => Version.Http_1_0
          case HttpVersion.HTTP_1_1 => Version.Http_1_1
          case other                => Version.Unsupported(other.text())
        }
      case Some(version) => version
    }

}

private[zio] object NettyServerRequest {

  sealed trait Update { self: NettyServerRequest =>

    def withUpdatedUrl(setUrl: URL): NettyServerRequest

    def withUpdatedHeaders(newHeaders: Headers): NettyServerRequest

    def withUpdatedVersion(newVersion: Version): NettyServerRequest
  }

  sealed trait SupportsBody { self: NettyServerRequest =>
    import ServerInboundHandler.isReadKey

    private def addAsyncBodyHandler(async: Body.UnsafeAsync): Unit = {
      if (contentIsRead) throw new RuntimeException("Content is already read")
      ctx
        .channel()
        .pipeline()
        .addAfter(Names.HttpRequestHandler, Names.HttpContentHandler, new ServerAsyncBodyHandler(async)): Unit
      setContentReadAttr(flag = true)
    }

    private def contentIsRead: Boolean =
      ctx.channel().attr(isReadKey).get()

    private def setContentReadAttr(flag: Boolean): Unit =
      ctx.channel().attr(isReadKey).set(flag)

    override def body: Body =
      nettyReq match {
        case nettyReq: FullHttpRequest =>
          Body.fromByteBuf(nettyReq.content())
        case _: HttpRequest            =>
          Body.fromAsync { async =>
            addAsyncBodyHandler(async)
          }

      }

  }

  @inline
  def apply(ctx: ChannelHandlerContext, request: HttpRequest): NettyServerRequest =
    request.method() match {
      case HttpMethod.TRACE   => trace(ctx, request)
      case HttpMethod.POST    => post(ctx, request)
      case HttpMethod.HEAD    => head(ctx, request)
      case HttpMethod.CONNECT => connect(ctx, request)
      case HttpMethod.DELETE  => delete(ctx, request)
      case HttpMethod.PUT     => put(ctx, request)
      case HttpMethod.GET     => get(ctx, request)
      case HttpMethod.OPTIONS => options(ctx, request)
      case HttpMethod.PATCH   => patch(ctx, request)
      case method: HttpMethod => custom(ctx, method.name(), request)
    }

  def connect(
    ctx: ChannelHandlerContext,
    request: HttpRequest,
    currentHeaders: Option[Headers] = None,
    currentUrl: Option[URL] = None,
    currentVersion: Option[Version] = None,
  ): NettyServerRequest = new NettyServerRequest(ctx, request, currentHeaders, currentUrl, currentVersion) with Update {
    self =>
    override def withUpdatedVersion(newVersion: Version): NettyServerRequest =
      connect(ctx, request, currentVersion = Some(newVersion))

    override def withUpdatedUrl(setUrl: URL): NettyServerRequest = connect(ctx, request, currentUrl = Some(setUrl))

    override def withUpdatedHeaders(newHeaders: Headers): NettyServerRequest =
      connect(ctx, request, currentHeaders = Some(newHeaders))

    /**
     * Per RFC 7231, CONNECT, GET, HEAD, DELETE, OPTION, TRACE method bodies'
     * have no defined semantics, and must be ignored.
     *
     * @SEE
     *   https://www.rfc-editor.org/rfc/rfc7231#page-24
     */
    override final val body: Body = Body.empty

    override final val method: Method = CONNECT

  }

  def custom(
    ctx: ChannelHandlerContext,
    customMethod: String,
    request: HttpRequest,
    currentHeaders: Option[Headers] = None,
    currentUrl: Option[URL] = None,
    currentVersion: Option[Version] = None,
  ): NettyServerRequest = new NettyServerRequest(ctx, request, currentHeaders, currentUrl, currentVersion)
    with Update
    with SupportsBody {
    self =>
    override def withUpdatedVersion(newVersion: Version): NettyServerRequest =
      custom(ctx, customMethod, request, currentVersion = Some(newVersion))

    override def withUpdatedUrl(setUrl: URL): NettyServerRequest =
      custom(ctx, customMethod, request, currentUrl = Some(setUrl))

    override def withUpdatedHeaders(newHeaders: Headers): NettyServerRequest =
      custom(ctx, customMethod, request, currentHeaders = Some(newHeaders))

    override final val method: Method = Method.fromString(customMethod)

  }

  def delete(
    ctx: ChannelHandlerContext,
    request: HttpRequest,
    currentHeaders: Option[Headers] = None,
    currentUrl: Option[URL] = None,
    currentVersion: Option[Version] = None,
  ): NettyServerRequest = new NettyServerRequest(ctx, request, currentHeaders, currentUrl, currentVersion) with Update {
    self =>
    override def withUpdatedVersion(newVersion: Version): NettyServerRequest =
      delete(ctx, request, currentVersion = Some(newVersion))

    override def withUpdatedUrl(setUrl: URL): NettyServerRequest = delete(ctx, request, currentUrl = Some(setUrl))

    override def withUpdatedHeaders(newHeaders: Headers): NettyServerRequest =
      delete(ctx, request, currentHeaders = Some(newHeaders))

    /**
     * Per RFC 7231, CONNECT, GET, HEAD, DELETE, OPTION, TRACE method bodies'
     * have no defined semantics and must be ignored.
     *
     * @SEE
     *   https://www.rfc-editor.org/rfc/rfc7231#page-24
     */
    override final val body: Body = Body.empty

    override final val method: Method = DELETE

  }

  def get(
    ctx: ChannelHandlerContext,
    request: HttpRequest,
    currentHeaders: Option[Headers] = None,
    currentUrl: Option[URL] = None,
    currentVersion: Option[Version] = None,
  ): NettyServerRequest = new NettyServerRequest(ctx, request, currentHeaders, currentUrl, currentVersion) with Update {
    self =>
    override def withUpdatedVersion(newVersion: Version): NettyServerRequest =
      get(ctx, request, currentVersion = Some(newVersion))

    override def withUpdatedUrl(setUrl: URL): NettyServerRequest = get(ctx, request, currentUrl = Some(setUrl))

    override def withUpdatedHeaders(newHeaders: Headers): NettyServerRequest =
      get(ctx, request, currentHeaders = Some(newHeaders))

    /**
     * Per RFC 7231, CONNECT, GET, HEAD, DELETE, OPTION, TRACE method bodies'
     * have no defined semantics, and must be ignored.
     *
     * @SEE
     *   https://www.rfc-editor.org/rfc/rfc7231#page-24
     */
    override final val body: Body = Body.empty

    override final val method: Method = Method.GET

  }

  def head(
    ctx: ChannelHandlerContext,
    request: HttpRequest,
    currentHeaders: Option[Headers] = None,
    currentUrl: Option[URL] = None,
    currentVersion: Option[Version] = None,
  ): NettyServerRequest = new NettyServerRequest(ctx, request, currentHeaders, currentUrl, currentVersion) with Update {
    self =>
    override def withUpdatedVersion(newVersion: Version): NettyServerRequest =
      head(ctx, request, currentVersion = Some(newVersion))

    override def withUpdatedUrl(setUrl: URL): NettyServerRequest = head(ctx, request, currentUrl = Some(setUrl))

    override def withUpdatedHeaders(newHeaders: Headers): NettyServerRequest =
      head(ctx, request, currentHeaders = Some(newHeaders))

    /**
     * Per RFC 7231, CONNECT, GET, HEAD, DELETE, OPTION, TRACE method bodies'
     * have no defined semantics, and must be ignored.
     *
     * @SEE
     *   https://www.rfc-editor.org/rfc/rfc7231#page-24
     */
    override final val body: Body = Body.empty

    override final val method: Method = HEAD
  }

  def options(
    ctx: ChannelHandlerContext,
    request: HttpRequest,
    currentHeaders: Option[Headers] = None,
    currentUrl: Option[URL] = None,
    currentVersion: Option[Version] = None,
  ): NettyServerRequest = new NettyServerRequest(ctx, request, currentHeaders, currentUrl, currentVersion) with Update {
    self =>
    override def withUpdatedVersion(newVersion: Version): NettyServerRequest =
      options(ctx, request, currentVersion = Some(newVersion))

    override def withUpdatedUrl(setUrl: URL): NettyServerRequest = options(ctx, request, currentUrl = Some(setUrl))

    override def withUpdatedHeaders(newHeaders: Headers): NettyServerRequest =
      options(ctx, request, currentHeaders = Some(newHeaders))

    /**
     * Per RFC 7231, CONNECT, GET, HEAD, DELETE, OPTIONS, TRACE method bodies'
     * have no defined semantics, and must be ignored.
     *
     * @SEE
     *   https://www.rfc-editor.org/rfc/rfc7231#page-24
     */
    override final val body: Body = Body.empty

    override final val method: Method = OPTIONS

  }

  def patch(
    ctx: ChannelHandlerContext,
    request: HttpRequest,
    currentHeaders: Option[Headers] = None,
    currentUrl: Option[URL] = None,
    currentVersion: Option[Version] = None,
  ): NettyServerRequest = new NettyServerRequest(ctx, request, currentHeaders, currentUrl, currentVersion)
    with Update
    with SupportsBody {
    self =>
    override def withUpdatedVersion(newVersion: Version): NettyServerRequest =
      patch(ctx, request, currentVersion = Some(newVersion))

    override def withUpdatedUrl(setUrl: URL): NettyServerRequest = patch(ctx, request, currentUrl = Some(setUrl))

    override def withUpdatedHeaders(newHeaders: Headers): NettyServerRequest =
      patch(ctx, request, currentHeaders = Some(newHeaders))

    override final val method: Method = PATCH

  }

  def post(
    ctx: ChannelHandlerContext,
    request: HttpRequest,
    currentHeaders: Option[Headers] = None,
    currentUrl: Option[URL] = None,
    currentVersion: Option[Version] = None,
  ): NettyServerRequest = new NettyServerRequest(ctx, request, currentHeaders, currentUrl, currentVersion)
    with Update
    with SupportsBody {
    self =>
    override def withUpdatedVersion(newVersion: Version): NettyServerRequest =
      post(ctx, request, currentVersion = Some(newVersion))

    override def withUpdatedUrl(setUrl: URL): NettyServerRequest = post(ctx, request, currentUrl = Some(setUrl))

    override def withUpdatedHeaders(newHeaders: Headers): NettyServerRequest =
      post(ctx, request, currentHeaders = Some(newHeaders))

    override final val method: Method = POST

  }

  def put(
    ctx: ChannelHandlerContext,
    request: HttpRequest,
    currentHeaders: Option[Headers] = None,
    currentUrl: Option[URL] = None,
    currentVersion: Option[Version] = None,
  ): NettyServerRequest = new NettyServerRequest(ctx, request, currentHeaders, currentUrl, currentVersion)
    with Update
    with SupportsBody {
    self =>
    override def withUpdatedVersion(newVersion: Version): NettyServerRequest =
      put(ctx, request, currentVersion = Some(newVersion))

    override def withUpdatedUrl(setUrl: URL): NettyServerRequest = put(ctx, request, currentUrl = Some(setUrl))

    override def withUpdatedHeaders(newHeaders: Headers): NettyServerRequest =
      put(ctx, request, currentHeaders = Some(newHeaders))

    override final val method: Method = PUT

  }

  def trace(
    ctx: ChannelHandlerContext,
    request: HttpRequest,
    currentHeaders: Option[Headers] = None,
    currentUrl: Option[URL] = None,
    currentVersion: Option[Version] = None,
  ): NettyServerRequest = new NettyServerRequest(ctx, request, currentHeaders, currentUrl, currentVersion) with Update {
    self =>
    override def withUpdatedVersion(newVersion: Version): NettyServerRequest =
      trace(ctx, request, currentVersion = Some(newVersion))

    override def withUpdatedUrl(setUrl: URL): NettyServerRequest = trace(ctx, request, currentUrl = Some(setUrl))

    override def withUpdatedHeaders(newHeaders: Headers): NettyServerRequest =
      trace(ctx, request, currentHeaders = Some(newHeaders))

    /**
     * Per RFC 7231, CONNECT, GET, HEAD, DELETE, OPTION, TRACE method bodies'
     * have no defined semantics, and must be ignored.
     *
     * @SEE
     *   https://www.rfc-editor.org/rfc/rfc7231#page-24
     */
    override final val body: Body = Body.empty

    override final val method: Method = TRACE

  }

}
