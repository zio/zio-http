package zhttp.service

import zhttp.http._
import zhttp.service.server.ServerChannelFactory
import zio._
import zio.clock.Clock
import zio.duration._
import zio.stream.ZStream
import zio.test.TestAspect.timeout
import zio.test._

/**
 * See https://github.com/dream11/zio-http/issues/1229
 */
object ServerIssue1229Spec extends DefaultRunnableSpec {
  private def callWithContent(content: HttpData): Task[Response] = {
    val dummyRoute = Http.collect[Request] { case req @ Method.POST -> !! / "echo" =>
      Response(status = Status.Ok, data = req.data)
    }

    val layer: ZLayer[ServerChannelFactory & EventLoopGroup, Nothing, Has[Server.Start]] =
      Server(dummyRoute).withBinding("localhost", 0).make.orDie.toLayer

    val env: ULayer[Has[Server.Start] & EventLoopGroup & ChannelFactory] =
      (EventLoopGroup.auto(0) ++ ServerChannelFactory.auto ++ ChannelFactory.auto) >+> layer

    val action = for {
      port <- ZIO.service[Server.Start].map(_.port)
      resp <- Client.request(
        s"http://localhost:$port/echo",
        method = Method.POST,
        content = content,
      )
    } yield resp

    action.provideLayer(env)
  }

  def spec = suite("ServerIssue1229Spec")(
    testM("fromString") {
      for {
        resp           <- callWithContent(HttpData.fromString("echo string"))
        responseString <- resp.bodyAsString
      } yield assertTrue(resp.status.isSuccess, responseString == "echo string")
    },
    testM("empty") {
      for {
        resp           <- callWithContent(HttpData.empty)
        responseString <- resp.bodyAsString
      } yield assertTrue(resp.status.isSuccess, responseString == "")
    },
    testM("fromStream") {
      val stream =
        ZStream.repeatEffectChunk(ZIO.sleep(10.millis).provideLayer(Clock.live).as(Chunk[String]("A"))).take(10)
      for {
        resp           <- callWithContent(HttpData.fromStream(stream))
        responseString <- resp.bodyAsString
      } yield assertTrue(resp.status.isSuccess, responseString == "AAAAAAAAAA")
    },
  ) @@ timeout(5.seconds)
}
