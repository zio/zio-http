package zhttp.experiment.multipart

import java.nio.charset.StandardCharsets

import io.netty.util.CharsetUtil
import zio.Chunk
import zio.stream.ZTransducer

sealed trait State
case object NotStarted   extends State
case object PartHeader   extends State
case object PartData     extends State
case object PartComplete extends State
case object End          extends State

sealed trait Message
case object Boundary                                                                          extends Message
final case class MetaInfo(ContentDisposition: String, name: String, fileName: Option[String]) extends Message
final case class ChunkedData(chunkedData: String)                                             extends Message
case object BodyEnd                                                                           extends Message
case object Empty                                                                             extends Message

class Parser(boundary: String) {
  val boundaryBytes: Chunk[Byte]   = Chunk.fromArray(boundary.getBytes(CharsetUtil.UTF_8))
  val CRLFBytes: Chunk[Byte]       = Chunk.fromArray(Array[Byte]('\r', '\n'))
  val doubleCRLFBytes: Chunk[Byte] = Chunk.fromArray(Array[Byte]('\r', '\n', '\r', '\n'))
  val dashDashBytesN: Chunk[Byte]  = Chunk.fromArray(Array[Byte]('-', '-'))
  val startByte: Chunk[Byte]       = dashDashBytesN ++ boundaryBytes
  val delimiter: Chunk[Byte]       = CRLFBytes ++ startByte
  var state: State                 = NotStarted
  var matchIndex: Int              = 0 // matching index of boundary and double dash
  var CRLFIndex: Int               = 0
  var tempData: Chunk[Byte]        = Chunk.empty
  def getMessages(input: Chunk[Byte], startIndex: Int = 0, outChunk: Chunk[Message] = Chunk.empty): Chunk[Message] = {
    state match {
      case NotStarted   => {
        var i            = startIndex
        var outChunkTemp = outChunk
        while (i < input.length && state == NotStarted) {
          if (input.byte(i) == delimiter.byte(matchIndex)) {
            i = i + 1
            matchIndex = matchIndex + 1
            tempData = tempData ++ Chunk(input.byte(i))
            if (matchIndex == delimiter.length) { // match complete
              state = PartHeader
              matchIndex = 0
              tempData = Chunk.empty              // release temp data to GC
            }
          } else {
            i = input.length // Invalid input. Break the loop
          }
        }
        if (i < input.length && state != NotStarted) { // more data is there in input
          outChunkTemp = getMessages(input, i - 1, outChunk ++ Chunk(Boundary))
        }
        outChunkTemp
      } // Look for starting Boundary
      case PartHeader   => {
        var i                = startIndex
        var terminatingMatch = 0
        var outChunkTemp     = outChunk
        println("Part Header")
        println(outChunkTemp)
        while (i < input.length && state == PartHeader) {
          if (doubleCRLFBytes.byte(terminatingMatch) == input.byte(i)) {
            terminatingMatch = terminatingMatch + 1
          } else {
            terminatingMatch = 0
          }
          tempData = tempData ++ Chunk(input.byte(i))
          i = i + 1
          if (terminatingMatch == doubleCRLFBytes.length) {
            // todo: Add header parsing logic here Parse and create Header Chunk
            outChunkTemp = outChunkTemp ++ Chunk(MetaInfo(tempData.byte(0).toString(), "abc", Some("abc.jpg")))
            tempData = Chunk.empty
            state = PartData
          }
        }
        if (i < input.length && state != PartHeader) {
          outChunkTemp = getMessages(input, i - 1, outChunkTemp)
        }
        outChunkTemp
      }
      case PartData     => {
        var i                      = startIndex
        var outChunkTemp           = outChunk
        var partChunk: Chunk[Byte] = Chunk.empty
        println("part data")
        println(outChunkTemp)
        while (i < input.length && state == PartData) {
          if (delimiter.byte(matchIndex) == input.byte(i)) {
            matchIndex = matchIndex + 1
            tempData = tempData ++ Chunk(input.byte(i))
          } else {
            matchIndex = 0
            partChunk = partChunk ++ tempData
            partChunk = partChunk.appended(input.byte(i))
            tempData = Chunk.empty
          }
          if (matchIndex == delimiter.length) {
            outChunkTemp =
              outChunkTemp ++ Chunk(ChunkedData(new String(partChunk.toArray, StandardCharsets.UTF_8)), Boundary)
            partChunk = Chunk.empty
            matchIndex = 0
            tempData = Chunk.empty
            state = PartComplete
          }
          i = i + 1
        }
        outChunkTemp = outChunkTemp ++ Chunk(ChunkedData(new String(partChunk.toArray, StandardCharsets.UTF_8)))
        if (i < input.length && state != PartData) {
          outChunkTemp = getMessages(input, i - 1, outChunkTemp)
        }
        outChunkTemp
      }
      case PartComplete => {
        var i               = startIndex
        var outputChunkTemp = outChunk
        while (i < input.length && state == PartComplete) {
          if (dashDashBytesN.byte(matchIndex) == input.byte(i)) {
            matchIndex = matchIndex + 1
          }
          if (CRLFBytes.byte(CRLFIndex) == input.byte(i)) {
            CRLFIndex = CRLFIndex + 1
          }
          if (CRLFIndex == CRLFBytes.length) {
            CRLFIndex = 0
            matchIndex = 0
            state = PartHeader
          }
          if (matchIndex == dashDashBytesN.length) {
            matchIndex = 0
            outputChunkTemp = outputChunkTemp ++ Chunk(BodyEnd)
            state = End
          }
          i = i + 1
        }
        if (i < input.length && state == PartHeader) {
          outputChunkTemp = getMessages(input, i - 1, outputChunkTemp)
        }
        outputChunkTemp
      }
      case End          => outChunk
    }
  }
  val byteToMessageTransducer: ZTransducer[Any, Nothing, Chunk[Byte], Message]                                     =
    ZTransducer.fromFunction[Chunk[Byte], Chunk[Message]](a => getMessages(a)).mapChunks(_.flatten)

//  def byteStreamToMessageStream(input: UStream[Chunk[Byte]]) = input.transduce(byteToMessageTransducer)
}
