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

          testFrame(PING, payload) {
            case (fin, mask, encoded) =>
              assert(encoded.length)(equalTo(6)) &&
                assert(encoded(0) & 0xFF)(equalTo(fin + PING)) &&
                assert(encoded(1) & 0xFF)(equalTo(mask + 4)) &&
                assert(encoded.slice(2, encoded.length))(equalTo(payload))
          }
        },
        testM("encode pong frame") {
          val payload = setPayload("pong")

          testFrame(PONG, payload) {
            case (fin, mask, encoded) =>
              assert(encoded.length)(equalTo(6)) &&
                assert(encoded(0) & 0xFF)(equalTo(fin + PONG)) &&
                assert(encoded(1) & 0xFF)(equalTo(mask + 4)) &&
                assert(encoded.slice(2, encoded.length))(equalTo(payload))
          }
        },
        testM("encode text frame") {
          val payload = setPayload("0123456789")

          testFrame(TEXT, payload) {
            case (fin, mask, encoded) =>
              assert(encoded.length)(equalTo(12)) &&
                assert(encoded(0) & 0xFF)(equalTo(fin + TEXT)) &&
                assert(encoded(1) & 0xFF)(equalTo(mask + 0x0A)) &&
                assert(encoded.slice(2, encoded.length))(equalTo(payload))
          }
        },
        testM("encode binary frame") {
          nextBytes(125).flatMap(
            payload =>
              testFrame(BINARY, payload) {
                case (fin, mask, encoded) =>
                  assert(encoded.length)(equalTo(127)) &&
                    assert(encoded(0) & 0xFF)(equalTo(fin + BINARY)) &&
                    assert(encoded(1) & 0xFF)(equalTo(mask + 0x7D)) &&
                    assert(encoded.slice(2, encoded.length))(equalTo(payload))
              }
          )
        },
        testM("encode continuation frame") {
          nextBytes(124).flatMap(
            payload =>
              testFrame(CONTINUATION, payload) {
                case (fin, mask, encoded) =>
                  assert(encoded.length)(equalTo(126)) &&
                    assert(encoded(0) & 0xFF)(equalTo(fin + CONTINUATION)) &&
                    assert(encoded(1) & 0xFF)(equalTo(mask + 0x7C)) &&
                    assert(encoded.slice(2, encoded.length))(equalTo(payload))
              }
          )
        }
      ),
      suite("frame sizes")(
        testM("a frame with the size greater than 125 and less than 65536 bytes") {
          nextBytes(128).flatMap(
            payload =>
              testFrame(CONTINUATION, payload) {
                case (fin, mask, encoded) =>
                  assert(encoded.length)(equalTo(132)) &&
                    assert(encoded(0) & 0xFF)(equalTo(fin + CONTINUATION)) &&
                    assert(encoded(1) & 0xFF)(equalTo(mask + 0x7E)) &&
                    assert((encoded(2) << 8) + (encoded(3) & 0xFF))(equalTo(0x80)) &&
                    assert(encoded.slice(4, encoded.length))(equalTo(payload))
              }
          )
        },
        testM("a frame with the size greater than 65536 bytes") {
          nextBytes(66666).flatMap(
            payload =>
              testFrame(CONTINUATION, payload) {
                case (fin, mask, encoded) =>
                  assert(encoded.length)(equalTo(66676)) &&
                    assert(encoded(0) & 0xFF)(equalTo(fin + CONTINUATION)) &&
                    assert(encoded(1) & 0xFF)(equalTo(mask + 0x7F)) &&
                    assert(
                      (encoded(2) << 56) +
                        (encoded(3) << 48) +
                        (encoded(4) << 40) +
                        (encoded(5) << 32) +
                        (encoded(6) << 24) +
                        (encoded(7) << 16) +
                        (encoded(8) << 8) +
                        (encoded(9) & 0xFF)
                    )(equalTo(0x1046A)) &&
                    assert(encoded.slice(10, encoded.length))(equalTo(payload))
              }
          )
        }
      ),
      suite("misc")(
        testM("a frame with masked payload") {
          val payload = setPayload("hello websockets")

          testFrame(CONTINUATION, payload, masked = true) {
            case (fin, mask, encoded) =>
              assert(encoded.length)(equalTo(22)) &&
                assert(encoded(0) & 0xFF)(equalTo(fin + CONTINUATION)) &&
                assert(encoded(1) & 0xFF)(equalTo(mask + 0x10)) &&
                assert(encoded.slice(2, 6))(equalTo(maskingKey)) &&
                assert(unmaskData(encoded.slice(6, encoded.length)))(equalTo(payload))
          }
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
        case CONTINUATION => MessageFrame.continuation(payload, last)
        case TEXT         => MessageFrame.text(new String(payload.toArray, "UTF-8"), last)
        case BINARY       => MessageFrame.binary(payload, last)
        case CLOSE        => MessageFrame.close(CloseCode.NormalClosure, new String(payload.toArray, "UTF-8"))
        case PING         => MessageFrame.ping(payload)
        case PONG         => MessageFrame.pong(payload)
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
