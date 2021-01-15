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

          testFrame(OpCode.Ping, payload) {
            case (fin, mask, encoded) =>
              assert(encoded.length)(equalTo(6)) &&
                assert(encoded(0) & 0xFF)(equalTo(fin + OpCode.Ping)) &&
                assert(encoded(1) & 0xFF)(equalTo(mask + 4)) &&
                assert(encoded.slice(2, encoded.length))(equalTo(payload))
          }
        },
        testM("encode pong frame") {
          val payload = setPayload("pong")

          testFrame(OpCode.Pong, payload) {
            case (fin, mask, encoded) =>
              assert(encoded.length)(equalTo(6)) &&
                assert(encoded(0) & 0xFF)(equalTo(fin + OpCode.Pong)) &&
                assert(encoded(1) & 0xFF)(equalTo(mask + 4)) &&
                assert(encoded.slice(2, encoded.length))(equalTo(payload))
          }
        },
        testM("encode text frame") {
          val payload = setPayload("0123456789")

          testFrame(OpCode.Text, payload) {
            case (fin, mask, encoded) =>
              assert(encoded.length)(equalTo(12)) &&
                assert(encoded(0) & 0xFF)(equalTo(fin + OpCode.Text)) &&
                assert(encoded(1) & 0xFF)(equalTo(mask + 0x0A)) &&
                assert(encoded.slice(2, encoded.length))(equalTo(payload))
          }
        },
        testM("encode binary frame") {
          nextBytes(125).flatMap(
            payload =>
              testFrame(OpCode.Binary, payload) {
                case (fin, mask, encoded) =>
                  assert(encoded.length)(equalTo(127)) &&
                    assert(encoded(0) & 0xFF)(equalTo(fin + OpCode.Binary)) &&
                    assert(encoded(1) & 0xFF)(equalTo(mask + 0x7D)) &&
                    assert(encoded.slice(2, encoded.length))(equalTo(payload))
              }
          )
        },
        testM("encode continuation frame") {
          nextBytes(124).flatMap(
            payload =>
              testFrame(OpCode.Continuation, payload) {
                case (fin, mask, encoded) =>
                  assert(encoded.length)(equalTo(126)) &&
                    assert(encoded(0) & 0xFF)(equalTo(fin + OpCode.Continuation)) &&
                    assert(encoded(1) & 0xFF)(equalTo(mask + 0x7C)) &&
                    assert(encoded.slice(2, encoded.length))(equalTo(payload))
              }
          )
        }
      ),
      suite("frame lengths")(
        testM("a frame with the length greater than 125 and less than 65536 in bytes") {
          nextBytes(128).flatMap(
            payload =>
              testFrame(OpCode.Continuation, payload) {
                case (_, _, encoded) =>
                  assert((encoded(2) << 8) + (encoded(3) & 0xFF))(equalTo(0x80))
              }
          )
        },
        testM("a frame with the length greater than 65536 in bytes") {
          nextBytes(66666).flatMap(
            payload =>
              testFrame(OpCode.Continuation, payload) {
                case (_, _, encoded) =>
                  assert(encoded.length)(equalTo(66676)) &&
                    assert(
                      (encoded(2) << 56) +
                        (encoded(3) << 48) +
                        (encoded(4) << 40) +
                        (encoded(5) << 32) +
                        (encoded(6) << 24) +
                        (encoded(7) << 16) +
                        (encoded(8) << 8) +
                        (encoded(9) & 0xFF)
                    )(equalTo(0x1046A))
              }
          )
        }
      ),
      suite("misc")(
        testM("a frame with masked payload") {
          val payload = setPayload("hello websockets")

          testFrame(OpCode.Continuation, payload, masked = true) {
            case (_, mask, encoded) =>
              assert(encoded.length)(equalTo(22)) &&
                assert(encoded(1) & 0xFF)(equalTo(mask + 0x10)) &&
                assert(encoded.slice(2, 6))(equalTo(maskingKey)) &&
                assert(unmaskData(encoded.slice(6, encoded.length)))(equalTo(payload))
          }
        },
        testM("a frame that is not the last one") {
          nextBytes(34).flatMap(
            payload =>
              testFrame(OpCode.Continuation, payload, last = false) {
                case (_, _, encoded) =>
                  assert(encoded(0) & 0xFF)(equalTo(0x00 + OpCode.Continuation))
              }
          )
        }
      )
    ).provideCustomLayer(FrameEncoder.live)

  val maskingKey: Chunk[Byte] = Chunk[Int](0xDE, 0xAD, 0xBE, 0xEF).map(_.toByte)

  def testFrame(opcode: Int, payload: Chunk[Byte], last: Boolean = true, masked: Boolean = false)(
    assert: (Int, Int, Chunk[Byte]) => TestResult
  ) = {
    val fin  = if (last) 0x80 else 0x00
    val mask = if (masked) 0x80 else 0x00

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
    } yield assert(fin, mask, encoded)
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
