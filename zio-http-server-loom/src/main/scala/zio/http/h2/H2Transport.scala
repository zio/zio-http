package zio.http.h2

import java.util.concurrent.atomic.{AtomicBoolean, AtomicLong}

import scala.annotation.experimental
import scala.collection.immutable.ListMap
import scala.util.control.NonFatal

import zio.blocks.chunk.Chunk
import zio.blocks.context.Context
import zio.blocks.endpoint.{RouteTree, SegmentSubtree}
import zio.blocks.mux.{MuxError, MuxStream}
import zio.blocks.scope.Scope
import zio.blocks.telemetry.{AttributeValue, ConsoleLogRecordProcessor, LoggerProvider, SpanKind, metric, trace}

import zio.http.h2.H2Frame.{Data, Headers, WindowUpdate}
import zio.http.h2.hpack.{HeaderField, Hpack, HpackCodec}
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
  TrustedProxyConfig,
  URL,
  Version,
}

@experimental
final class H2Transport[Ctx](
  routes: Routes[Ctx],
  context: Context[Ctx],
  connector: Connector,
  defectHandler: DefectHandler,
  sendWindowTimeoutMs: Long = FlowController.DefaultSendWindowTimeoutMs,
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

  // Single source for the wire SETTINGS payload: validated eagerly so an
  // invalid Http2Config fails fast at transport construction, before bind.
  private val localSettings: List[Setting] = H2Connection.settingsFor(http2Config)

  def start(): BoundConnectorHandle =
    connector.bind match {
      case BindAddress.Tcp(host, port) =>
        val listener = new TcpListener(
          host,
          port,
          tlsConfig,
          (input, output, peer) => {
            activeConnectionCount.incrementAndGet()
            activeConnections.add(1L, "protocol" -> protocolName)
            try {
              val flowController =
                new FlowController(H2Settings.DefaultInitialWindowSize.toInt, http2Config.initialWindowSize)
              val hpackCodec     = new HpackCodec()
              // Trust inputs (peer IP literal, mTLS cert presence) are
              // connection-stable: decide once per connection so the
              // per-request path never re-parses the peer IP.
              val peerTrusted    = connector.trustedProxy.isTrusted(peer.address, peer.hasPeerCert)
              val connection     =
                new H2Connection(
                  input = input,
                  output = output,
                  maxConcurrentStreams = http2Config.maxConcurrentStreams,
                  flowController = flowController,
                  hpackCodec = hpackCodec,
                  localSettings = Some(localSettings),
                  maxHeaderListSize = http2Config.maxHeaderListSize,
                  idleTimeoutMs = H2ConnectionControl.idleTimeoutMs(connector),
                  requestTimeoutMs = connector.requestTimeoutMs,
                  headerTimeoutMs = connector.headerTimeoutMs,
                )
              connection.run(stream => handleStream(stream, flowController, hpackCodec, connection, peer, peerTrusted))
            } catch {
              case e: Throwable =>
                logger.error(
                  "H2 connection error",
                  "protocol"      -> AttributeValue.StringValue(protocolName),
                  "error_type"    -> AttributeValue.StringValue(e.getClass.getSimpleName),
                  "error_message" -> AttributeValue.StringValue(Option(e.getMessage).getOrElse("")),
                  "stacktrace"    -> AttributeValue.StringValue(stackTraceToString(e)),
                )
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

  private def handleStream(
    stream: MuxStream[Int, H2Frame, H2Frame],
    flowController: FlowController,
    hpackCodec: HpackCodec,
    connection: H2Connection,
    peer: PeerInfo,
    peerTrusted: Boolean,
  ): Unit = {
    // Request timeout lives on the connection's control plane: RST_STREAM
    // (CANCEL) fires from a Loom virtual thread if the handler overruns.
    // handleStream itself already runs on a per-stream virtual thread, so no
    // ZIO fiber ever blocks here.
    val requestTimer            = connection.connectionControl.startRequestTimer(stream.id)
    // Body-completion (time-to-complete) deadline, measured from stream start:
    // total elapsed time is enforced, not just the gap between frames, so a
    // drip that keeps moving but too slowly still times out. Enforced both by
    // a virtual-thread timer (shared H2ConnectionControl path, never ZIO
    // fibers) and by polling checks in awaitFrame: the timer covers a fully
    // stalled peer, the polls cover a slow-but-moving drip. Expiry resets the
    // stream with RST_STREAM(CANCEL).
    //
    // Fused off path: when the body timeout is disabled there is no clock
    // read, no AtomicBoolean, and no timer thread — the deadline stays the
    // far-future sentinel (see bodyDeadlineNanos) and awaitFrame skips its
    // per-iteration nanoTime check for it. The VirtualTimerFuture path below
    // is unchanged when enabled.
    val bodyTimeoutEnabled      = connector.bodyTimeoutMs > 0L
    val streamStartNanos        = if (bodyTimeoutEnabled) System.nanoTime() else 0L
    val bodyDone: AtomicBoolean =
      if (bodyTimeoutEnabled) new AtomicBoolean(false) else null
    val bodyTimer               =
      if (bodyTimeoutEnabled)
        connection.connectionControl.scheduleTimeoutRst(
          stream.id,
          connector.bodyTimeoutMs,
          H2Error.Code.CANCEL,
          () => !bodyDone.get(),
        )
      else null
    try {
      val requestFrame = awaitHeaders(stream, connection)
      val request      =
        decodeRequest(requestFrame, stream, connection, bodyDeadlineNanos(streamStartNanos), peer, peerTrusted)
      if (bodyDone != null) bodyDone.set(true)
      if (bodyTimer != null) bodyTimer.cancel(true)
      val response     = instrumentRequest(request)
      sendResponse(stream, request.method, response, flowController, hpackCodec, connection)
    } catch {
      case ResponseAborted =>
        () // Response HEADERS already on the wire and RST_STREAM already sent
      // (see StreamSender): an error response now would emit a second HEADERS
      // block and corrupt the peer's HPACK dynamic table. Fall through to the
      // `finally` (request-timer cancel, flow deregistration).
      case e: Throwable =>
        logger.error(
          "H2 stream error",
          "stream_id"     -> AttributeValue.LongValue(stream.id.toLong),
          "error_type"    -> AttributeValue.StringValue(e.getClass.getSimpleName),
          "error_message" -> AttributeValue.StringValue(Option(e.getMessage).getOrElse("")),
          "stacktrace"    -> AttributeValue.StringValue(stackTraceToString(e)),
        )
        // The failure above may already have reset the stream (over-cap
        // RST_STREAM, length-mismatch PROTOCOL_ERROR): a HEADERS frame on a
        // reset stream is a connection error (STREAM_CLOSED) for the peer, so
        // the best-effort 500 goes out only while the stream is still open.
        // Genuine handler errors never reset, so the 500 path for live
        // streams is unchanged.
        if (!stream.isClosed) {
          try {
            sendResponse(stream, Method.GET, Response.internalServerError, flowController, hpackCodec, connection)
          } catch {
            case _: Throwable => () // Best effort: if error response also fails, give up silently
          }
        }
    } finally {
      if (bodyDone != null) bodyDone.set(true)
      if (bodyTimer != null) bodyTimer.cancel(true)
      requestTimer.cancel(true)
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

  private def awaitHeaders(stream: MuxStream[Int, H2Frame, H2Frame], connection: H2Connection): Headers =
    awaitFrame(stream, connection, Long.MaxValue) match {
      case headers: Headers => headers
      case other            => throw new IllegalStateException("Expected HTTP/2 HEADERS frame but received: " + other)
    }

  private def decodeRequest(
    initialHeaders: Headers,
    stream: MuxStream[Int, H2Frame, H2Frame],
    connection: H2Connection,
    bodyDeadlineNanos: Long,
    peer: PeerInfo,
    peerTrusted: Boolean,
  ): Request = {
    // Decoded on the reader thread in wire order (see H2Connection.takeDecodedRequestHeaders);
    // decoding here would desync the shared decoder across concurrent streams (RFC 7541 2.3.2).
    val decodedHeaders = connection.takeDecodedRequestHeaders(stream.id)
    val pseudoHeaders  = collectPseudoHeaders(decodedHeaders)
    val httpHeaders    = buildRequestHeaders(decodedHeaders, pseudoHeaders.authority)
    val declaredLength = declaredContentLength(httpHeaders)
    val body           =
      if (initialHeaders.endStream) {
        checkEmptyBodyLength(stream, connection, declaredLength)
        Body.empty
      } else Body.fromChunk(readRequestBody(stream, connection, declaredLength, bodyDeadlineNanos))
    val proxy          = resolveProxyTrust(httpHeaders, peer, peerTrusted)

    Request(
      method = parseMethod(pseudoHeaders.method),
      url = applyProxyUrl(
        parseUrl(
          pseudoHeaders.path,
          pseudoHeaders.scheme,
          pseudoHeaders.authority,
        ),
        proxy,
      ),
      headers = proxy.headers,
      body = body,
      version = Version.`HTTP/2.0`,
    )
  }

  /**
   * Gates forwarding headers on proxy trust (default-deny).
   *
   * When `peer` is trusted, `X-Forwarded-For/Proto/Host` (or RFC 7239
   * `Forwarded`) resolve the client IP, scheme, and host; otherwise the headers
   * are ignored entirely and the socket peer address is the client IP. Either
   * way the raw forwarding headers are stripped before route handlers run (an
   * attacker must not smuggle them through, nor spoof the normalized
   * `x-client-ip` / `x-peer-address` headers, which are removed from the wire
   * set and re-added here), and the peer address plus the resolved client IP
   * are attached as `x-peer-address` / `x-client-ip`.
   */
  private def resolveProxyTrust(
    headers: zio.http.Headers,
    peer: PeerInfo,
    peerTrusted: Boolean,
  ): H2Transport.ResolvedProxy = {
    val trusted   = peerTrusted
    val forwarded = if (trusted) H2Transport.parseForwarded(headers) else None
    val clientIp  = forwarded.flatMap(_.clientIp).getOrElse(peer.address)
    // Pre-check before stripping: the common default-deny case (no
    // forwarding headers on the wire) keeps `headers` untouched and allocates
    // nothing; otherwise one single-pass rebuild, not one copy per name.
    val stripped  =
      if (H2Transport.hasProxyHeader(headers)) H2Transport.stripProxyHeaders(headers)
      else headers
    val enriched  = stripped
      .add(TrustedProxyConfig.PeerAddressHeader, peer.address)
      .add(TrustedProxyConfig.ClientIpHeader, clientIp)
    val withHost  = forwarded.flatMap(_.host) match {
      case Some(host) => enriched.set(Header.Host.name, host)
      case None       => enriched
    }
    H2Transport.ResolvedProxy(withHost, forwarded.flatMap(_.proto), forwarded.flatMap(_.host))
  }

  private def applyProxyUrl(url: URL, proxy: H2Transport.ResolvedProxy): URL = {
    val withScheme = proxy.proto match {
      case Some(proto) => url.scheme(Scheme.fromString(proto))
      case None        => url
    }
    proxy.host match {
      case Some(host) =>
        Header.Host.parse(host) match {
          case Right(parsed) =>
            val withHost = withScheme.host(parsed.host)
            parsed.port match {
              case Some(port) => withHost.port(port)
              case None       => withHost
            }
          case Left(_)       => withScheme.host(host)
        }
      case None       => withScheme
    }
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
    hpackCodec: HpackCodec,
    connection: H2Connection,
  ): Unit = {
    // HEAD responses never carry a body (RFC 9110 9.3.2): headers close the stream.
    if (requestMethod == Method.HEAD) {
      val responseHeaders = buildResponseHeaders(response, None, bodyIsEmpty = true)
      writeResponseHeaders(stream, hpackCodec, connection, responseHeaders, endStream = true)
      return
    }
    // Fast path: the body is already materialized (knownChunk) — framing it
    // costs no collection. Slow path: pull the stream chunk-by-chunk so an
    // unbounded body never lands on the heap at once (Todo 6). Both paths run
    // on this stream's Loom virtual thread; FlowController parks it on a
    // Condition under backpressure, so no ZIO fiber ever blocks here.
    response.body.toStream.knownChunk match {
      case Some(chunk) => sendKnownBody(stream, response, chunk, flowController, hpackCodec, connection)
      case None        => sendStreamedBody(stream, response, flowController, hpackCodec, connection)
    }
  }

  private def sendKnownBody(
    stream: MuxStream[Int, H2Frame, H2Frame],
    response: Response,
    body: Chunk[Byte],
    flowController: FlowController,
    hpackCodec: HpackCodec,
    connection: H2Connection,
  ): Unit = {
    probeStreamOpen(stream)
    val bodyIsEmpty     = body.isEmpty
    val responseHeaders = buildResponseHeaders(response, Some(body.length.toLong), bodyIsEmpty)
    writeResponseHeaders(stream, hpackCodec, connection, responseHeaders, endStream = bodyIsEmpty)

    if (!bodyIsEmpty) {
      val sender = new StreamSender(stream, flowController, connection)
      try {
        val chunks = chunkBody(body, http2Config.maxFrameSize)
        var index  = 0
        while (index < chunks.length) {
          sender.send(chunks(index), endStream = index == chunks.length - 1)
          index += 1
        }
      } catch {
        case _: H2Transport.Aborted => throw ResponseAborted
      }
    }
  }

  /**
   * Streams a body whose chunk is not materialized: pulls
   * `body.toStream.chunked(maxFrameSize)` and writes each chunk as a DATA frame
   * gated by `FlowController.consumeSendWindow`. Only one maxFrameSize chunk is
   * ever in flight, so a 10MB (or unbounded) body streams in ~16KB of heap.
   * Bodies with a known length advertise Content-Length; unknown lengths stream
   * until the terminal END_STREAM with no Content-Length.
   *
   * Latency-sensitive `text/event-stream` bodies take the per-event flush path
   * instead (see `sendSseFrames`); everything else flows through `chunked`
   * here.
   *
   * jvm-perf notes: the per-chunk callback captures only stable references (no
   * `*Ref` mutable capture, no boxing — lengths stay primitive `Int`), the hot
   * calls are monomorphic (`final FlowController`, one `MuxStream` impl), and
   * backpressure parks on a Condition (no spin).
   */
  private def sendStreamedBody(
    stream: MuxStream[Int, H2Frame, H2Frame],
    response: Response,
    flowController: FlowController,
    hpackCodec: HpackCodec,
    connection: H2Connection,
  ): Unit = {
    probeStreamOpen(stream)
    val frameSize       = Math.max(1, http2Config.maxFrameSize)
    val knownLength     = response.body.length
    // A zero known length with no known chunk closes on HEADERS like an empty body.
    val bodyIsEmpty     = knownLength.contains(0L)
    val responseHeaders =
      buildResponseHeaders(response, knownLength.filterNot(_ => bodyIsEmpty), bodyIsEmpty = bodyIsEmpty)
    writeResponseHeaders(stream, hpackCodec, connection, responseHeaders, endStream = bodyIsEmpty)
    if (bodyIsEmpty) return

    val sender = new StreamSender(stream, flowController, connection)
    try {
      if (isEventStream(response)) {
        // Todo 9: latency-sensitive SSE path — per-event flush (see sendSseFrames).
        sendSseFrames(response, sender, frameSize)
        sender.send(Chunk.empty[Byte], endStream = true)
      } else {
        response.body.toStream.chunked(frameSize).runForeach { chunk =>
          // chunked never emits empties, but a defensive skip keeps the
          // flow-control accounting (consumeSendWindow is a no-op on 0 anyway)
          // and the wire trace free of zero-length DATA frames.
          if (!chunk.isEmpty) sender.send(chunk, endStream = false)
        } match {
          case Right(())        => sender.send(Chunk.empty[Byte], endStream = true)
          case Left(impossible) => throw impossible
        }
      }
    } catch {
      case _: H2Transport.Aborted => throw ResponseAborted
    }
  }

  /**
   * Todo 9: latency-sensitive `text/event-stream` send path.
   *
   * SSE carries inter-message delay as its payload contract: each event must
   * hit the wire promptly. The accumulate-to-`maxFrameSize` `chunked` traversal
   * used for throughput-oriented bodies would hold small events until a full
   * frame accumulates (or the stream ends), destroying the delay — and the
   * stream API offers no non-blocking readiness probe that would let a consumer
   * flush promptly (`readable()` is optimistic on compute-backed readers, so
   * `readUpToN` degrades to blocking `readN`).
   *
   * Instead frame on the media type's own self-delimiting unit: an SSE message
   * ends at a blank line, so bytes accumulate only until the terminator (or
   * `maxFrameSize`, for events larger than a frame) and flush as one DATA frame
   * per event. Within an event the producer pulls are pure compute (no sleep —
   * the delay sits strictly *between* events), so each event's bytes arrive
   * back-to-back and flush immediately, while the next pull parks in the
   * inter-message delay. No timing assumption, no extra thread, no timeout
   * tuning: framing follows the bytes, and delay preservation falls out of the
   * producer's pacing. A body that never emits a blank line still flushes every
   * full frame, so framing degrades to `chunked`-like behavior instead of
   * stalling or growing without bound.
   *
   * Flow-gating (`consumeSendWindow` per frame), abort/RST mapping (via
   * `StreamSender`, whose `Aborted` control exception propagates through
   * `runForeach` exactly as on the `chunked` path), and the terminal empty
   * END_STREAM match `sendStreamedBody` exactly; only the grouping strategy
   * differs. Throughput bodies keep the `chunked` path untouched.
   *
   * jvm-perf notes: per-byte `runForeach` boxes on this lane only (SSE is
   * latency-oriented, not the bulk path); at most one frame is ever buffered,
   * so unbounded event streams still stream in ~16KB of heap.
   */
  private def sendSseFrames(response: Response, sender: StreamSender, frameSize: Int): Unit = {
    var builder        = Chunk.newBuilder[Byte]
    var buffered       = 0
    var lineHasContent = false
    response.body.toStream.runForeach { byte =>
      builder += byte
      buffered += 1
      if (byte == '\n'.toByte) {
        if (!lineHasContent) {
          sender.send(builder.result(), endStream = false)
          builder = Chunk.newBuilder[Byte]
          buffered = 0
        }
        lineHasContent = false
      } else if (byte != '\r'.toByte) lineHasContent = true
      if (buffered >= frameSize) {
        sender.send(builder.result(), endStream = false)
        builder = Chunk.newBuilder[Byte]
        buffered = 0
      }
    } match {
      case Right(())        =>
        // Well-formed event streams end on a blank line (nothing buffered);
        // flush any unterminated tail rather than dropping it. The caller
        // still closes with the terminal empty END_STREAM.
        if (buffered > 0) sender.send(builder.result(), endStream = false)
      case Left(impossible) => throw impossible
    }
  }

  /** True when the response carries a streamed `text/event-stream` body. */
  private def isEventStream(response: Response): Boolean = {
    val headers = response.headers.toList
    var index   = 0
    var found   = false
    while (index < headers.length && !found) {
      val header = headers(index)
      if (
        header._1.equalsIgnoreCase(Header.ContentType.name) &&
        header._2.toLowerCase(java.util.Locale.ROOT).contains("text/event-stream")
      ) found = true
      index += 1
    }
    found
  }

  /**
   * Sends one DATA frame under flow control, converting any post-headers
   * failure (peer close, interrupt, deregistered stream, flow-control timeout)
   * into a single RST_STREAM plus a [[H2Transport.Aborted]] control exception.
   * The caller maps that to [[ResponseAborted]] so `handleStream` skips its
   * error-response attempt: response HEADERS are already on the wire, so a
   * second HEADERS block would corrupt the peer's HPACK dynamic table.
   * Idempotent: the first abort wins, later failures are silent.
   *
   * RST code choice: a genuine window-overflow violation
   * ([[FlowController.FlowControlException]], RFC 9113 section 6.9.1) surfaces
   * as FLOW_CONTROL_ERROR; every other send failure — including a bounded
   * flow-control wait expiring ([[FlowController.FlowControlTimeout]], a local
   * abort rather than a peer violation) — surfaces as CANCEL. Both go through
   * the single T5 `H2ConnectionControl.sendRstStream` send site; no duplicate
   * RST machinery lives here.
   */
  private final class StreamSender(
    stream: MuxStream[Int, H2Frame, H2Frame],
    flowController: FlowController,
    connection: H2Connection,
  ) {
    private val aborted = new AtomicBoolean(false)

    def send(chunk: Chunk[Byte], endStream: Boolean): Unit =
      try {
        // A remotely-reset (or torn-down) stream must not consume window or
        // queue doomed DATA: abort promptly instead of waiting for a send
        // failure. Both checks are single volatile reads (isClosed).
        if (stream.isClosed) {
          abort()
          throw new H2Transport.Aborted
        }
        flowController.consumeSendWindow(stream.id, chunk.length, sendWindowTimeoutMs)
        sendFrame(stream, Data(stream.id, chunk, endStream = endStream))
        if (stream.isClosed) {
          abort()
          throw new H2Transport.Aborted
        }
      } catch {
        case error: H2Transport.Aborted             => throw error
        case _: FlowController.FlowControlException =>
          abort(H2Error.Code.FLOW_CONTROL_ERROR)
          throw new H2Transport.Aborted
        case _: FlowController.FlowControlTimeout   =>
          abort()
          throw new H2Transport.Aborted
        case NonFatal(_)                            =>
          abort()
          throw new H2Transport.Aborted
      }

    private def abort(): Unit = abort(H2Error.Code.CANCEL)

    private def abort(errorCode: H2Error.Code): Unit =
      if (aborted.compareAndSet(false, true)) {
        try connection.connectionControl.sendRstStream(stream.id, errorCode)
        catch {
          case NonFatal(_) => ()
        }
      }
  }

  /**
   * Control-flow marker: the response started (HEADERS on the wire) and then
   * aborted with RST_STREAM already sent. `handleStream` must not attempt an
   * error response and just runs its `finally` (request-timer cancel, flow
   * deregistration). Stackless: this is routine control flow, not a defect.
   */
  private object ResponseAborted extends RuntimeException("HTTP/2 response aborted after headers") {
    override def fillInStackTrace(): Throwable = this
  }

  /**
   * Reads the request body DATA stream incrementally against the
   * `Connector.maxRequestBodySize` cap: each DATA payload is accounted BEFORE
   * it is buffered, so the byte that crosses the cap is never retained and an
   * over-cap body never lands on the heap in full. On exceed the stream is
   * reset with `RST_STREAM(FLOW_CONTROL_ERROR)` — the same code the response
   * abort path uses for window-overflow violations — and the connection
   * survives for sibling streams. A `content-length` that disagrees with the
   * bytes actually received resets with `PROTOCOL_ERROR` instead.
   *
   * The reset reuses the single upstream send site
   * (`H2ConnectionControl.sendRstStream`, shared with the response abort and
   * timer paths): no parallel RST machinery lives here.
   */
  private def readRequestBody(
    stream: MuxStream[Int, H2Frame, H2Frame],
    connection: H2Connection,
    declaredLength: Option[Long],
    deadlineNanos: Long,
  ): Chunk[Byte] = {
    val maxBytes = connector.maxRequestBodySize
    declaredLength.foreach { declared =>
      if (declared > maxBytes) {
        // Declared over the cap: drain the wire (retaining nothing) so the
        // reset below cannot race trailing DATA delivery, then reset.
        discardRequestBody(stream, connection, deadlineNanos)
        resetStream(stream, connection, H2Error.Code.FLOW_CONTROL_ERROR)
        throw H2Transport.RequestBodyTooLarge(stream.id, maxBytes)
      } else if (declared < 0L) {
        resetStream(stream, connection, H2Error.Code.PROTOCOL_ERROR)
        throw H2Transport.RequestBodyLengthMismatch(stream.id, declared, 0L)
      }
    }

    val builder  = Chunk.newBuilder[Byte]
    var received = 0L
    var done     = false

    while (!done) {
      awaitFrame(stream, connection, deadlineNanos) match {
        case data: Data       =>
          val size = data.data.length.toLong
          // Account BEFORE buffering: the byte that crosses the cap is never retained.
          if (received + size > maxBytes) {
            resetStream(stream, connection, H2Error.Code.FLOW_CONTROL_ERROR)
            throw H2Transport.RequestBodyTooLarge(stream.id, maxBytes)
          }
          builder ++= data.data
          received += size
          if (data.endStream) {
            done = true
            checkReceivedLength(stream, connection, declaredLength, received)
          }
        case headers: Headers =>
          done = headers.endStream
          if (headers.endStream) checkReceivedLength(stream, connection, declaredLength, received)
        case _: WindowUpdate  => ()
        case other => throw new IllegalStateException("Unexpected HTTP/2 frame while reading request body: " + other)
      }
    }

    builder.result()
  }

  /**
   * Reads until end-of-stream, retaining no bytes. Used when the declared
   * `content-length` already exceeds the cap: draining keeps connection-level
   * framing intact so the subsequent reset cannot race trailing DATA delivery.
   * Retention stays at zero throughout; the peer's flow-control window bounds
   * how much can arrive.
   */
  private def discardRequestBody(
    stream: MuxStream[Int, H2Frame, H2Frame],
    connection: H2Connection,
    deadlineNanos: Long,
  ): Unit = {
    var done = false
    while (!done) {
      awaitFrame(stream, connection, deadlineNanos) match {
        case data: Data       => done = data.endStream
        case headers: Headers => done = headers.endStream
        case _: WindowUpdate  => ()
        case other => throw new IllegalStateException("Unexpected HTTP/2 frame while discarding request body: " + other)
      }
    }
  }

  /**
   * Parses the declared request `content-length`, if any. Returns `Some(-1)`
   * when the value is missing, malformed, or conflicts across duplicates, so
   * every downstream comparison rejects it with `PROTOCOL_ERROR`. Uses a
   * targeted single-name scan (`rawGetAll`, case-insensitive like the old
   * `equalsIgnoreCase` walk) instead of materializing every header with
   * `toList`; duplicate/conflict semantics are unchanged.
   */
  private def declaredContentLength(headers: zio.http.Headers): Option[Long] = {
    var result: Option[Long] = None
    var conflict             = false
    val values               = headers.rawGetAll(Header.ContentLength.name)
    var index                = 0
    while (index < values.length) {
      values(index).toLongOption match {
        case Some(length) =>
          if (result.exists(_ != length)) conflict = true
          result = Some(length)
        case None         => conflict = true
      }
      index += 1
    }
    if (conflict) Some(-1L)
    else result
  }

  private def checkEmptyBodyLength(
    stream: MuxStream[Int, H2Frame, H2Frame],
    connection: H2Connection,
    declaredLength: Option[Long],
  ): Unit =
    declaredLength.foreach { declared =>
      if (declared != 0L) {
        if (declared > connector.maxRequestBodySize) {
          resetStream(stream, connection, H2Error.Code.FLOW_CONTROL_ERROR)
          throw H2Transport.RequestBodyTooLarge(stream.id, connector.maxRequestBodySize)
        } else {
          resetStream(stream, connection, H2Error.Code.PROTOCOL_ERROR)
          throw H2Transport.RequestBodyLengthMismatch(stream.id, declared, 0L)
        }
      }
    }

  private def checkReceivedLength(
    stream: MuxStream[Int, H2Frame, H2Frame],
    connection: H2Connection,
    declaredLength: Option[Long],
    received: Long,
  ): Unit =
    declaredLength.foreach { declared =>
      if (declared != received) {
        resetStream(stream, connection, H2Error.Code.PROTOCOL_ERROR)
        throw H2Transport.RequestBodyLengthMismatch(stream.id, declared, received)
      }
    }

  /**
   * Resets `stream` with `errorCode` through the single upstream send site
   * (`H2ConnectionControl.sendRstStream`, shared with the response abort and
   * timer paths), which writes the RST_STREAM frame directly under the
   * connection write lock and cancels the mux entry — so the failure stays
   * per-stream and the connection survives for sibling streams. Best-effort: if
   * the peer already reset the stream there is nobody left to notify, so a send
   * failure is swallowed and the caller still throws the underlying bound
   * violation.
   */
  /**
   * Re-check-at-send probe before the first wire write of a response: the
   * body/request timer may have fired (RST already on the wire) between
   * body-read completion and this send. Never emit HEADERS/DATA on a reset
   * stream — a HEADERS on a reset stream is a connection error (STREAM_CLOSED)
   * for the peer. Complements `StreamSender`'s per-send `isClosed` guard, which
   * covers every DATA frame including the first. The RST is already sent, so
   * aborting maps to `ResponseAborted` and skips the error response in
   * `handleStream`.
   */
  @inline private def probeStreamOpen(stream: MuxStream[Int, H2Frame, H2Frame]): Unit =
    if (stream.isClosed) throw ResponseAborted

  private def resetStream(
    stream: MuxStream[Int, H2Frame, H2Frame],
    connection: H2Connection,
    errorCode: H2Error.Code,
  ): Unit =
    try connection.connectionControl.sendRstStream(stream.id, errorCode)
    catch {
      case NonFatal(_) => ()
    }

  private def awaitFrame(
    stream: MuxStream[Int, H2Frame, H2Frame],
    connection: H2Connection,
    deadlineNanos: Long,
  ): H2Frame = {
    var frame: H2Frame = null
    while (frame == null) {
      // Fused off path: the far-future sentinel means timeouts are disabled,
      // so skip the per-iteration nanoTime read entirely.
      if (deadlineNanos != Long.MaxValue && System.nanoTime() > deadlineNanos) {
        // Time-to-complete exceeded: reset with CANCEL on the single shared
        // control path, then surface the timeout (the 500 guard below skips
        // reset streams, so no HEADERS follows the RST). Skipped when the
        // stream is already closed (the virtual-thread timer won the race and
        // its RST is on the wire): never RST a closed stream twice.
        if (!stream.isClosed) resetStream(stream, connection, H2Error.Code.CANCEL)
        throw H2Transport.StreamTimeout(stream.id, deadlineNanos)
      }
      toReceivedFrame(stream.receive()) match {
        case Left(error) => throw new IllegalStateException("HTTP/2 stream receive failed: " + error)
        case Right(next) => frame = next
        case null        => park()
      }
    }
    frame
  }

  /**
   * Body-completion deadline as absolute nanos, measured from stream start.
   * Non-positive timeouts disable (far-future deadline, never hit).
   */
  @inline private def bodyDeadlineNanos(streamStartNanos: Long): Long =
    if (connector.bodyTimeoutMs <= 0L) Long.MaxValue
    else streamStartNanos + connector.bodyTimeoutMs * 1000000L

  private def sendFrame(stream: MuxStream[Int, H2Frame, H2Frame], frame: H2Frame): Unit = {
    val result = stream.send(frame)
    toSendError(result).foreach { error =>
      throw new IllegalStateException("HTTP/2 stream send failed: " + error)
    }
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

  /**
   * HPACK-encodes and writes the response HEADERS frame to the wire atomically
   * on this thread inside the connection's write lock (see writeHeadersDirect),
   * so the wire-write order matches the encode order of the shared
   * per-connection HPACK encoder. Routing it through the per-stream
   * queue/writer thread instead would let the writer thread's arbitrary drain
   * order reorder header blocks on the wire and corrupt the peer's HPACK
   * dynamic table (RFC 7541 section 2.3.2).
   */
  private def writeResponseHeaders(
    stream: MuxStream[Int, H2Frame, H2Frame],
    hpackCodec: HpackCodec,
    connection: H2Connection,
    responseHeaders: List[HeaderField],
    endStream: Boolean,
  ): Unit =
    connection.writeHeadersDirect(
      stream,
      Headers(
        stream.id,
        hpackCodec.encode(responseHeaders),
        endStream = endStream,
        endHeaders = true,
      ),
    )

  private def buildResponseHeaders(
    response: Response,
    contentLength: Option[Long],
    bodyIsEmpty: Boolean,
  ): List[HeaderField] = {
    val builder = List.newBuilder[HeaderField]
    builder += HeaderField(":status", response.status.code.toString)

    val normalizedResponse =
      if (bodyIsEmpty) response
      else if (response.headers.has(Header.ContentLength.name)) response
      else
        contentLength match {
          // Known length (materialized chunk or stream metadata): advertise it.
          // Unknown length: stream until END_STREAM with no Content-Length.
          case Some(length) => response.addHeader(Header.ContentLength(length))
          case None         => response
        }

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

  /**
   * Thrown after resetting the stream when a request body crosses
   * `Connector.maxRequestBodySize`.
   */
  final case class RequestBodyTooLarge(streamId: Int, maxBytes: Long)
      extends java.io.IOException(
        s"HTTP/2 request body on stream $streamId exceeded maxRequestBodySize of $maxBytes bytes",
      )

  /**
   * Thrown after resetting the stream when `content-length` disagrees with the
   * bytes actually received.
   */
  final case class RequestBodyLengthMismatch(streamId: Int, declared: Long, received: Long)
      extends java.io.IOException(
        s"HTTP/2 request body on stream $streamId declared content-length $declared but received $received bytes",
      )

  /**
   * Thrown after resetting the stream with `RST_STREAM(CANCEL)` when body
   * completion exceeds its time-to-complete deadline.
   */
  final case class StreamTimeout(streamId: Int, deadlineNanos: Long)
      extends java.util.concurrent.TimeoutException(
        s"HTTP/2 stream $streamId exceeded body time-to-complete deadline",
      )

  /**
   * Per-stream abort marker: lives on the companion (static, stable prefix)
   * rather than on the StreamSender instance so `case _: H2Transport.Aborted`
   * type tests compile on both Scala 3 and 2.13 (where a path-dependent
   * `sender.Aborted` test is an "outer reference cannot be checked" error).
   * Stackless: aborts are routine control flow, not defects.
   */
  private final class Aborted extends RuntimeException("HTTP/2 response stream aborted") {
    override def fillInStackTrace(): Throwable = this
  }

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

  /**
   * Forwarding headers stripped before route handlers run. Includes the
   * normalized `x-client-ip` / `x-peer-address` headers themselves so a peer
   * cannot spoof them: they are removed from the wire set and re-added by
   * `resolveProxyTrust` after trust gating.
   */
  private val ProxyHeaderNames: List[String] =
    List("x-forwarded-for", "x-forwarded-proto", "x-forwarded-host", "forwarded", "x-client-ip", "x-peer-address")

  private final case class ForwardedValues(clientIp: Option[String], proto: Option[String], host: Option[String])

  private final case class ResolvedProxy(headers: zio.http.Headers, proto: Option[String], host: Option[String])

  /**
   * Extracts forwarding values from `X-Forwarded-For/Proto/Host`, falling back
   * to RFC 7239 `Forwarded` when no `X-Forwarded-For` is present. Only the
   * first (leftmost, closest-to-client) element is used. Returns `None` when
   * neither header family is present.
   */
  private def parseForwarded(headers: zio.http.Headers): Option[ForwardedValues] = {
    val xff = headers.rawGet("x-forwarded-for").map(firstListValue).filter(_.nonEmpty)
    if (xff.isDefined)
      Some(
        ForwardedValues(
          clientIp = xff.map(normalizeForwardedIp),
          proto = headers.rawGet("x-forwarded-proto").map(firstListValue).filter(_.nonEmpty).map(_.toLowerCase),
          host = headers.rawGet("x-forwarded-host").map(firstListValue).filter(_.nonEmpty),
        ),
      )
    else headers.rawGet("forwarded").map(parseRfc7239Forwarded)
  }

  @inline private def firstListValue(value: String): String = {
    val comma = value.indexOf(',')
    (if (comma < 0) value else value.substring(0, comma)).trim
  }

  /**
   * Normalizes one `for=` / `X-Forwarded-For` element to a bare IP: strips
   * quotes, IPv6 brackets (with optional port), and an IPv4 port suffix.
   */
  private def normalizeForwardedIp(value: String): String = {
    val trimmed = value.trim.stripPrefix("\"").stripSuffix("\"").trim
    if (trimmed.startsWith("[")) {
      val close = trimmed.indexOf(']')
      if (close > 0) trimmed.substring(1, close) else trimmed
    } else {
      val colon = trimmed.indexOf(':')
      if (colon >= 0 && trimmed.indexOf(':', colon + 1) < 0 && trimmed.contains(".")) trimmed.substring(0, colon)
      else trimmed
    }
  }

  /**
   * Parses the first element of an RFC 7239 `Forwarded` header
   * (`for=…;proto=…;host=…`). Obfuscated (`_…`) and `unknown` identifiers
   * resolve to no client IP. Scanned with `indexOf` loops — no regex `split`
   * anywhere on this path.
   */
  private def parseRfc7239Forwarded(value: String): ForwardedValues = {
    var clientIp: Option[String] = None
    var proto: Option[String]    = None
    var host: Option[String]     = None
    // First element only: up to the first ',' (or the end), mirroring the old
    // `split(",", 2)(0)`.
    val comma                    = value.indexOf(',')
    val end                      = if (comma < 0) value.length else comma
    var start                    = 0
    while (start < end) {
      val semi    = value.indexOf(';', start)
      val pairEnd = if (semi < 0 || semi > end) end else semi
      val eq      = value.indexOf('=', start)
      if (eq > start && eq < pairEnd) {
        val key = value.substring(start, eq).trim.toLowerCase
        val raw = value.substring(eq + 1, pairEnd).trim.stripPrefix("\"").stripSuffix("\"").trim
        key match {
          case "for"   =>
            if (raw.nonEmpty && !raw.equalsIgnoreCase("unknown") && !raw.startsWith("_"))
              clientIp = Some(normalizeForwardedIp(raw))
          case "proto" => if (raw.nonEmpty) proto = Some(raw.toLowerCase)
          case "host"  => if (raw.nonEmpty) host = Some(raw)
          case _       => ()
        }
      }
      start = pairEnd + 1
    }
    ForwardedValues(clientIp, proto, host)
  }

  /**
   * True when any forwarding/normalized header is present. Allocation-free
   * boolean scans, so the common default-deny case (nothing present) pays no
   * rebuild before `resolveProxyTrust` keeps the headers untouched.
   */
  private def hasProxyHeader(headers: zio.http.Headers): Boolean =
    headers.has("x-forwarded-for") ||
      headers.has("x-forwarded-proto") ||
      headers.has("x-forwarded-host") ||
      headers.has("forwarded") ||
      headers.has("x-client-ip") ||
      headers.has("x-peer-address")

  /**
   * Strips every forwarding/normalized header in a single pass over the header
   * list (one builder, one output), replacing N per-name `remove` copies.
   * `Headers` stores names pre-lowercased, so plain equality against the
   * lowercase `ProxyHeaderNames` matches `remove`'s case-insensitive behavior.
   */
  private def stripProxyHeaders(headers: zio.http.Headers): zio.http.Headers = {
    val pairs   = headers.toList
    val builder = zio.http.HeadersBuilder.make(pairs.length)
    var index   = 0
    while (index < pairs.length) {
      val (name, value) = pairs(index)
      if (!ProxyHeaderNames.contains(name)) builder.add(name, value)
      index += 1
    }
    builder.build()
  }

  private implicit final class EitherOps[A](private val either: Either[String, A]) extends AnyVal {
    def getOrElse(default: => A): A =
      either match {
        case Right(value) => value
        case Left(_)      => default
      }
  }
}
