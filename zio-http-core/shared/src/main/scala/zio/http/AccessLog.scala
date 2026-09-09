/*
 * Copyright 2026 the ZIO HTTP contributors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package zio.http

import scala.util.control.NonFatal

/**
 * Composable access/audit logging with a pluggable sink (v4-native).
 *
 * Every request that reaches a route produces one [[AccessLogRecord]] carrying
 * method, path, the matched route, status, duration, and request-id, plus the
 * proxy-trust decision (G3 headers) and the slow-stream deadline outcome (G2
 * headers) when the transport attached them. Records are metadata only: the
 * case class has no body, header-map, or stream handle, so body bytes cannot
 * reach the sink by construction.
 *
 * Middleware-only by design: transports emit nothing by default (no console
 * noise, no per-request record). Custom sinks (capture, file, structured audit)
 * attach opt-in via [[Middleware.accessLog]]; the transport's internal
 * OpenTelemetry span (`H2Transport.instrumentRequest`) is a separate surface
 * and is never touched by this middleware.
 *
 * A throwing sink never fails the request: [[AccessLog.emit]] (used by every
 * built-in emitter) swallows sink failures.
 */
final case class AccessLogRecord(
  method: String,
  /**
   * Request path, logged verbatim (standard access-log behavior: no scrub
   * hook).
   */
  path: String,
  /** Matched route pattern, if the request reached a route. */
  route: Option[String],
  status: Int,
  /**
   * Handler wall-clock time in milliseconds: measured around the route handler
   * only, so response-body send time is excluded.
   */
  durationMs: Long,
  /**
   * Client-supplied `x-request-id` sanitized by [[AccessLog.sanitizeRequestId]]
   * (restricted charset, truncated to [[AccessLog.MaxRequestIdLength]]), or a
   * generated UUID when absent or empty after sanitizing.
   */
  requestId: String,
  /** Socket peer address (`x-peer-address`, G3 trust gate). */
  peerAddress: Option[String],
  /** Resolved client IP (`x-client-ip`, G3 trust gate). */
  clientIp: Option[String],
  /**
   * Whether proxy forwarding headers were honored for this request:
   * [[AccessLog.TrustTrusted]] when the resolved client IP differs from the
   * peer address (forwarding applied), [[AccessLog.TrustUntrusted]] when it
   * falls back to the peer (ignored or absent). `None` when neither address is
   * known (non-H2 transports).
   */
  trustDecision: Option[String],
  /**
   * Slow-stream deadline outcome (G2): [[AccessLog.DeadlineOk]] — the only
   * outcome any built-in emitter reports. [[AccessLog.DeadlineHeaderTimeout]] /
   * [[AccessLog.DeadlineBodyTimeout]] are reserved for future transport-side
   * timeout reporting and are never emitted today; streams reset by a deadline
   * yield no record at all.
   */
  deadlineOutcome: Option[String],
  /**
   * Request version string (same vocab as the transport and the middleware).
   */
  protocol: String,
)

/**
 * Pluggable receiver for [[AccessLogRecord]]s.
 *
 * Implementations run on the server I/O path, so `log` should be fast and
 * non-blocking; it receives metadata only and must never retain request bodies
 * (it cannot: the record carries none). Throwing is tolerated but pointless:
 * built-in emitters route through [[AccessLog.emit]], which swallows sink
 * failures so logging can never fail a request.
 */
trait AccessLogSink {

  /** Receives one metadata-only record per served request. */
  def log(record: AccessLogRecord): Unit
}

object AccessLogSink {

  /** Discards every record. */
  val disabled: AccessLogSink =
    new AccessLogSink {
      def log(record: AccessLogRecord): Unit = ()
    }

  /** Writes one [[AccessLog.formatLine]] per record to standard out. */
  val console: AccessLogSink =
    new AccessLogSink {
      def log(record: AccessLogRecord): Unit = Console.out.println(AccessLog.formatLine(record))
    }

  /** Builds a sink from a plain function (e.g. test capture, file append). */
  def apply(f: AccessLogRecord => Unit): AccessLogSink =
    new AccessLogSink {
      def log(record: AccessLogRecord): Unit = f(record)
    }
}

object AccessLog {

