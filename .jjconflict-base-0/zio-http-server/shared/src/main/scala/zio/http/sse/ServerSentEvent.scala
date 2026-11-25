package zio.http.sse

import scala.concurrent.duration.Duration

/**
 * One Server-Sent Events message (WHATWG HTML, server-sent events).
 *
 * This is the native `text/event-stream` model: plain `String` data framed by
 * [[SseCodec]] without materializing the event stream. It is intentionally
 * distinct from the schema-driven `zio.http.ServerSentEvent[T]` that ships in
 * the `http-model` dependency.
 *
 * @param data
 *   event payload; split on CR/LF/CRLF into one `data:` line each
 * @param event
 *   optional event-type line
 * @param id
 *   optional last-event-id line
 * @param retry
 *   optional reconnection delay, rendered in milliseconds per spec
 */
final case class ServerSentEvent(
  data: String,
  event: Option[String] = None,
  id: Option[String] = None,
  retry: Option[Duration] = None,
) {
  // `event:`/`id:` are single-line SSE fields: a CR/LF in the value would
  // inject framing (SseCodec renders them verbatim). Fail fast at
  // construction — the encode hot path stays validation-free. Multiline
  // `data` is legitimate framing and is split per data line instead.
  event.foreach { name =>
    require(
      !name.exists(c => c == '\r' || c == '\n'),
      "event must not contain CR or LF (single-line SSE field)",
    )
  }
  id.foreach { value =>
    require(
      !value.exists(c => c == '\r' || c == '\n'),
      "id must not contain CR or LF (single-line SSE field)",
    )
  }
}
