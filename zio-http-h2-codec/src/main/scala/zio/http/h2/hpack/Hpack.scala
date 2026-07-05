package zio.http.h2.hpack

import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets

import scala.annotation.experimental
import scala.collection.mutable.ListBuffer

import zio.blocks.chunk.Chunk

final case class HeaderField(name: String, value: String, sensitive: Boolean = false)

@experimental
object Hpack {
  def encode(headers: List[HeaderField]): Chunk[Byte] = new HpackEncoder().encode(headers)

  def decode(bytes: Chunk[Byte]): Either[String, List[HeaderField]] = new HpackDecoder().decode(bytes)
}

@experimental
final class HpackEncoder(initialMaxTableSize: Int = 4096) {
  private val dynamicTable                  = new DynamicTable(initialMaxTableSize)
  private var pendingMinSize: Option[Int]   = None
  private var pendingFinalSize: Option[Int] = None

  def setMaxTableSize(newMaxTableSize: Int): Unit = {
    dynamicTable.setMaxSize(newMaxTableSize)
    pendingMinSize = Some(pendingMinSize.fold(newMaxTableSize)(math.min(_, newMaxTableSize)))
    pendingFinalSize = Some(newMaxTableSize)
  }

  def encode(headers: List[HeaderField]): Chunk[Byte] = {
    val output = new ByteArrayOutputStream()
    emitPendingTableSizeUpdates(output)

    headers.foreach { header =>
      val normalized = header.copy(name = DynamicTable.normalizeName(header.name))
      if (normalized.sensitive) encodeLiteral(output, normalized, LiteralNeverIndexed)
      else {
        val exactIndex = findExactIndex(normalized)
        if (exactIndex > 0) encodeIndexed(output, exactIndex)
        else if (dynamicTable.maximumSize == 0) encodeLiteral(output, normalized, LiteralWithoutIndexing)
        else {
          encodeLiteral(output, normalized, LiteralIncrementalIndexing)
          dynamicTable.add(normalized.copy(sensitive = false))
        }
      }
    }

    Chunk.fromArray(output.toByteArray)
  }

  private def emitPendingTableSizeUpdates(output: ByteArrayOutputStream): Unit = {
    val sizes = (pendingMinSize, pendingFinalSize) match {
      case (Some(minimum), Some(finalSize)) if minimum != finalSize => List(minimum, finalSize)
      case (_, Some(finalSize))                                     => List(finalSize)
      case _                                                        => Nil
    }

    sizes.foreach(size => writePrefixedInteger(output, size, 5, 0x20))
    pendingMinSize = None
    pendingFinalSize = None
  }

  private def encodeIndexed(output: ByteArrayOutputStream, index: Int): Unit =
    writePrefixedInteger(output, index, 7, 0x80)

  private def encodeLiteral(
    output: ByteArrayOutputStream,
    header: HeaderField,
    representation: LiteralRepresentation,
  ): Unit = {
    val nameIndex = findNameIndex(header.name)
    writePrefixedInteger(output, if (nameIndex > 0) nameIndex else 0, representation.prefixBits, representation.mask)
    if (nameIndex <= 0) writeStringLiteral(output, header.name)
    writeStringLiteral(output, header.value)
  }

  private def writePrefixedInteger(
    output: ByteArrayOutputStream,
    value: Int,
    prefixBits: Int,
    prefixMask: Int,
  ): Unit = {
    val encoded = IntegerCodec.encodeInt(value, prefixBits).toArray
    encoded(0) = (encoded(0) | prefixMask).toByte
    output.write(encoded)
  }

  private def writeStringLiteral(output: ByteArrayOutputStream, value: String): Unit = {
    val rawBytes      = value.getBytes(StandardCharsets.UTF_8)
    val huffmanBytes  = HuffmanCodec.encode(value).toArray
    val useHuffman    = huffmanBytes.length < rawBytes.length
    val encodedBytes  = if (useHuffman) huffmanBytes else rawBytes
    val encodedLength = IntegerCodec.encodeInt(encodedBytes.length, 7).toArray

    if (useHuffman) encodedLength(0) = (encodedLength(0) | 0x80).toByte
    output.write(encodedLength)
    output.write(encodedBytes)
  }

  private def findExactIndex(header: HeaderField): Int = {
    val staticIndex = StaticTable.indexOf(header)
    if (staticIndex > 0) staticIndex
    else {
      val dynamicIndex = dynamicTable.indexOf(header)
      if (dynamicIndex > 0) StaticTable.length + dynamicIndex else -1
    }
  }

  private def findNameIndex(name: String): Int = {
    val staticIndex = StaticTable.indexOfName(name)
    if (staticIndex > 0) staticIndex
    else {
      val dynamicIndex = dynamicTable.indexOfName(name)
      if (dynamicIndex > 0) StaticTable.length + dynamicIndex else -1
    }
  }
}

@experimental
final class HpackDecoder(initialMaxTableSize: Int = 4096, maxAllowedTableSize: Int = 4096) {
  private val dynamicTable = new DynamicTable(initialMaxTableSize)

