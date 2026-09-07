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
)
