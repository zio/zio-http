package zio.http.datastar

import zio._
import zio.stream.ZStream
import zio.http.{Server, ServerSentEvent}
import zio.http.endpoint._

object EndpointExtensions {
  implicit class DatastarEndpointExtensions[I, E, O, Auth <: AuthType](endpoint: Endpoint[I, E, ZNothing, O, Auth]) {
    /** Converts a ZStream[String] into a ZStream[ServerSentEvent] for SSE responses */
    def toServerSentEvents(stream: ZStream[Any, Nothing, String]): ZStream[Any, Nothing, ServerSentEvent[String]] =
      stream.map(data => ServerSentEvent(data))

    /** Returns a single DatastarEvent in the endpoint response */
    def toDatastarEvent(event: DatastarEvent): ZStream[Any, Nothing, ServerSentEvent[String]] =
      ZStream.succeed(event.toServerSentEvent)

    /** Returns a ZStream of DatastarEvent in the endpoint response */
    def toDatastarEventStream(events: ZStream[Any, Nothing, DatastarEvent]): ZStream[Any, Nothing, ServerSentEvent[String]] =
      events.map(_.toServerSentEvent)
  }

  /**
   * Example usage:
   *
   * {{{
   * import zio.http.datastar.EndpointExtensions._
   *
   * // Example 1: Converting string stream to SSE
   * val stringStream: ZStream[Any, Nothing, String] = ZStream("message1", "message2")
   * val endpoint1 = Endpoint.get("/sse")
   *   .out[ZStream[Any, Nothing, ServerSentEvent]](
   *     endpoint1.toServerSentEvents(stringStream)
   *   )
   *
   * // Example 2: Single DatastarEvent
   * val singleEvent = DatastarEvent.SetHTML("#counter", "<span>1</span>")
   * val endpoint2 = Endpoint.get("/single-event")
   *   .out[ZStream[Any, Nothing, ServerSentEvent]](
   *     endpoint2.toDatastarEvent(singleEvent)
   *   )
   *
   * // Example 3: Stream of DatastarEvents
   * val eventStream: ZStream[Any, Nothing, DatastarEvent] = 
   *   ZStream(
   *     DatastarEvent.SetHTML("#counter", "<span>1</span>"),
   *     DatastarEvent.SetHTML("#counter", "<span>2</span>")
   *   )
   * val endpoint3 = Endpoint.get("/event-stream")
   *   .out[ZStream[Any, Nothing, ServerSentEvent]](
   *     endpoint3.toDatastarEventStream(eventStream)
   *   )
   * }}}
   */
}