  def decode(bytes: Chunk[Byte]): Either[String, List[HeaderField]] = {
    val input   = bytes.toArray
    val headers = ListBuffer.empty[HeaderField]
    var offset  = 0
    var sawHead = false

    while (offset < input.length) {
      val current = input(offset) & 0xff

      if ((current & 0x80) != 0) {
        decodeIndexed(input, offset) match {
          case Left(error)               => return Left(error)
          case Right((header, consumed)) =>
            headers += header
            offset += consumed
            sawHead = true
        }
      } else if ((current & 0x40) != 0) {
        decodeLiteral(input, offset, 6, shouldIndex = true, sensitive = false) match {
          case Left(error)               => return Left(error)
          case Right((header, consumed)) =>
            headers += header
            dynamicTable.add(header.copy(sensitive = false))
            offset += consumed
            sawHead = true
        }
      } else if ((current & 0x20) != 0) {
        if (sawHead) return Left("HPACK dynamic table size update must appear before header fields in a block")
        IntegerCodec.decodeInt(input, offset, 5) match {
          case Left(error)                => return Left(error)
          case Right((newSize, consumed)) =>
            if (newSize > maxAllowedTableSize) return Left(s"HPACK dynamic table size update exceeds limit: $newSize")
            dynamicTable.setMaxSize(newSize)
            offset += consumed
        }
      } else if ((current & 0x10) != 0) {
        decodeLiteral(input, offset, 4, shouldIndex = false, sensitive = true) match {
          case Left(error)               => return Left(error)
          case Right((header, consumed)) =>
            headers += header
            offset += consumed
            sawHead = true
        }
      } else {
        decodeLiteral(input, offset, 4, shouldIndex = false, sensitive = false) match {
          case Left(error)               => return Left(error)
          case Right((header, consumed)) =>
            headers += header
            offset += consumed
            sawHead = true
        }
      }
    }

    Right(headers.toList)
  }

  private def decodeIndexed(input: Array[Byte], offset: Int): Either[String, (HeaderField, Int)] =
    IntegerCodec.decodeInt(input, offset, 7).flatMap { case (index, consumed) =>
      if (index == 0) Left("Indexed header field representation cannot use index 0")
      else resolveHeader(index).map(header => (header, consumed))
    }

  private def decodeLiteral(
    input: Array[Byte],
    offset: Int,
    prefixBits: Int,
    shouldIndex: Boolean,
    sensitive: Boolean,
  ): Either[String, (HeaderField, Int)] = {
    IntegerCodec.decodeInt(input, offset, prefixBits).flatMap { case (nameIndex, nameIndexBytes) =>
      val nameStart                                 = offset + nameIndexBytes
      val nameResult: Either[String, (String, Int)] =
        if (nameIndex == 0) decodeStringLiteral(input, nameStart)
        else resolveHeader(nameIndex).map(header => (header.name, 0))

      nameResult.flatMap { case (name, nameBytes) =>
        val valueStart = nameStart + nameBytes
        decodeStringLiteral(input, valueStart).map { case (value, valueBytes) =>
          val normalized = HeaderField(DynamicTable.normalizeName(name), value, sensitive)
          val consumed   = nameIndexBytes + nameBytes + valueBytes
          (normalized, consumed)
        }
      }
    }
  }

  private def decodeStringLiteral(input: Array[Byte], offset: Int): Either[String, (String, Int)] = {
    if (offset >= input.length) Left("Truncated HPACK string literal")
    else {
      val huffmanEncoded = (input(offset) & 0x80) != 0
      IntegerCodec.decodeInt(input, offset, 7).flatMap { case (length, consumed) =>
        val dataOffset = offset + consumed
        val dataEnd    = dataOffset + length

        if (length < 0 || dataEnd > input.length) Left("Truncated HPACK string literal data")
        else {
          val data = java.util.Arrays.copyOfRange(input, dataOffset, dataEnd)
          val text =
            if (huffmanEncoded) HuffmanCodec.decode(Chunk.fromArray(data))
            else Right(new String(data, StandardCharsets.UTF_8))

          text.map(value => (value, consumed + length))
        }
      }
    }
  }

  private def resolveHeader(index: Int): Either[String, HeaderField] = {
    if (index <= StaticTable.length) StaticTable.get(index).toRight(s"Invalid static table index: $index")
    else {
      val dynamicIndex = index - StaticTable.length
      dynamicTable.get(dynamicIndex).toRight(s"Invalid dynamic table index: $dynamicIndex (combined index $index)")
    }
  }
}

private sealed trait LiteralRepresentation {
  def prefixBits: Int
  def mask: Int
}

private case object LiteralIncrementalIndexing extends LiteralRepresentation {
  val prefixBits: Int = 6
  val mask: Int       = 0x40
}

private case object LiteralWithoutIndexing extends LiteralRepresentation {
  val prefixBits: Int = 4
  val mask: Int       = 0x00
}

private case object LiteralNeverIndexed extends LiteralRepresentation {
  val prefixBits: Int = 4
  val mask: Int       = 0x10
}
