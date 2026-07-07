package zio.http.h2

import java.util.concurrent.atomic.AtomicLong

import scala.annotation.experimental
import scala.collection.immutable.ListMap

import zio.blocks.chunk.Chunk
import zio.blocks.context.Context
import zio.blocks.endpoint.{RouteTree, SegmentSubtree}
import zio.blocks.mux.{MuxError, MuxStream}
import zio.blocks.scope.Scope
import zio.blocks.telemetry.{AttributeValue, ConsoleLogRecordProcessor, LoggerProvider, SpanKind, metric, trace}

import zio.http.h2.H2Frame.{Data, Headers, WindowUpdate}
import zio.http.h2.hpack.{HeaderField, Hpack}
import zio.http.{
  BindAddress,
  Body,
  BoundAddress,
  BoundConnector,
  BoundConnectorHandle,
  Connector,
  DefectHandler,
  Halt,
  Header,
  Method,
  Protocol,
  Request,
  Response,
  Route,
  Routes,
  Scheme,
  URL,
  Version,
}

@experimental
final class H2Transport[Ctx](
  routes: Routes[Ctx],
  context: Context[Ctx],
  connector: Connector,
  defectHandler: DefectHandler,
) {
  private val routeTree: RouteTree[Route[Ctx]] =
    H2Transport.buildRouteTree(routes)

  private val requestCounter        = metric.counter("http.requests.total")
  private val activeConnections     = metric.upDownCounter("http.connections.active")
  private val activeConnectionCount = new AtomicLong(0L)
  private val logger                =
    LoggerProvider.builder.addLogRecordProcessor(new ConsoleLogRecordProcessor).build().get("zio.http.h2.H2Transport")

  private val http2Config = connector.protocol match {
    case Protocol.H2C(http2)   => http2
    case Protocol.H2(_, http2) => http2
    case Protocol.H3(_, _, _)  => throw new UnsupportedOperationException("H3/QUIC is not implemented yet")
  }

  def start(): BoundConnectorHandle =
    connector.bind match {
      case BindAddress.Tcp(host, port) =>
        val listener = new TcpListener(
          host,
          port,
          tlsConfig,
          (input, output) => {
            activeConnectionCount.incrementAndGet()
            activeConnections.add(1L, "protocol" -> protocolName)
            try {
              val flowController =
                new FlowController(H2Settings.DefaultInitialWindowSize.toInt, http2Config.initialWindowSize)
              val connection     = new H2Connection(input, output, http2Config.maxConcurrentStreams, flowController)
              connection.run(stream => handleStream(stream, flowController))
            } finally {
              activeConnectionCount.decrementAndGet()
              activeConnections.add(-1L, "protocol" -> protocolName)
            }
          },
        )
        val bound    = listener.start()
        BoundConnectorHandle(
          BoundConnector(BoundAddress.Tcp(bound.host, bound.port), connector.protocol),
          bound.close,
          bound.isRunning,
        )
      case BindAddress.Unix(path)      =>
        throw new UnsupportedOperationException("Unix domain sockets are not implemented yet: " + path)
    }

  private def tlsConfig =
    connector.protocol match {
      case Protocol.H2C(_)      => None
      case Protocol.H2(tls, _)  => Some(tls)
      case Protocol.H3(_, _, _) => None
    }

  private def handleStream(stream: MuxStream[Int, H2Frame, H2Frame], flowController: FlowController): Unit = {
    try {
      val requestFrame = awaitHeaders(stream)
      val request      = decodeRequest(requestFrame, stream)
      val response     = instrumentRequest(request)
      sendResponse(stream, request.method, response, flowController)
    } catch {
      case e: Throwable =>
        logger.error(
          "H2 stream error",
          "stream_id"     -> AttributeValue.LongValue(stream.id.toLong),
          "error_type"    -> AttributeValue.StringValue(e.getClass.getSimpleName),
          "error_message" -> AttributeValue.StringValue(Option(e.getMessage).getOrElse("")),
          "stacktrace"    -> AttributeValue.StringValue(stackTraceToString(e)),
        )
        try {
          sendResponse(stream, Method.GET, Response.internalServerError, flowController)
        } catch {
          case _: Throwable => () // Best effort: if error response also fails, give up silently
        }
    } finally {
      flowController.removeStream(stream.id)
    }
  }

  private def instrumentRequest(request: Request): Response = {
    val startedAtNanos = System.nanoTime()
    val requestPath    = request.url.path.encode

    trace.span("http.request", SpanKind.Server) { span =>
      span.setAttribute("http.request.method", request.method.toString)
      span.setAttribute("url.path", requestPath)
      span.setAttribute("network.protocol.name", protocolName)

      val response   = handleRequest(request)
      val durationMs = nanosToMillis(System.nanoTime() - startedAtNanos)

      span.setAttribute("http.response.status_code", response.status.code.toLong)
      span.setAttribute("http.server.active_connections", activeConnectionCount.get())
      span.setAttribute("http.server.duration_ms", durationMs)
      requestCounter.add(
        1L,
        "method"      -> request.method.toString,
        "path"        -> requestPath,
        "status"      -> response.status.code,
        "protocol"    -> protocolName,
      )
      logger.info(
        "HTTP request",
        "method"      -> AttributeValue.StringValue(request.method.toString),
        "path"        -> AttributeValue.StringValue(requestPath),
        "status"      -> AttributeValue.LongValue(response.status.code.toLong),
        "duration_ms" -> AttributeValue.LongValue(durationMs),
      )

      response
    }
  }

  private def awaitHeaders(stream: MuxStream[Int, H2Frame, H2Frame]): Headers =
    awaitFrame(stream) match {
      case headers: Headers => headers
      case other            => throw new IllegalStateException("Expected HTTP/2 HEADERS frame but received: " + other)
    }

  private def decodeRequest(initialHeaders: Headers, stream: MuxStream[Int, H2Frame, H2Frame]): Request = {
    val decodedHeaders = decodeHeaderBlock(initialHeaders.headerBlock)
    val pseudoHeaders  = collectPseudoHeaders(decodedHeaders)
    val httpHeaders    = buildRequestHeaders(decodedHeaders, pseudoHeaders.authority)
    val body           =
      if (initialHeaders.endStream) Body.empty
      else Body.fromChunk(readRequestBody(stream))

    Request(
      method = parseMethod(pseudoHeaders.method),
      url = parseUrl(
        pseudoHeaders.path,
        pseudoHeaders.scheme,
        pseudoHeaders.authority,
      ),
      headers = httpHeaders,
      body = body,
      version = Version.`HTTP/2.0`,
    )
  }

  private def handleRequest(request: Request): Response = {
    routeTree.get(request.method, request.path) match {
      case Some(route) =>
        route.pattern.decode(request.method, request.path) match {
          case Right(vars) =>
            val openScope = Scope.global.open()
            try {
              toResponse(invokeHandler(route, request, vars, openScope.scope), request)
            } finally {
              openScope.close().orThrow()
            }
          case Left(_)     => Response.notFound
        }
      case None        => Response.notFound
    }
  }

  private def invokeHandler(route: Route[Ctx], request: Request, vars: Any, scope: Scope): Any =
    try route.handler.handle(request, context, vars, scope)
    catch {
      case throwable: Throwable =>
        try defectHandler.handleDefect(request, throwable)
        catch {
          case _: Throwable => Response.internalServerError
        }
    }

  private def sendResponse(
    stream: MuxStream[Int, H2Frame, H2Frame],
    requestMethod: Method,
    response: Response,
    flowController: FlowController,
  ): Unit = {
    val bodyBytes       = if (requestMethod == Method.HEAD) Chunk.empty[Byte] else response.body.toChunk
    val bodyIsEmpty     = bodyBytes.isEmpty
    val responseHeaders = buildResponseHeaders(response, bodyBytes, bodyIsEmpty)
    val encodedHeaders  = Hpack.encode(responseHeaders)
    sendFrame(stream, Headers(stream.id, encodedHeaders, endStream = bodyIsEmpty, endHeaders = true))

    if (!bodyIsEmpty) {
      val chunks = chunkBody(bodyBytes, http2Config.maxFrameSize)
      var index  = 0
      while (index < chunks.length) {
        val chunk     = chunks(index)
        val endStream = index == chunks.length - 1
        flowController.consumeSendWindow(stream.id, chunk.length)
        sendFrame(stream, Data(stream.id, chunk, endStream = endStream))
        index += 1
      }
    }
  }

  private def readRequestBody(stream: MuxStream[Int, H2Frame, H2Frame]): Chunk[Byte] = {
    val builder = Chunk.newBuilder[Byte]
    var done    = false

    while (!done) {
      awaitFrame(stream) match {
        case data: Data       =>
          builder ++= data.data
          done = data.endStream
        case headers: Headers =>
          done = headers.endStream
        case _: WindowUpdate  => ()
        case other => throw new IllegalStateException("Unexpected HTTP/2 frame while reading request body: " + other)
      }
    }

    builder.result()
  }

  private def awaitFrame(stream: MuxStream[Int, H2Frame, H2Frame]): H2Frame = {
    var frame: H2Frame = null
    while (frame == null) {
      toReceivedFrame(stream.receive()) match {
        case Left(error) => throw new IllegalStateException("HTTP/2 stream receive failed: " + error)
        case Right(next) => frame = next
        case null        => park()
      }
    }
    frame
  }

  private def sendFrame(stream: MuxStream[Int, H2Frame, H2Frame], frame: H2Frame): Unit = {
    val result = stream.send(frame)
    toSendError(result).foreach { error =>
      throw new IllegalStateException("HTTP/2 stream send failed: " + error)
    }
  }

  private def decodeHeaderBlock(headerBlock: Chunk[Byte]): List[HeaderField] =
    Hpack.decode(headerBlock) match {
      case Right(headers) => headers
      case Left(error)    => throw new IllegalStateException("Failed to decode HPACK header block: " + error)
    }

  private def collectPseudoHeaders(headers: List[HeaderField]): H2Transport.PseudoHeaders = {
    var method: String    = null
    var path: String      = null
    var scheme: String    = null
    var authority: String = null

    val iterator = headers.iterator
    while (iterator.hasNext) {
      val header = iterator.next()
      header.name match {
        case ":method"    => method = header.value
        case ":path"      => path = header.value
        case ":scheme"    => scheme = header.value
        case ":authority" => authority = header.value
        case _            => ()
      }
    }

    if (method == null || path == null)
      throw new IllegalStateException("HTTP/2 request is missing required pseudo-headers")

    H2Transport.PseudoHeaders(method, path, scheme, authority)
  }

  private def buildRequestHeaders(headers: List[HeaderField], authority: String): zio.http.Headers = {
    val builder  = zio.http.HeadersBuilder.make(headers.length + (if (authority == null) 0 else 1))
    val iterator = headers.iterator

    while (iterator.hasNext) {
      val header = iterator.next()
      if (!header.name.startsWith(":")) builder.add(header.name, header.value)
    }

    if (authority != null && !containsHost(headers)) builder.add(Header.Host.name, authority)

    builder.build()
  }

  private def containsHost(headers: List[HeaderField]): Boolean = {
    val iterator = headers.iterator
    var found    = false
    while (iterator.hasNext && !found) {
      found = iterator.next().name == Header.Host.name
    }
    found
  }

  private def parseMethod(name: String): Method =
    Method.fromString(name).getOrElse(throw new IllegalStateException("Unsupported HTTP method: " + name))

  private def parseUrl(pathValue: String, schemeValue: String, authorityValue: String): URL = {
    val parsed     = URL
      .parse(pathValue)
      .getOrElse(throw new IllegalStateException("Invalid HTTP/2 :path pseudo-header: " + pathValue))
    val withScheme =
      if (schemeValue == null) parsed
      else parsed.scheme(Scheme.fromString(schemeValue))

    authorityValue match {
      case null  => withScheme
      case value =>
        Header.Host.parse(value) match {
          case Right(host) =>
            val withHost = withScheme.host(host.host)
            host.port match {
              case Some(port) => withHost.port(port)
              case None       => withHost
            }
          case Left(_)     => withScheme.host(value)
        }
    }
  }

  private def buildResponseHeaders(response: Response, body: Chunk[Byte], bodyIsEmpty: Boolean): List[HeaderField] = {
    val builder = List.newBuilder[HeaderField]
    builder += HeaderField(":status", response.status.code.toString)

    val normalizedResponse =
      if (bodyIsEmpty) response
      else if (response.headers.has(Header.ContentLength.name)) response
      else response.addHeader(Header.ContentLength(body.length.toLong))

    val headers = normalizedResponse.headers.toList
    var index   = 0
    while (index < headers.length) {
      val header = headers(index)
      if (!header._1.startsWith(":")) builder += HeaderField(header._1, header._2)
      index += 1
    }

    builder.result()
  }

  private def toResponse(result: Any, request: Request): Response =
    result match {
      case response: Response       => response
      case halt: Halt               => halt.response
      case Left(response: Response) => response
      case Right(halt: Halt)        => halt.response
      case other                    =>
        try {
          toResponse(
            defectHandler.handleDefect(request, new IllegalStateException("Unexpected handler result: " + other)),
            request,
          )
        } catch {
          case _: Throwable => Response.internalServerError
        }
    }

  private def chunkBody(body: Chunk[Byte], maxFrameSize: Int): Chunk[Chunk[Byte]] = {
    val frameSize = Math.max(1, maxFrameSize)
    val builder   = Chunk.newBuilder[Chunk[Byte]]
    var offset    = 0
    while (offset < body.length) {
      val remaining = body.length - offset
      val size      = Math.min(frameSize, remaining)
      builder += body.slice(offset, offset + size)
      offset += size
    }
    builder.result()
  }

  private def park(): Unit =
    try Thread.sleep(1L)
    catch {
      case _: InterruptedException => Thread.currentThread().interrupt()
    }

  private def protocolName: String =
    connector.protocol match {
      case Protocol.H2C(_)      => "h2c"
      case Protocol.H2(_, _)    => "h2"
      case Protocol.H3(_, _, _) => "h3"
    }

  private def nanosToMillis(nanos: Long): Long = nanos / 1000000L

  private def stackTraceToString(e: Throwable): String = {
    val sw = new java.io.StringWriter()
    e.printStackTrace(new java.io.PrintWriter(sw))
    sw.toString
  }

  private def toReceivedFrame(result: Any): Either[MuxError, H2Frame] =
    result match {
      case Left(error: MuxError)       => Left(error)
      case Right(Some(frame: H2Frame)) => Right(frame)
      case Right(None)                 => Right(null)
      case Some(frame: H2Frame)        => Right(frame)
      case None                        => Right(null)
      case other                       => Left(MuxError.ProtocolError("Unexpected mux receive result: " + other))
    }

  private def toSendError(result: Any): Option[MuxError] =
    result match {
      case Left(error: MuxError) => Some(error)
      case Right(_)              => None
      case ()                    => None
      case unexpected            =>
        throw new IllegalStateException(
          s"Stream send failed with unexpected result: $unexpected (${unexpected.getClass.getSimpleName})",
        )
    }
}

@experimental
object H2Transport {
  private def buildRouteTree[Ctx](routes: Routes[Ctx]): RouteTree[Route[Ctx]] =
    routes.routes.foldLeft(RouteTree.empty[Route[Ctx]]) { (tree, route) =>
      val alternatives = route.pattern.alternatives
      if (alternatives.nonEmpty) tree.add(route.pattern, route)
      else tree.merge(rootRouteTree(route))
    }

  private def rootRouteTree[Ctx](route: Route[Ctx]): RouteTree[Route[Ctx]] = {
    val rootSubtree = SegmentSubtree[Route[Ctx]](Map.empty, ListMap.empty, Some(route))
    RouteTree(Map(route.pattern.method -> rootSubtree))
  }

  private final case class PseudoHeaders(
    method: String,
    path: String,
    scheme: String,
    authority: String,
  )

  private implicit final class EitherOps[A](private val either: Either[String, A]) extends AnyVal {
    def getOrElse(default: => A): A =
      either match {
        case Right(value) => value
        case Left(_)      => default
      }
  }
}
