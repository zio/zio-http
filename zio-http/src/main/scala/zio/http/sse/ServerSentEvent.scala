package zio.http.sse

/**
 * Server-Sent Event (SSE) as defined by
 * https://html.spec.whatwg.org/multipage/server-sent-events.html#server-sent-events
 * @param data
 *   data, may span multiple lines
 * @param eventType
 *   optional type, must not contain \n or \r
 * @param id
 *   optional id, must not contain \n or \r
 * @param retry
 *   optional reconnection delay in milliseconds
 */
final case class ServerSentEvent(
  data: String,
  eventType: Option[String] = None,
  id: Option[String] = None,
  retry: Option[Int] = None,
) {

  def encode: String = {
    val sb = new StringBuilder
    data.linesIterator.foreach { line =>
      sb.append("data: ").append(line).append('\n')
    }
    eventType.foreach { et =>
      sb.append("event: ").append(et.linesIterator.mkString(" ")).append('\n')
    }
    id.foreach { i =>
      sb.append("id: ").append(i.linesIterator.mkString(" ")).append('\n')
    }
    retry.foreach { r =>
      sb.append("retry: ").append(r).append('\n')
    }
    sb.append('\n').toString
  }
}

object ServerSentEvent {
  def apply(data: String): ServerSentEvent = new ServerSentEvent(data)

  def heartbeat(): ServerSentEvent = new ServerSentEvent("")
}

// Source
//          .tick(2.seconds, 2.seconds, NotUsed)
//          .map(_ => LocalTime.now())
//          .map(time => ServerSentEvent(ISO_LOCAL_TIME.format(time)))
//          .keepAlive(1.second, () => ServerSentEvent.heartbeat)
//      }
