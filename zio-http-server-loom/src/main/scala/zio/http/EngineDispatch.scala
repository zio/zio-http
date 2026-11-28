package zio.http

import scala.collection.immutable.ListMap

import zio.blocks.context.Context
import zio.blocks.endpoint.{RouteTree, SegmentSubtree}
import zio.blocks.scope.Scope
import zio.blocks.telemetry.{AttributeValue, ConsoleLogRecordProcessor, LoggerProvider, SpanKind, metric, trace}

import zio.http.h2.PeerInfo

/**
 * Protocol-neutral application semantics shared by the HTTP engines: route
 * dispatch, proxy-trust resolution, and per-request telemetry.
 *
 * Every engine constructs one instance from the same
 * `(routes, context, defectHandler, trustedProxy)` inputs and calls the same
 * methods, so handler invocation, defect mapping, forwarded-header trust
 * decisions, and telemetry stay identical across protocols.
 */
private[http] final class EngineDispatch[Ctx](
  routes: Routes[Ctx],
  context: Context[Ctx],
  defectHandler: DefectHandler,
  trustedProxy: TrustedProxyConfig,
) {

  private val routeTree: RouteTree[Route[Ctx]] = EngineDispatch.buildRouteTree(routes)

  private val requestCounter = metric.counter("http.requests.total")
  private val logger         =
    LoggerProvider.builder.addLogRecordProcessor(new ConsoleLogRecordProcessor).build().get("zio.http.EngineDispatch")

  /**
   * Connection-stable trust decision for `peer`: allowlisted CIDR match or an
   * mTLS-authenticated identity (see [[TrustedProxyConfig.isTrusted]]).
   */
  def isPeerTrusted(peer: PeerInfo): Boolean =
    trustedProxy.isTrusted(peer.address, peer.hasPeerCert)

  /**
   * Route lookup, pattern decode, per-request scope, handler invocation, and
   * defect mapping. Identical dispatch semantics for every protocol.
   */
  def handleRequest(request: Request): Response = {
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

  /**
   * Wraps request handling in the request span, the request counter, and the
   * access-log line. Callers gate this on connector telemetry so the
   * telemetry-disabled path stays allocation-free.
   */
  def instrumentRequest(request: Request, protocolName: String, activeConnections: () => Long): Response = {
    val startedAtNanos = System.nanoTime()
    val requestPath    = request.url.path.encode

    trace.span("http.request", SpanKind.Server) { span =>
      span.setAttribute("http.request.method", request.method.toString)
      span.setAttribute("url.path", requestPath)
      span.setAttribute("network.protocol.name", protocolName)

      val response   = handleRequest(request)
      val durationMs = nanosToMillis(System.nanoTime() - startedAtNanos)

      span.setAttribute("http.response.status_code", response.status.code.toLong)
      span.setAttribute("http.server.active_connections", activeConnections())
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
  def resolveProxy(headers: Headers, peer: PeerInfo, peerTrusted: Boolean): EngineDispatch.ResolvedProxy = {
    val trusted   = peerTrusted
    val forwarded = if (trusted) EngineDispatch.parseForwarded(headers) else None
    val clientIp  = forwarded.flatMap(_.clientIp).getOrElse(peer.address)
    // Pre-check before stripping: the common default-deny case (no
    // forwarding headers on the wire) keeps `headers` untouched and allocates
    // nothing; otherwise one single-pass rebuild, not one copy per name.
    val stripped  =
      if (EngineDispatch.hasProxyHeader(headers)) EngineDispatch.stripProxyHeaders(headers)
      else headers
    val enriched  = stripped
      .add(TrustedProxyConfig.PeerAddressHeader, peer.address)
      .add(TrustedProxyConfig.ClientIpHeader, clientIp)
    val withHost  = forwarded.flatMap(_.host) match {
      case Some(host) => enriched.set(Header.Host.name, host)
      case None       => enriched
    }
    EngineDispatch.ResolvedProxy(withHost, forwarded.flatMap(_.proto), forwarded.flatMap(_.host))
  }

  def applyProxyUrl(url: URL, proxy: EngineDispatch.ResolvedProxy): URL = {
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

  private def invokeHandler(route: Route[Ctx], request: Request, vars: Any, scope: Scope): Any =
    try route.handler.handle(request, context, vars, scope)
    catch {
      case throwable: Throwable =>
        try defectHandler.handleDefect(request, throwable)
        catch {
          case _: Throwable => Response.internalServerError
        }
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

  private def nanosToMillis(nanos: Long): Long = nanos / 1000000L
}

private[http] object EngineDispatch {

  private[http] def buildRouteTree[Ctx](routes: Routes[Ctx]): RouteTree[Route[Ctx]] =
    routes.routes.foldLeft(RouteTree.empty[Route[Ctx]]) { (tree, route) =>
      val alternatives = route.pattern.alternatives
      if (alternatives.nonEmpty) tree.add(route.pattern, route)
      else tree.merge(rootRouteTree(route))
    }

  private[http] def rootRouteTree[Ctx](route: Route[Ctx]): RouteTree[Route[Ctx]] = {
    val rootSubtree = SegmentSubtree[Route[Ctx]](Map.empty, ListMap.empty, Some(route))
    RouteTree(Map(route.pattern.method -> rootSubtree))
  }

  /**
   * Forwarding headers stripped before route handlers run. Includes the
   * normalized `x-client-ip` / `x-peer-address` headers themselves so a peer
   * cannot spoof them: they are removed from the wire set and re-added by
   * `resolveProxy` after trust gating.
   */
  private[http] val ProxyHeaderNames: List[String] =
    List("x-forwarded-for", "x-forwarded-proto", "x-forwarded-host", "forwarded", "x-client-ip", "x-peer-address")

  private[http] final case class ForwardedValues(
    clientIp: Option[String],
    proto: Option[String],
    host: Option[String],
  )

  private[http] final case class ResolvedProxy(
    headers: Headers,
    proto: Option[String],
    host: Option[String],
  )

  /**
   * Extracts forwarding values from `X-Forwarded-For/Proto/Host`, falling back
   * to RFC 7239 `Forwarded` when no `X-Forwarded-For` is present. Only the
   * first (leftmost, closest-to-client) element is used. Returns `None` when
   * neither header family is present.
   */
  private[http] def parseForwarded(headers: Headers): Option[ForwardedValues] = {
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

  private[http] def firstListValue(value: String): String = {
    val comma = value.indexOf(',')
    (if (comma < 0) value else value.substring(0, comma)).trim
  }

  /**
   * Normalizes one `for=` / `X-Forwarded-For` element to a bare IP: strips
   * quotes, IPv6 brackets (with optional port), and an IPv4 port suffix.
   */
  private[http] def normalizeForwardedIp(value: String): String = {
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
  private[http] def parseRfc7239Forwarded(value: String): ForwardedValues = {
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
   * rebuild before `resolveProxy` keeps the headers untouched.
   */
  private[http] def hasProxyHeader(headers: Headers): Boolean =
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
  private[http] def stripProxyHeaders(headers: Headers): Headers = {
    val pairs   = headers.toList
    val builder = HeadersBuilder.make(pairs.length)
    var index   = 0
    while (index < pairs.length) {
      val (name, value) = pairs(index)
      if (!ProxyHeaderNames.contains(name)) builder.add(name, value)
      index += 1
    }
    builder.build()
  }
}
