package zhttp.service

import zhttp.http._
import zhttp.internal.{DynamicServer, HttpRunnableSpec, WebSocketQueue}
import zhttp.service.server._
import zhttp.socket.{Socket, WebSocketFrame}
import zio.duration.durationInt
import zio.stream.ZStream
import zio.test.Assertion._
import zio.test.TestAspect._
import zio.test._
import zio.{Chunk, ZIO}

object ClientSpec extends HttpRunnableSpec {

  private val env =
    EventLoopGroup.nio() ++ ChannelFactory.nio ++ ServerChannelFactory.nio ++ DynamicServer.live ++ WebSocketQueue.live

  def clientSpec = suite("ClientSpec") {
    testM("respond Ok") {
      val app = Http.ok.requestStatus()
      assertM(app)(equalTo(Status.OK))
    } +
      testM("non empty content") {
        val app             = Http.text("abc")
        val responseContent = app.requestBody()
        assertM(responseContent)(isNonEmpty)
      } +
      testM("echo POST request content") {
        val app = Http.collectZIO[Request] { case req => req.getBodyAsString.map(Response.text(_)) }
        val res = app.requestBodyAsString(method = Method.POST, content = "ZIO user")
        assertM(res)(equalTo("ZIO user"))
      } +
      testM("empty content") {
        val app             = Http.empty
        val responseContent = app.requestBody()
        assertM(responseContent)(isEmpty)
      } +
      testM("text content") {
        val app             = Http.text("zio user does not exist")
        val responseContent = app.requestBodyAsString()
        assertM(responseContent)(containsString("user"))
      }
  }

  def websocketClientSpec = suite("WebSocketClientSpec") {
    testM("foo bar baz") {
      val app = Http.fromEffect {
        Socket
          .collect[WebSocketFrame] {
            case WebSocketFrame.Text("FOO") => ZStream.succeed(WebSocketFrame.text("BAR"))
            case WebSocketFrame.Text("BAR") =>
              ZStream.succeed(WebSocketFrame.text("BAZ")) ++ ZStream.succeed(WebSocketFrame.close(1000))
          }
          .toResponse
      }

      val client = Socket
        .collect[WebSocketFrame] { case frame: WebSocketFrame =>
          ZStream.fromEffect(WebSocketQueue.offer(frame).as(frame))
        }
        .toSocketApp
        .onOpen(Socket.succeed(WebSocketFrame.Text("FOO")))
        .onClose(_ => WebSocketQueue.shutdown)
        .onError(thr => ZIO.die(thr))

      val actual = for {
        _     <- app.webSocketRequest(path = !! / "subscriptions", ss = client)
        queue <- WebSocketQueue.queue
        chunk <- ZStream.fromQueue(queue).runCollect
      } yield chunk

      assertM(actual)(equalTo(Chunk(WebSocketFrame.text("BAR"), WebSocketFrame.Text("BAZ"))))
    }
  }

  override def spec = {
    suiteM("Client") {
      serve(DynamicServer.app).as(List(clientSpec, websocketClientSpec)).useNow
    }.provideCustomLayerShared(env) @@ timeout(5 seconds) @@ sequential
  }
}
