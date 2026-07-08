/*
 * Copyright 2021 - 2023 Sporta Technologies PVT LTD & the ZIO HTTP contributors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package zio.http.h2

import scala.annotation.experimental

import zio.blocks.chunk.{Chunk, ChunkBuilder}

@experimental
object FrameCodec {
  import H2Error._
  import H2Frame._

  private val HeaderLength     = 9
  private val MaxPayloadLength = 16777215
  private val FlagEndStream    = 0x1
  private val FlagAck          = 0x1
  private val FlagEndHeaders   = 0x4
  private val FlagPadded       = 0x8
  private val FlagPriority     = 0x20

  def decode(bytes: Chunk[Byte]): Either[H2Error, (H2Frame, Chunk[Byte])] =
    if (bytes.length < HeaderLength) Left(InsufficientData)
    else {
      val length = (unsigned(bytes(0)) << 16) | (unsigned(bytes(1)) << 8) | unsigned(bytes(2))
      if (bytes.length < HeaderLength + length) Left(InsufficientData)
      else {
        val frameType = unsigned(bytes(3))
        val flags     = unsigned(bytes(4))
        val streamId  = readInt31(bytes, 5)
        val payload   = bytes.slice(HeaderLength, HeaderLength + length)
        val rest      = bytes.drop(HeaderLength + length)
        decodeFrame(frameType, flags, streamId, payload).map(_ -> rest)
      }
    }

  def encode(frame: H2Frame): Chunk[Byte] = {
    val encoded                               = frame match {
      case Data(streamId, data, endStream, padLength)                                  =>
        val flags = if (endStream) FlagEndStream else 0
        (0x0, flagsWithPadding(flags, padLength), streamId, addPadding(data, padLength))
      case Headers(streamId, headerBlock, endStream, endHeaders, priority, padLength)  =>
        val flags  = (if (endStream) FlagEndStream else 0) | (if (endHeaders) FlagEndHeaders else 0) |
          (if (priority.isDefined) FlagPriority else 0)
        val prefix = priority.fold(Chunk.empty: Chunk[Byte])(encodePriorityFields)
        (0x1, flagsWithPadding(flags, padLength), streamId, addPadding(prefix ++ headerBlock, padLength))
      case Priority(streamId, dependency, weight, exclusive)                           =>
        (0x2, 0, streamId, encodePriorityFields(zio.http.h2.Priority(dependency, weight, exclusive)))
      case RstStream(streamId, errorCode)                                              =>
        (0x3, 0, streamId, fromArray(int32(errorCode.value)))
      case Settings(ack, settings)                                                     =>
        val payload = if (ack) {
          require(settings.isEmpty, "SETTINGS ack frames must not carry settings")
          Chunk.empty
        } else encodeSettings(settings)
        (0x4, if (ack) FlagAck else 0, 0, payload)
      case PushPromise(streamId, promisedStreamId, headerBlock, endHeaders, padLength) =>
        val prefix = fromArray(int31(promisedStreamId))
        val flags  = if (endHeaders) FlagEndHeaders else 0
        (0x5, flagsWithPadding(flags, padLength), streamId, addPadding(prefix ++ headerBlock, padLength))
      case Ping(ack, data)                                                             =>
        require(data.length == 8, "PING payload must be exactly 8 bytes")
        (0x6, if (ack) FlagAck else 0, 0, data)
      case GoAway(lastStreamId, errorCode, debugData)                                  =>
        (0x7, 0, 0, fromArray(int31(lastStreamId)) ++ fromArray(int32(errorCode.value)) ++ debugData)
      case WindowUpdate(streamId, increment)                                           =>
        (0x8, 0, streamId, fromArray(int31(increment)))
      case Continuation(streamId, headerBlock, endHeaders)                             =>
        (0x9, if (endHeaders) FlagEndHeaders else 0, streamId, headerBlock)
    }
    val (frameType, flags, streamId, payload) = encoded
    require(payload.length <= MaxPayloadLength, "HTTP/2 frame payload exceeds 24-bit length")
    encodeHeader(payload.length, frameType, flags, streamId) ++ payload
  }

  private def decodeFrame(frameType: Int, flags: Int, streamId: Int, payload: Chunk[Byte]): Either[H2Error, H2Frame] =
    frameType match {
      case 0x0 => decodeData(flags, streamId, payload)
      case 0x1 => decodeHeaders(flags, streamId, payload)
      case 0x2 => decodePriority(streamId, payload)
      case 0x3 => decodeRstStream(streamId, payload)
      case 0x4 => decodeSettings(flags, streamId, payload)
      case 0x5 => decodePushPromise(flags, streamId, payload)
      case 0x6 => decodePing(flags, streamId, payload)
      case 0x7 => decodeGoAway(streamId, payload)
      case 0x8 => decodeWindowUpdate(streamId, payload)
      case 0x9 => decodeContinuation(flags, streamId, payload)
      case _   => Left(ProtocolViolation("Unknown frame type: " + frameType))
    }

  private def decodeData(flags: Int, streamId: Int, payload: Chunk[Byte]): Either[H2Error, H2Frame] =
    for {
      _              <- requireNonZeroStream(streamId, "DATA")
      dataAndPadding <- removePadding(payload, (flags & FlagPadded) != 0, "DATA")
      (data, padLength) = dataAndPadding
    } yield Data(streamId, data, (flags & FlagEndStream) != 0, padLength)

  private def decodeHeaders(flags: Int, streamId: Int, payload: Chunk[Byte]): Either[H2Error, H2Frame] =
    for {
      _                 <- requireNonZeroStream(streamId, "HEADERS")
      contentAndPadding <- removePadding(payload, (flags & FlagPadded) != 0, "HEADERS")
      (content, padLength) = contentAndPadding
      priorityAndHeaderBlock <- splitPriority(content, (flags & FlagPriority) != 0, "HEADERS")
      (priority, headerBlock) = priorityAndHeaderBlock
    } yield Headers(
      streamId,
      headerBlock,
      (flags & FlagEndStream) != 0,
      (flags & FlagEndHeaders) != 0,
      priority,
      padLength,
    )

  private def decodePriority(streamId: Int, payload: Chunk[Byte]): Either[H2Error, H2Frame] =
    for {
      _ <- requireNonZeroStream(streamId, "PRIORITY")
      _ <- requirePayloadSize(payload, 5, "PRIORITY")
      priority = decodePriorityValue(payload)
    } yield Priority(streamId, priority.dependency, priority.weight, priority.exclusive)

  private def decodeRstStream(streamId: Int, payload: Chunk[Byte]): Either[H2Error, H2Frame] =
    for {
      _ <- requireNonZeroStream(streamId, "RST_STREAM")
      _ <- requirePayloadSize(payload, 4, "RST_STREAM")
    } yield RstStream(streamId, Code(readInt32(payload, 0)))

  private def decodeSettings(flags: Int, streamId: Int, payload: Chunk[Byte]): Either[H2Error, H2Frame] =
    for {
      _        <- requireStreamId(streamId == 0, "SETTINGS frames must use stream 0")
      _        <- requireFrameCondition(
        (flags & FlagAck) == 0 || payload.isEmpty,
        "SETTINGS ack frames must have empty payload",
      )
      _        <- requireFrameCondition(payload.length % 6 == 0, "SETTINGS payload must be a multiple of 6 bytes")
      settings <- decodeSettingsPayload(payload)
    } yield Settings((flags & FlagAck) != 0, settings)

  private def decodePushPromise(flags: Int, streamId: Int, payload: Chunk[Byte]): Either[H2Error, H2Frame] =
    for {
      _                 <- requireNonZeroStream(streamId, "PUSH_PROMISE")
      contentAndPadding <- removePadding(payload, (flags & FlagPadded) != 0, "PUSH_PROMISE")
      (content, padLength) = contentAndPadding
      _ <- requireFrameCondition(content.length >= 4, "PUSH_PROMISE payload must include a promised stream id")
    } yield PushPromise(streamId, readInt31(content, 0), content.drop(4), (flags & FlagEndHeaders) != 0, padLength)

  private def decodePing(flags: Int, streamId: Int, payload: Chunk[Byte]): Either[H2Error, H2Frame] =
    for {
      _ <- requireStreamId(streamId == 0, "PING frames must use stream 0")
      _ <- requirePayloadSize(payload, 8, "PING")
    } yield Ping((flags & FlagAck) != 0, payload)

  private def decodeGoAway(streamId: Int, payload: Chunk[Byte]): Either[H2Error, H2Frame] =
    for {
      _ <- requireStreamId(streamId == 0, "GOAWAY frames must use stream 0")
      _ <- requireFrameCondition(payload.length >= 8, "GOAWAY payload must be at least 8 bytes")
    } yield GoAway(readInt31(payload, 0), Code(readInt32(payload, 4)), payload.drop(8))

  private def decodeWindowUpdate(streamId: Int, payload: Chunk[Byte]): Either[H2Error, H2Frame] =
    for {
      _ <- requirePayloadSize(payload, 4, "WINDOW_UPDATE")
      increment = readInt31(payload, 0)
      _ <- requireFrameCondition(increment > 0, "WINDOW_UPDATE increment must be positive")
    } yield WindowUpdate(streamId, increment)

  private def decodeContinuation(flags: Int, streamId: Int, payload: Chunk[Byte]): Either[H2Error, H2Frame] =
    requireNonZeroStream(streamId, "CONTINUATION").map(_ =>
      Continuation(streamId, payload, (flags & FlagEndHeaders) != 0),
    )

  private def decodeSettingsPayload(payload: Chunk[Byte]): Either[H2Error, List[Setting]] = {
    val builder = List.newBuilder[Setting]
    var offset  = 0
    while (offset < payload.length) {
      val id    = readUnsignedShort(payload, offset)
      val value = readUnsignedInt(payload, offset + 2)
      validateSetting(id, value) match {
        case Left(error) => return Left(error)
        case Right(_)    => builder += Setting(id, value)
      }
      offset += 6
    }
    Right(builder.result())
  }

  private def validateSetting(id: Int, value: Long): Either[H2Error, Unit] =
    if (id == Setting.ENABLE_PUSH && value != 0L && value != 1L)
      Left(ProtocolViolation("SETTINGS_ENABLE_PUSH must be 0 or 1"))
    else if (id == Setting.INITIAL_WINDOW_SIZE && value > Int.MaxValue.toLong)
      Left(ProtocolViolation("SETTINGS_INITIAL_WINDOW_SIZE exceeds 31 bits"))
    else if (
      id == Setting.MAX_FRAME_SIZE && (value < H2Settings.MinimumMaxFrameSize || value > H2Settings.MaximumMaxFrameSize)
    ) Left(ProtocolViolation("SETTINGS_MAX_FRAME_SIZE is out of range"))
    else Right(())

  private def splitPriority(
    content: Chunk[Byte],
    hasPriority: Boolean,
    frameName: String,
  ): Either[H2Error, (Option[zio.http.h2.Priority], Chunk[Byte])] =
    if (!hasPriority) Right((None, content))
    else if (content.length < 5) Left(InvalidFrameSize(frameName + " priority segment must be 5 bytes"))
    else Right((Some(decodePriorityValue(content.take(5))), content.drop(5)))

  private def decodePriorityValue(payload: Chunk[Byte]): zio.http.h2.Priority = {
    val dependencyBits = readInt32(payload, 0)
    zio.http.h2.Priority(dependencyBits & 0x7fffffff, unsigned(payload(4)) + 1, (dependencyBits & 0x80000000) != 0)
  }

  private def encodePriorityFields(priority: zio.http.h2.Priority): Chunk[Byte] = {
    requirePriority(priority.dependency, priority.weight)
    val dependency = if (priority.exclusive) priority.dependency | 0x80000000 else priority.dependency & 0x7fffffff
    fromArray(int32(dependency) ++ Array((priority.weight - 1).toByte))
  }

  private def encodeSettings(settings: List[Setting]): Chunk[Byte] = {
    val builder = new ChunkBuilder.Byte
    settings.foreach { setting =>
      validateSetting(setting.id, setting.value)
        .fold(error => throw new IllegalArgumentException(error.toString), _ => ())
      require(
        setting.value >= 0L && setting.value <= 0xffffffffL,
        "HTTP/2 settings values are 32-bit unsigned integers",
      )
      builder.addAll(fromArray(short16(setting.id)))
      builder.addAll(fromArray(int32(setting.value.toInt)))
    }
    builder.result()
  }

  private def removePadding(
    payload: Chunk[Byte],
    padded: Boolean,
    frameName: String,
  ): Either[H2Error, (Chunk[Byte], Int)] =
    if (!padded) Right((payload, 0))
    else if (payload.isEmpty) Left(InvalidFrameSize(frameName + " padded payload must include pad length"))
    else {
      val padLength = unsigned(payload(0))
      val content   = payload.drop(1)
      if (padLength > content.length) Left(InvalidPadding)
      else Right((content.take(content.length - padLength), padLength))
    }

  private def addPadding(content: Chunk[Byte], padLength: Int): Chunk[Byte] = {
    require(padLength >= 0 && padLength <= 255, "HTTP/2 pad length must fit in one byte")
    if (padLength == 0) content
    else Chunk.single(padLength.toByte) ++ content ++ fromArray(new Array[Byte](padLength))
  }

  private def encodeHeader(length: Int, frameType: Int, flags: Int, streamId: Int): Chunk[Byte] = {
    require((streamId & 0x80000000) == 0, "HTTP/2 stream identifiers are 31-bit values")
    fromArray(
      Array[Byte](
        ((length >>> 16) & 0xff).toByte,
        ((length >>> 8) & 0xff).toByte,
        (length & 0xff).toByte,
        frameType.toByte,
        flags.toByte,
      ) ++ int31(streamId),
    )
  }

  private def requireNonZeroStream(streamId: Int, frameName: String): Either[H2Error, Unit] =
    requireStreamId(streamId != 0, frameName + " frames must use a non-zero stream id")

  private def requirePayloadSize(payload: Chunk[Byte], size: Int, frameName: String): Either[H2Error, Unit] =
    if (payload.length == size) Right(())
    else Left(InvalidFrameSize(frameName + " payload must be exactly " + size + " bytes"))

  private def requireStreamId(condition: Boolean, message: String): Either[H2Error, Unit] =
    requireFrameCondition(condition, message)

  private def requireFrameCondition(condition: Boolean, message: String): Either[H2Error, Unit] =
    if (condition) Right(()) else Left(ProtocolViolation(message))

  private def requirePriority(dependency: Int, weight: Int): Unit = {
    require((dependency & 0x80000000) == 0, "HTTP/2 stream dependencies are 31-bit values")
    require(weight >= 1 && weight <= 256, "HTTP/2 weights must be in the range 1..256")
  }

  private def flagsWithPadding(flags: Int, padLength: Int): Int =
    if (padLength > 0) flags | FlagPadded else flags

  private def readUnsignedShort(bytes: Chunk[Byte], offset: Int): Int =
    (unsigned(bytes(offset)) << 8) | unsigned(bytes(offset + 1))

  private def readInt31(bytes: Chunk[Byte], offset: Int): Int =
    readInt32(bytes, offset) & 0x7fffffff

  private def readInt32(bytes: Chunk[Byte], offset: Int): Int =
    (unsigned(bytes(offset)) << 24) | (unsigned(bytes(offset + 1)) << 16) | (unsigned(
      bytes(offset + 2),
    ) << 8) | unsigned(bytes(offset + 3))

  private def readUnsignedInt(bytes: Chunk[Byte], offset: Int): Long =
    readInt32(bytes, offset).toLong & 0xffffffffL

  private def unsigned(byte: Byte): Int = byte & 0xff

  private def int31(value: Int): Array[Byte] = int32(value & 0x7fffffff)

  private def int32(value: Int): Array[Byte] =
    Array(
      ((value >>> 24) & 0xff).toByte,
      ((value >>> 16) & 0xff).toByte,
      ((value >>> 8) & 0xff).toByte,
      (value & 0xff).toByte,
    )

  private def short16(value: Int): Array[Byte] =
    Array(((value >>> 8) & 0xff).toByte, (value & 0xff).toByte)

  private def fromArray(bytes: Array[Byte]): Chunk[Byte] = Chunk.fromArray(bytes)
}
