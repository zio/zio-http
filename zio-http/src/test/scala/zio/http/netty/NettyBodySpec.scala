package zio.http.netty

import zio.test.Assertion.equalTo
import zio.test._
import zio.{Chunk, Scope}

import zio.http.Body
import zio.http.model.HTTP_CHARSET

import io.netty.channel.embedded.EmbeddedChannel
import io.netty.util.AsciiString

object NettyBodySpec extends ZIOSpecDefault {
  override def spec: Spec[TestEnvironment with Scope, Any] =
    suite("NettyBody")(
      suite("fromAsync")(
        test("success") {
          val ctx     = new EmbeddedChannel()
          val message = Chunk.fromArray("Hello World".getBytes(HTTP_CHARSET))
          val chunk   = NettyBody.fromAsync(async => async(ctx, message, isLast = true)).asChunk
          assertZIO(chunk)(equalTo(message))
        },
        test("fail") {
          val exception = new RuntimeException("Some Error")
          val error     = NettyBody.fromAsync(_ => throw exception).asChunk.flip
          assertZIO(error)(equalTo(exception))
        },
      ),
      test("FromASCIIString: toHttp") {
        check(Gen.asciiString) { payload =>
          val res = NettyBody.fromAsciiString(AsciiString.cached(payload)).asString(HTTP_CHARSET)
          assertZIO(res)(equalTo(payload))
        }
      },
    )
}
