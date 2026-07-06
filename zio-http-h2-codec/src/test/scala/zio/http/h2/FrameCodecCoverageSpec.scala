package zio.http.h2

import scala.annotation.experimental
import scala.util.Try

import zio.blocks.chunk.Chunk
import zio.test._

import zio.http.h2.H2Error.{InvalidFrameSize, InvalidPadding, ProtocolViolation}
import zio.http.h2.H2Frame.{Continuation, Data, Headers, Ping, Priority => PriorityFrame, PushPromise, Settings}

@experimental
object FrameCodecCoverageSpec extends ZIOSpecDefault {
  override def spec =
    suite("FrameCodecCoverageSpec")(
      test("roundtrip preserves non-default false branches for HEADERS, PUSH_PROMISE, and CONTINUATION") {
        val headers      = Headers(
          streamId = 1,
          headerBlock = bytes(0x01, 0x02),
          endStream = false,
          endHeaders = false,
          priority = Some(zio.http.h2.Priority(dependency = 7, weight = 32, exclusive = false)),
        )
        val pushPromise  = PushPromise(
          streamId = 3,
          promisedStreamId = 5,
          headerBlock = bytes(0x0a, 0x0b),
          endHeaders = false,
        )
        val continuation = Continuation(streamId = 7, headerBlock = bytes(0x55), endHeaders = false)

        assertTrue(
          roundTrip(headers),
          roundTrip(pushPromise),
          roundTrip(continuation),
        )
      },
      test("decode rejects malformed frame shapes and protocol violations") {
        val unknownFrameType = rawFrame(frameType = 0xff, flags = 0x00, streamId = 1, payload = Chunk.empty)
        val zeroStreamData   = rawFrame(frameType = 0x0, flags = 0x00, streamId = 0, payload = bytes(0x01))
        val missingPadLength = rawFrame(frameType = 0x0, flags = 0x08, streamId = 1, payload = Chunk.empty)
        val invalidPadding   = rawFrame(frameType = 0x0, flags = 0x08, streamId = 1, payload = bytes(0x02, 0x01))
        val shortPriority    =
          rawFrame(frameType = 0x1, flags = 0x20, streamId = 1, payload = bytes(0x01, 0x02, 0x03, 0x04))
        val badAckSettings   = rawFrame(frameType = 0x4, flags = 0x01, streamId = 0, payload = bytes(0x00))
        val zeroIncrement    =
          rawFrame(frameType = 0x8, flags = 0x00, streamId = 1, payload = bytes(0x00, 0x00, 0x00, 0x00))

        assertTrue(
          FrameCodec.decode(unknownFrameType) == Left(ProtocolViolation("Unknown frame type: 255")),
          FrameCodec.decode(zeroStreamData) == Left(ProtocolViolation("DATA frames must use a non-zero stream id")),
          FrameCodec.decode(missingPadLength) == Left(InvalidFrameSize("DATA padded payload must include pad length")),
          FrameCodec.decode(invalidPadding) == Left(InvalidPadding),
          FrameCodec.decode(shortPriority) == Left(InvalidFrameSize("HEADERS priority segment must be 5 bytes")),
          FrameCodec.decode(badAckSettings) == Left(ProtocolViolation("SETTINGS ack frames must have empty payload")),
          FrameCodec.decode(zeroIncrement) == Left(ProtocolViolation("WINDOW_UPDATE increment must be positive")),
        )
      },
      test("decode rejects invalid SETTINGS values") {
        val invalidEnablePush =
          rawFrame(frameType = 0x4, flags = 0x00, streamId = 0, payload = settingPayload(Setting.ENABLE_PUSH, 2L))
        val invalidWindowSize =
          rawFrame(
            frameType = 0x4,
            flags = 0x00,
            streamId = 0,
            payload = settingPayload(Setting.INITIAL_WINDOW_SIZE, Int.MaxValue.toLong + 1L),
          )
        val invalidFrameSize  =
          rawFrame(
            frameType = 0x4,
            flags = 0x00,
            streamId = 0,
            payload = settingPayload(Setting.MAX_FRAME_SIZE, H2Settings.MinimumMaxFrameSize - 1L),
          )

        assertTrue(
          FrameCodec.decode(invalidEnablePush) == Left(ProtocolViolation("SETTINGS_ENABLE_PUSH must be 0 or 1")),
          FrameCodec.decode(invalidWindowSize) == Left(
            ProtocolViolation("SETTINGS_INITIAL_WINDOW_SIZE exceeds 31 bits"),
          ),
          FrameCodec.decode(invalidFrameSize) == Left(ProtocolViolation("SETTINGS_MAX_FRAME_SIZE is out of range")),
        )
      },
      test("encode rejects invalid frame parameters") {
        val invalidAckSettings =
          Try(FrameCodec.encode(Settings(ack = true, settings = List(Setting(Setting.ENABLE_PUSH, 1L))))).isFailure
        val invalidPing        = Try(FrameCodec.encode(Ping(ack = false, data = bytes(0x01, 0x02)))).isFailure
        val invalidDependency  = Try(
          FrameCodec.encode(PriorityFrame(streamId = 1, dependency = Int.MinValue, weight = 1, exclusive = false)),
        ).isFailure
        val invalidWeight      =
          Try(FrameCodec.encode(PriorityFrame(streamId = 1, dependency = 1, weight = 0, exclusive = false))).isFailure
        val invalidPadding     =
          Try(FrameCodec.encode(Data(streamId = 1, data = Chunk.empty, endStream = false, padLength = 256))).isFailure
        val invalidStreamId    =
          Try(FrameCodec.encode(Data(streamId = Int.MinValue, data = Chunk.empty, endStream = false))).isFailure

        assertTrue(
          invalidAckSettings,
          invalidPing,
          invalidDependency,
          invalidWeight,
          invalidPadding,
          invalidStreamId,
        )
      },
    )

  private def roundTrip(frame: H2Frame): Boolean =
    FrameCodec.decode(FrameCodec.encode(frame)) == Right((frame, Chunk.empty))

  private def rawFrame(frameType: Int, flags: Int, streamId: Int, payload: Chunk[Byte]): Chunk[Byte] =
    Chunk.fromArray(
      Array[Byte](
        ((payload.length >>> 16) & 0xff).toByte,
        ((payload.length >>> 8) & 0xff).toByte,
        (payload.length & 0xff).toByte,
        frameType.toByte,
        flags.toByte,
      ) ++ int31(streamId),
    ) ++ payload

  private def settingPayload(id: Int, value: Long): Chunk[Byte] =
    Chunk.fromArray(short16(id) ++ int32(value.toInt))

  private def int31(value: Int): Array[Byte] =
    int32(value & 0x7fffffff)

  private def int32(value: Int): Array[Byte] =
    Array(
      ((value >>> 24) & 0xff).toByte,
      ((value >>> 16) & 0xff).toByte,
      ((value >>> 8) & 0xff).toByte,
      (value & 0xff).toByte,
    )

  private def short16(value: Int): Array[Byte] =
    Array(((value >>> 8) & 0xff).toByte, (value & 0xff).toByte)

  private def bytes(values: Int*): Chunk[Byte] =
    Chunk.fromArray(values.toArray.map(_.toByte))
}