  /** Request-id header honored (and echoed into the record) per request. */
  val RequestIdHeader: String = "x-request-id"

  /**
   * Maximum length of a sanitized request id: longer client-supplied values are
   * truncated to bound log cardinality.
   */
  val MaxRequestIdLength: Int = 128

  /**
   * Socket peer-address header. Mirrors `TrustedProxyConfig.PeerAddressHeader`
   * (zio-http-server); the literal is repeated here so core middleware stays
   * dependency-free.
   */
  val PeerAddressHeader: String = "x-peer-address"

  /**
   * Resolved client-IP header. Mirrors `TrustedProxyConfig.ClientIpHeader`
   * (zio-http-server); see [[PeerAddressHeader]].
   */
  val ClientIpHeader: String = "x-client-ip"

  /** [[AccessLogRecord.trustDecision]] when forwarding was honored. */
  val TrustTrusted: String = "trusted"

  /** [[AccessLogRecord.trustDecision]] when the peer address was kept. */
  val TrustUntrusted: String = "untrusted"

  /** [[AccessLogRecord.deadlineOutcome]] when the request completed in time. */
  val DeadlineOk: String = "ok"

  /** [[AccessLogRecord.deadlineOutcome]] when header completion timed out. */
  val DeadlineHeaderTimeout: String = "header-timeout"

  /** [[AccessLogRecord.deadlineOutcome]] when body completion timed out. */
  val DeadlineBodyTimeout: String = "body-timeout"

  /**
   * Resolves the request id: the client-supplied `x-request-id` header when
   * present and non-empty, otherwise a freshly generated UUID.
   *
   * The header value is sanitized before it reaches the record (see
   * [[sanitizeRequestId]]): only alphanumerics plus `-._~` survive, the result
   * is truncated to [[MaxRequestIdLength]] chars, and a value that is empty
   * after sanitizing yields a generated UUID — so newline/ANSI log injection
   * and cardinality bombs cannot pass through.
   */
  def requestId(headers: Headers): String =
    headers
      .rawGet(RequestIdHeader)
      .filter(_.nonEmpty)
      .map(sanitizeRequestId)
      .getOrElse(java.util.UUID.randomUUID().toString)

  /**
   * Sanitizes a client-supplied request id for safe logging: strips every
   * character outside `[A-Za-z0-9-._~]`, truncates to [[MaxRequestIdLength]]
   * characters, and returns a generated UUID when nothing survives.
   */
  def sanitizeRequestId(raw: String): String = {
    val kept = new StringBuilder()
    var i    = 0
    while (i < raw.length && kept.length < MaxRequestIdLength) {
      val c = raw.charAt(i)
      if (
        (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9') ||
        c == '-' || c == '.' || c == '_' || c == '~'
      ) kept.append(c)
      i += 1
    }
    if (kept.isEmpty) java.util.UUID.randomUUID().toString else kept.toString
  }

  /**
   * Derives the trust decision from the addresses the transport attached:
   * forwarding was honored exactly when the resolved client IP differs from the
   * socket peer. `None` when either address is unknown.
   */
  def trustDecision(peerAddress: Option[String], clientIp: Option[String]): Option[String] =
    (peerAddress, clientIp) match {
      case (Some(peer), Some(client)) => Some(if (client != peer) TrustTrusted else TrustUntrusted)
      case _                          => None
    }

  /**
   * Delivers `record` to `sink`, swallowing any sink failure so logging can
   * never fail the request being served.
   */
  def emit(sink: AccessLogSink, record: AccessLogRecord): Unit =
    try sink.log(record)
    catch {
      case NonFatal(_) => ()
    }

  /** Renders one stable, greppable log line for [[AccessLogSink.console]]. */
  def formatLine(record: AccessLogRecord): String = {
    def show(value: Option[String]): String = value.getOrElse("-")
    record.method + " " + record.path + " " + record.status + " " + record.durationMs + "ms" +
      " id=" + record.requestId +
      " peer=" + show(record.peerAddress) +
      " client=" + show(record.clientIp) +
      " trust=" + show(record.trustDecision) +
      " deadline=" + show(record.deadlineOutcome) +
      " route=" + show(record.route) +
      " proto=" + record.protocol
  }
}
