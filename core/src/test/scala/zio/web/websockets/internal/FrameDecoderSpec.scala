package zio.web.websockets.internal

import zio.Chunk
import zio.random._
import zio.test.Assertion._
import zio.test._

object FrameDecoderSpec extends DefaultRunnableSpec {
  override def spec =
    suite("FrameDecoderSpec")(
      suite("frames")(
        testM("decode a ping frame") {
          testFrame(OpCode.Ping)
        },
        testM("decode a pong frame") {
          testFrame(OpCode.Pong)
        },
        testM("decode a text frame") {
          testFrame(OpCode.Text)
        },
        testM("decode a binary frame") {
          testFrame(OpCode.Binary)
        },
        testM("decode a continuation frame") {
          testFrame(OpCode.Continuation)
        },
        testM("decode a close frame") {
          val code   = CloseCode.InvalidFrame
          val reason = "couldn't recognise the received frame"

          val chunk =
            Chunk((0x80 + OpCode.Close).toByte, 39.toByte) ++
              code.toBinary ++
              Chunk.fromArray(reason.getBytes("UTF-8"))

          FrameDecoder.decode(chunk, masked = false).map { frame =>
            assert(frame)(equalTo(MessageFrame.close(code, reason)))
          }
        }
      ),
      suite("frame lengths")(
        testM("a frame with the length greater than 125 and less than 65536 in bytes") {
          nextBytes(128).flatMap { payload =>
            val chunk =
              Chunk(
                (0x80 + OpCode.Binary).toByte,
                0x7E.toByte,
                (128 >> 8).toByte,
                (128 & 0xFF).toByte
              ) ++ payload

            FrameDecoder.decode(chunk, masked = false).map { frame =>
              assert(frame)(equalTo(MessageFrame.binary(payload, last = true)))
            }
          }
        },
        testM("a frame with the length greater than 65536 in bytes") {
          val len = 65700

          nextBytes(len).flatMap {
            payload =>
              val chunk =
                Chunk(
                  (0x80 + OpCode.Binary).toByte,
                  0x7F.toByte,
                  (len >> 56).toByte,
                  ((len >> 48) & 0xFF).toByte,
                  ((len >> 40) & 0xFF).toByte,
                  ((len >> 32) & 0xFF).toByte,
                  ((len >> 24) & 0xFF).toByte,
                  ((len >> 16) & 0xFF).toByte,
                  ((len >> 8) & 0xFF).toByte,
                  (len & 0xFF).toByte
                ) ++ payload

              FrameDecoder.decode(chunk, masked = false).map { frame =>
                assert(frame)(equalTo(MessageFrame.binary(payload, last = true)))
              }
          }
        }
      ),
      suite("misc")(
        testM("a frame with masked payload") {
          MaskingKey.get.zip(nextBytes(8)).flatMap {
            case (maskingKey, payload) =>

              val masked = payload.mapAccum(0) {
                case (i, byte) =>
                  (i + 1, (byte ^ maskingKey(i & 0x3)).toByte)
              }._2

              val chunk = Chunk(
                (0x80 + OpCode.Continuation).toByte,
                0x08.toByte
              ) ++ maskingKey ++ masked

              FrameDecoder.decode(chunk, masked = true).map { frame =>
                assert(frame)(equalTo(MessageFrame.continuation(payload, last = true)))
              }
          }
        }
      )
    ).provideCustomLayer(FrameDecoder.live ++ MaskingKey.live)

  private def testFrame(opcode: Int) =
    for {
      payload <- nextBytes(8)
      chunk   = Chunk((0x80 + opcode).toByte, 8.toByte) ++ payload
      frame   <- FrameDecoder.decode(chunk, masked = false)
      assertData = if (opcode != OpCode.Text) assert(frame.data)(equalTo(payload))
      else assert(new String(frame.data.toArray))(equalTo(new String(payload.toArray)))
    } yield assert(frame.last)(isTrue) && assert(frame.frameType.opcode)(equalTo(opcode)) && assertData

}
