package zio.http.h2

import scala.annotation.experimental

import zio.blocks.chunk.Chunk
import zio.test._

import zio.http.h2.H2Error.Code
import zio.http.h2.H2Frame.{
  Continuation,
  Data,
  GoAway,
  Headers,
  Ping,
  Priority => PriorityFrame,
  PushPromise,
  RstStream,
  Settings,
  WindowUpdate,
}

@experimental
object FrameCodecSpec extends ZIOSpecDefault {
  override def spec =
    suite("FrameCodecSpec")(
      suite("frame roundtrip")(
        test("DATA roundtrip preserves empty payload and maximum padding") {
          val frame = Data(streamId = 1, data = Chunk.empty, endStream = true, padLength = 255)

          assertRoundTrip(frame)
        },
        test("DATA roundtrip preserves non-zero padding") {
          val frame = Data(streamId = 1, data = bytes(1, 2, 3, 4), endStream = false, padLength = 5)

          assertRoundTrip(frame)
        },
        test("HEADERS roundtrip preserves priority and padding") {
          val frame = Headers(
            streamId = 3,
            headerBlock = bytes(0x01, 0x23, 0x45, 0x67),
            endStream = true,
            endHeaders = true,
            priority = Some(zio.http.h2.Priority(dependency = 1, weight = 256, exclusive = true)),
            padLength = 255,
          )

          assertRoundTrip(frame)
        },
        test("HEADERS roundtrip without priority") {
          val frame = Headers(
            streamId = 3,
            headerBlock = bytes(0x10, 0x20, 0x30),
            endStream = false,
            endHeaders = true,
            priority = None,
          )

          assertRoundTrip(frame)
        },
        test("PRIORITY roundtrip preserves exclusive dependency and max weight") {
          val frame = PriorityFrame(streamId = 5, dependency = 7, weight = 256, exclusive = true)

          assertRoundTrip(frame)
        },
        test("RST_STREAM roundtrip preserves error code") {
          val frame = RstStream(streamId = 7, errorCode = Code.HTTP_1_1_REQUIRED)

          assertRoundTrip(frame)
        },
        test("SETTINGS roundtrip preserves all configured values") {
          val frame = Settings(
            ack = false,
            settings = List(
              Setting(Setting.HEADER_TABLE_SIZE, H2Settings.DefaultHeaderTableSize),
              Setting(Setting.ENABLE_PUSH, 0L),
              Setting(Setting.MAX_CONCURRENT_STREAMS, 100L),
              Setting(Setting.INITIAL_WINDOW_SIZE, Int.MaxValue.toLong),
              Setting(Setting.MAX_FRAME_SIZE, H2Settings.MaximumMaxFrameSize),
              Setting(Setting.MAX_HEADER_LIST_SIZE, 16384L),
            ),
          )

          assertRoundTrip(frame)
        },
        test("PUSH_PROMISE roundtrip preserves promised stream id and padding") {
          val frame = PushPromise(
            streamId = 9,
            promisedStreamId = 11,
            headerBlock = bytes(0x7f, 0x00, 0x55),
            endHeaders = true,
            padLength = 2,
          )

          assertRoundTrip(frame)
        },
        test("PING roundtrip preserves ack flag and 8-byte payload") {
          val frame = Ping(ack = true, data = bytes(0, 1, 2, 3, 4, 5, 6, 7))

          assertRoundTrip(frame)
        },
        test("GOAWAY roundtrip preserves debug payload") {
          val frame =
            GoAway(lastStreamId = 13, errorCode = Code.ENHANCE_YOUR_CALM, debugData = bytes(0xde, 0xad, 0xbe, 0xef))

          assertRoundTrip(frame)
        },
        test("WINDOW_UPDATE roundtrip preserves maximum positive increment") {
          val frame = WindowUpdate(streamId = 15, increment = Int.MaxValue)

          assertRoundTrip(frame)
        },
        test("CONTINUATION roundtrip preserves empty header block") {
          val frame = Continuation(streamId = 17, headerBlock = Chunk.empty, endHeaders = true)

          assertRoundTrip(frame)
        },
      ),
      suite("frame decoding details")(
        test("decode returns the remaining bytes after one frame") {
          val first   = Data(streamId = 1, data = bytes(1, 2, 3), endStream = false)
          val second  = Ping(ack = false, data = bytes(9, 8, 7, 6, 5, 4, 3, 2))
          val encoded = FrameCodec.encode(first) ++ FrameCodec.encode(second)

          assertTrue(FrameCodec.decode(encoded) == Right((first, FrameCodec.encode(second))))
        },
        test("SETTINGS ack roundtrip preserves empty payload") {
          val frame = Settings(ack = true, settings = Nil)

          assertRoundTrip(frame)
        },
        test("SETTINGS with ENABLE_PUSH disabled roundtrip") {
          val frame = Settings(ack = false, settings = List(Setting(Setting.ENABLE_PUSH, 0L)))

          assertRoundTrip(frame)
        },
        test("WINDOW_UPDATE with stream id 0 roundtrip") {
          val frame = WindowUpdate(streamId = 0, increment = 1024)

          assertRoundTrip(frame)
        },
        test("decode insufficient data returns InsufficientData") {
          val partialHeader = bytes(0x00, 0x00, 0x00, 0x01, 0x04, 0x00, 0x00, 0x00)

          assertTrue(FrameCodec.decode(partialHeader) == Left(H2Error.InsufficientData))
        },
        test("decode insufficient payload returns InsufficientData") {
          val frame   = Data(streamId = 1, data = bytes(1, 2, 3), endStream = false)
          val encoded = FrameCodec.encode(frame)
          val partial = encoded.take(encoded.length - 1)

          assertTrue(FrameCodec.decode(partial) == Left(H2Error.InsufficientData))
        },
        test("multiple frames in sequence decode correctly") {
          val first   = Data(streamId = 1, data = bytes(1, 2, 3), endStream = false)
          val second  = Headers(streamId = 3, headerBlock = bytes(0x10, 0x11), endStream = false, endHeaders = true)
          val third   = WindowUpdate(streamId = 0, increment = 4096)
          val encoded = FrameCodec.encode(first) ++ FrameCodec.encode(second) ++ FrameCodec.encode(third)

          val decoded = for {
            firstDecoded  <- FrameCodec.decode(encoded)
            secondDecoded <- FrameCodec.decode(firstDecoded._2)
            thirdDecoded  <- FrameCodec.decode(secondDecoded._2)
          } yield (firstDecoded, secondDecoded, thirdDecoded)

          assertTrue(
            decoded == Right(
              (
                (first, FrameCodec.encode(second) ++ FrameCodec.encode(third)),
                (second, FrameCodec.encode(third)),
                (third, Chunk.empty),
              ),
            ),
          )
        },
      ),
      suite("H2Error")(
        test("error code constants match RFC numeric values") {
          val codes = List(
            Code.NO_ERROR,
            Code.PROTOCOL_ERROR,
            Code.INTERNAL_ERROR,
            Code.FLOW_CONTROL_ERROR,
            Code.SETTINGS_TIMEOUT,
            Code.STREAM_CLOSED,
            Code.FRAME_SIZE_ERROR,
            Code.REFUSED_STREAM,
            Code.CANCEL,
            Code.COMPRESSION_ERROR,
            Code.CONNECT_ERROR,
            Code.ENHANCE_YOUR_CALM,
            Code.INADEQUATE_SECURITY,
            Code.HTTP_1_1_REQUIRED,
          )

          assertTrue(codes.map(_.value) == (0 to 13).toList)
        },
        test("error variants are constructible through the public API") {
          val errors: List[H2Error] = List(
            H2Error.InsufficientData,
            H2Error.InvalidFrameSize("bad size"),
            H2Error.InvalidPadding,
            H2Error.ProtocolViolation("bad protocol"),
          )

          assertTrue(
            errors == List(
              H2Error.InsufficientData,
              H2Error.InvalidFrameSize("bad size"),
              H2Error.InvalidPadding,
              H2Error.ProtocolViolation("bad protocol"),
            ),
          )
        },
      ),
      suite("H2Settings and H2Frame construction")(
        test("setting construction preserves identifiers and values") {
          val setting = Setting(Setting.MAX_FRAME_SIZE, H2Settings.MaximumMaxFrameSize)

          assertTrue(setting == Setting(id = 5, value = 16777215L))
        },
        test("default settings expose the expected negotiated defaults") {
          assertTrue(
            H2Settings.DefaultHeaderTableSize == 4096L,
            H2Settings.DefaultEnablePush == 1L,
            H2Settings.DefaultInitialWindowSize == 65535L,
            H2Settings.DefaultMaxFrameSize == 16384L,
            H2Settings.MinimumMaxFrameSize == 16384L,
            H2Settings.MaximumMaxFrameSize == 16777215L,
            H2Settings.DefaultMaxConcurrentStreams.isEmpty,
            H2Settings.DefaultMaxHeaderListSize.isEmpty,
            H2Settings.DefaultSettings == List(
              Setting(Setting.HEADER_TABLE_SIZE, 4096L),
              Setting(Setting.ENABLE_PUSH, 1L),
              Setting(Setting.INITIAL_WINDOW_SIZE, 65535L),
              Setting(Setting.MAX_FRAME_SIZE, 16384L),
            ),
            H2Settings.KnownIdentifiers == Set(1, 2, 3, 4, 5, 6),
          )
        },
        test("frame constructors expose the expected stream identifiers and fields") {
          val streamPriority = zio.http.h2.Priority(dependency = 3, weight = 32, exclusive = false)
          val headers        = Headers(
            streamId = 19,
            headerBlock = bytes(0xaa),
            endStream = false,
            endHeaders = true,
            priority = Some(streamPriority),
            padLength = 1,
          )

          assertTrue(
            headers.streamId == 19,
            headers.priority.contains(streamPriority),
            Settings(ack = false, settings = Nil).streamId == 0,
            Ping(ack = false, data = bytes(0, 1, 2, 3, 4, 5, 6, 7)).streamId == 0,
            GoAway(lastStreamId = 0, errorCode = Code.NO_ERROR, debugData = Chunk.empty).streamId == 0,
          )
        },
      ),
    )

  private def assertRoundTrip(frame: H2Frame) = {
    val encoded = FrameCodec.encode(frame)
    val decoded = FrameCodec.decode(encoded)

    assertTrue(decoded.map { case (decodedFrame, rest) => (decodedFrame, rest.isEmpty) } == Right((frame, true)))
  }

  private def bytes(values: Int*): Chunk[Byte] =
    Chunk.fromArray(values.toArray.map(_.toByte))
}
