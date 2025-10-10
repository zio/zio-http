package zio.http.datastar

import zio._
import zio.test._
import zio.stream.ZStream
import zio.http.{Method, Server, ServerSentEvent}
import zio.http.endpoint._

object EndpointExtensionsSpec extends ZIOSpecDefault {
  import EndpointExtensions._

  def spec = suite("EndpointExtensionsSpec")(
    test("toServerSentEvents should convert string stream to SSE") {
      val endpoint = Endpoint(Method.GET / "test")
      val stringStream = ZStream("message1", "message2")
      val result = endpoint.toServerSentEvents(stringStream)

      for {
        events <- result.runCollect
      } yield assertTrue(
        events.size == 2,
        events(0).data == "message1",
        events(1).data == "message2"
      )
    },

    test("toDatastarEvent should convert single event to SSE") {
      val endpoint = Endpoint(Method.GET / "test")
      val event = DatastarEvent.SetHTML("#counter", "<span>1</span>")
      val result = endpoint.toDatastarEvent(event)

      for {
        events <- result.runCollect
      } yield assertTrue(
        events.size == 1,
        events.head.data.nonEmpty
      )
    },

    test("toDatastarEventStream should convert stream of events to SSE") {
      val endpoint = Endpoint(Method.GET / "test")
      val events = ZStream(
        DatastarEvent.SetHTML("#counter", "<span>1</span>"),
        DatastarEvent.SetHTML("#counter", "<span>2</span>")
      )
      val result = endpoint.toDatastarEventStream(events)

      for {
        sseEvents <- result.runCollect
      } yield assertTrue(
        sseEvents.size == 2,
        sseEvents.forall(_.data.nonEmpty)
      )
    }
  )
}