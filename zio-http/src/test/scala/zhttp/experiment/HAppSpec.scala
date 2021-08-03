package zhttp.experiment

import io.netty.handler.codec.http._
import zhttp.experiment.HttpMessage.CompleteResponse
import zhttp.experiment.internal.{HttpMessageAssertion, HttpQueue}
import zhttp.http.Http
import zhttp.service.EventLoopGroup
import zio.Chunk
import zio.duration._
import zio.test.TestAspect._
import zio.test._

/**
 * Be prepared for some real nasty runtime tests.
 */
object HAppSpec extends DefaultRunnableSpec with HttpMessageAssertion {
  val env = EventLoopGroup.auto(1)

  def spec = suite("HApp") {
    testM("simple response") {
      for {
        proxy <- HttpQueue.make {
          // Todo: Use Http.succeed after fixing the issue
          HApp {
            Http.collect[AnyRequest] { case _ =>
              CompleteResponse(content = Chunk.fromArray("Hello!".getBytes()))
            }
          }
        }
        _     <- proxy.offer(new DefaultHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, "/text"))
        res   <- proxy.take
        _ = println(s"res: $res")
      } yield assert(res)(isResponse {
        statusIs(200)
      })

    }
  }.provideCustomLayer(env) @@ timeout(5 second)
}
