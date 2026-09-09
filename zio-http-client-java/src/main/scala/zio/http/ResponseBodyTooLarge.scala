package zio.http

/**
 * Thrown when a response body crosses `ClientConfig.maxResponseBodySize`.
 *
 * Every client leg (one-shot [[H2WireClient]], pooled [[PooledLoomH2Client]],
 * JDK [[JavaH2Client]]) accounts body bytes incrementally and fails fast with
 * this error the moment the cap is exceeded, so a malicious server cannot force
 * unbounded buffering. It carries the configured cap for operability.
 *
 * Deliberately unchecked: the pooled leg streams bodies lazily through
 * `Body.toArray`, which treats a mid-pull [[java.io.IOException]] as truncation
 * and returns collected-so-far silently — a checked cap would fail silently
 * there. As a [[RuntimeException]] the trip surfaces loudly on every leg; catch
 * it explicitly.
 *
 * Control-flow note: [[LoomH2ClientDriver]] never falls back to the JDK
 * HTTP/1.1 leg on this error — the H2 exchange itself succeeded, so a fallback
 * would only re-download the same over-cap body under another protocol and mask
 * the cause.
 */
final case class ResponseBodyTooLarge(maxBytes: Long)
    extends RuntimeException(
      s"HTTP response body exceeded client cap of $maxBytes bytes",
    )
