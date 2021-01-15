package zio.web.websockets.internal

import zio.Chunk
import zio.random._
import zio.test._
import zio.test.Assertion._
import zio.test.environment.TestRandom

object FrameEncoderSpec extends DefaultRunnableSpec {

  override def spec =
    suite("FrameEncoderSpec")(
      suite("frames")(
        testM("encode ping frame") {
          val payload = setPayload("ping")

          testFrame(OpCode.Ping, payload) { frame =>
              assert(frame.length)(equalTo(6)) &&
                assert(frame(0) & 0xFF)(equalTo(0x80 + OpCode.Ping)) &&
                assert(frame(1) & 0xFF)(equalTo(0x00 + 0x04)) &&
                assert(frame.slice(2, frame.length))(equalTo(payload))
          }
        },
        testM("encode pong frame") {
          val payload = setPayload("pong")

          testFrame(OpCode.Pong, payload) { frame =>
              assert(frame.length)(equalTo(6)) &&
                assert(frame(0) & 0xFF)(equalTo(0x80 + OpCode.Pong)) &&
                assert(frame(1) & 0xFF)(equalTo(0x00 + 0x04)) &&
                assert(frame.slice(2, frame.length))(equalTo(payload))
          }
        },
        testM("encode text frame") {
          val payload = setPayload("0123456789")

          testFrame(OpCode.Text, payload) { frame =>
              assert(frame.length)(equalTo(12)) &&
                assert(frame(0) & 0xFF)(equalTo(0x80 + OpCode.Text)) &&
                assert(frame(1) & 0xFF)(equalTo(0x00 + 0x0A)) &&
                assert(frame.slice(2, frame.length))(equalTo(payload))
          }
        },
        testM("encode binary frame") {
          nextBytes(125).flatMap(
            payload =>
              testFrame(OpCode.Binary, payload) { frame =>
                  assert(frame.length)(equalTo(127)) &&
                    assert(frame(0) & 0xFF)(equalTo(0x80 + OpCode.Binary)) &&
                    assert(frame(1) & 0xFF)(equalTo(0x00 + 0x7D)) &&
                    assert(frame.slice(2, frame.length))(equalTo(payload))
              }
          )
        },
        testM("encode continuation frame") {
          nextBytes(124).flatMap(
            payload =>
              testFrame(OpCode.Continuation, payload) { frame =>
                  assert(frame.length)(equalTo(126)) &&
                    assert(frame(0) & 0xFF)(equalTo(0x80 + OpCode.Continuation)) &&
                    assert(frame(1) & 0xFF)(equalTo(0x00 + 0x7C)) &&
                    assert(frame.slice(2, frame.length))(equalTo(payload))
              }
          )
        }
      ),
      suite("frame lengths")(
        testM("a frame with the length greater than 125 and less than 65536 in bytes") {
          nextBytes(128).flatMap(
            payload =>
              testFrame(OpCode.Continuation, payload) { frame =>
                  assert((frame(2) << 8) + (frame(3) & 0xFF))(equalTo(0x80))
              }
          )
        },
        testM("a frame with the length greater than 65536 in bytes") {
          nextBytes(66666).flatMap(
            payload =>
              testFrame(OpCode.Continuation, payload) { frame =>
                  assert(frame.length)(equalTo(66676)) &&
                    assert(
                      (frame(2) << 56) +
                        (frame(3) << 48) +
                        (frame(4) << 40) +
                        (frame(5) << 32) +
                        (frame(6) << 24) +
                        (frame(7) << 16) +
                        (frame(8) << 8) +
                        (frame(9) & 0xFF)
                    )(equalTo(0x1046A))
              }
          )
        }
      ),
      suite("misc")(
        testM("a frame with masked payload") {
          val payload = setPayload("hello websockets")

          testFrame(OpCode.Continuation, payload, masked = true) { frame =>
              assert(frame.length)(equalTo(22)) &&
                assert(frame(1) & 0xFF)(equalTo(0x80 + 0x10)) &&
                assert(frame.slice(2, 6))(equalTo(maskingKey)) &&
                assert(unmaskData(frame.slice(6, frame.length)))(equalTo(payload))
          }
        },
        testM("a frame that is not the last one") {
          nextBytes(34).flatMap(
            payload =>
              testFrame(OpCode.Continuation, payload, last = false) { frame =>
                  assert(frame(0) & 0xFF)(equalTo(0x00 + OpCode.Continuation))
              }
          )
        }
      )
    ).provideCustomLayer(FrameEncoder.live)

  val maskingKey: Chunk[Byte] = Chunk[Int](0xDE, 0xAD, 0xBE, 0xEF).map(_.toByte)

  def testFrame(opcode: Int, payload: Chunk[Byte], last: Boolean = true, masked: Boolean = false)(
    assert: Chunk[Byte] => TestResult
  ) = {
    for {
      _ <- TestRandom.feedBytes(maskingKey).when(masked)
      frame = opcode match {
        case OpCode.Continuation => MessageFrame.continuation(payload, last)
        case OpCode.Text         => MessageFrame.text(new String(payload.toArray, "UTF-8"), last)
        case OpCode.Binary       => MessageFrame.binary(payload, last)
        case OpCode.Close        => MessageFrame.close(CloseCode.NormalClosure, new String(payload.toArray, "UTF-8"))
        case OpCode.Ping         => MessageFrame.ping(payload)
        case OpCode.Pong         => MessageFrame.pong(payload)
      }
      encoded <- FrameEncoder.encode(frame, masked).map(Chunk.fromByteBuffer)
    } yield assert(encoded)
  }

  private def unmaskData(data: Chunk[Byte]) =
    data
      .mapAccum(0) {
        case (idx, byte) =>
          (idx + 1, (byte ^ maskingKey(idx % 4)).toByte)
      }
      ._2

  private def setPayload(str: String) =
    Chunk.fromArray(str.getBytes("UTF-8"))
}
