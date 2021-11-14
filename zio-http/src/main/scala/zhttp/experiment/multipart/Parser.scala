package zhttp.experiment.multipart

import zhttp.http.Header
import zio.Chunk

import java.nio.charset.StandardCharsets
import scala.annotation.tailrec

sealed trait State
case object NotStarted   extends State
case object PartHeader   extends State
case object PartData     extends State
case object PartComplete extends State
case object End          extends State

sealed trait PartMeta
case class PartContentDisposition(name: String, filename: Option[String]) extends PartMeta
case class PartContentType(ContentType: String, charset: Option[String])  extends PartMeta
case class PartContentTransferEncoding(encoding: String)                  extends PartMeta

sealed trait Message
final case class MetaInfo(
  contentDisposition: PartContentDisposition,
  contentType: Option[PartContentType] = None,
  transferEncoding: Option[PartContentTransferEncoding] = None,
) extends Message
final case class ChunkedData(chunkedData: Chunk[Byte]) extends Message
case object BodyEnd                                    extends Message

case object Constants {
  val CRLF                         = "\r\n"
  val CRLFBytes: Chunk[Byte]       = Chunk.fromArray(Array[Byte]('\r', '\n'))
  val doubleCRLFBytes: Chunk[Byte] = Chunk.fromArray(Array[Byte]('\r', '\n', '\r', '\n'))
  val dashDashBytesN: Chunk[Byte]  = Chunk.fromArray(Array[Byte]('-', '-'))
}

case class ParserState(
  delimiter: Chunk[Byte],
  state: State = NotStarted,
  matchIndex: Int = 0,
  CRLFIndex: Int = 0,
  tempData: Chunk[Byte] = Chunk.empty,
  partChunk: Chunk[Byte] = Chunk.empty,
  startIndex: Int = 0,
  outChunk: Chunk[Message] = Chunk.empty,
)

object Parser {
  import zhttp.experiment.multipart.Constants._
  def getBoundary(headers: List[Header]): Either[Throwable, String] =
    headers.filter(_.name == "Content-Type") match {
      case ::(head, _) =>
        head.value.toString.split(";").toList.filter(_.contains("boundary=")) match {
          case ::(head, _) =>
            head.split("=").toList match {
              case ::(_, next) =>
                next match {
                  case ::(head, _) => Right(head)
                  case Nil         => Left(new Error("Invalid Request"))
                }
              case Nil         => Left(new Error("Invalid Request"))
            }
          case Nil         => Left(new Error("Invalid Request"))
        }
      case Nil         => Left(new Error("Invalid Request"))
    }
  private def parsePartHeader(input: Chunk[Byte]): MetaInfo         = {
    val headerString = new String(input.toArray, StandardCharsets.UTF_8)
    headerString
      .split(CRLF)
      .foldLeft(MetaInfo(PartContentDisposition("", None), None, None))((metaInfo, aHeader) => {
        val subPart       = aHeader.split(";").map(_.trim())
        val subPartHeader = subPart.head.split(":")
        val metaInfoType  = subPartHeader.head
        val directiveData = subPart.tail
          .map(s => {
            s.split("=").map(_.trim) match {
              case Array(v1, v2) => (v1, v2)
              case _             => ("", "")
            }
          })
        if (metaInfoType == "Content-Disposition") {
          metaInfo.copy(contentDisposition =
            directiveData
              .foldLeft(PartContentDisposition("", None))((acc, value) => {
                if (value._1.toLowerCase == "name") {
                  acc.copy(name = value._2.replace("\"", ""))
                } else if (value._1.toLowerCase == "filename") {
                  acc.copy(filename = Some(value._2.replace("\"", "")))
                } else {
                  acc
                }
              }),
          )
        } else if (metaInfoType == "Content-Type") {
          if (directiveData.isEmpty) {
            metaInfo.copy(contentType = Some(PartContentType(subPartHeader.tail.head.trim, None)))
          } else {
            metaInfo.copy(contentType =
              Some(
                directiveData
                  .foldLeft(PartContentType(subPartHeader.tail.head.trim, None))((acc, value) => {
                    if (value._1.toLowerCase == "charset") {
                      acc.copy(charset = Some(value._2))
                    }
                    acc
                  }),
              ),
            )
          }
        } else if (metaInfoType == "Content-Transfer-Encoding") {
          metaInfo.copy(transferEncoding = Some(PartContentTransferEncoding(subPartHeader.tail.head.trim)))
        } else {
          metaInfo
        }
      })
  }

  @tailrec
  def getMessages(
    input: Chunk[Byte],
    parserState: ParserState,
  ): Either[String, (Chunk[Message], ParserState)] = {

    val startDelimiterRaw = dashDashBytesN ++ parserState.delimiter
    val delimiterRaw      = CRLFBytes ++ startDelimiterRaw
    var state             = parserState.state
    var matchIndex        = parserState.matchIndex
    var CRLFIndex         = parserState.CRLFIndex
    var tempData          = parserState.tempData
    var partChunk         = parserState.partChunk
    state match {
      case NotStarted   =>
        var i = parserState.startIndex
        // Look for starting Boundary
        while (i < input.length && state == NotStarted) {
          if (input.byte(i) == startDelimiterRaw.byte(matchIndex)) {
            i = i + 1
            matchIndex = matchIndex + 1
            tempData = tempData ++ Chunk(input.byte(i))
            if (matchIndex == startDelimiterRaw.length) { // match complete
              state = PartHeader                          // start getting part header data
              matchIndex = 0
              tempData = Chunk.empty                      // discard boundary bytes
            }
          } else {
            return Left("Invalid Request")
          }
        }
        if (i < input.length && state != NotStarted) { // more data is there in input
          getMessages(
            input,
            ParserState(
              parserState.delimiter,
              state,
              matchIndex,
              CRLFIndex,
              tempData,
              partChunk,
              i,
              parserState.outChunk,
            ),
          )
        } else {
          Right(
            (
              parserState.outChunk,
              ParserState(parserState.delimiter, state, matchIndex, CRLFIndex, tempData, partChunk),
            ),
          )
        }
      case PartHeader   =>
        var i            = parserState.startIndex
        var outChunkTemp = parserState.outChunk
        // Look until double CRLF
        while (i < input.length && state == PartHeader) {
          if (doubleCRLFBytes.byte(matchIndex) == input.byte(i)) {
            matchIndex = matchIndex + 1
          } else {
            // do a look behind check
            if (doubleCRLFBytes.byte(0) == input.byte(i)) {
              matchIndex = 1
            } else {
              matchIndex = 0
            }
          }
          // todo: tempData needs to have a bound (overflowing that needs to throw error)
          tempData = tempData ++ Chunk(input.byte(i))
          i = i + 1
          if (matchIndex == doubleCRLFBytes.length) {
            matchIndex = 0
            val metaInfo = parsePartHeader(tempData) // return Either
            outChunkTemp = outChunkTemp ++ Chunk(metaInfo)
            tempData = Chunk.empty
            state = PartData
          }
        }
        if (i < input.length && state != PartHeader) {
          getMessages(
            input,
            ParserState(parserState.delimiter, state, matchIndex, CRLFIndex, tempData, partChunk, i, outChunkTemp),
          )
        } else {
          Right((outChunkTemp, ParserState(parserState.delimiter, state, matchIndex, CRLFIndex, tempData, partChunk)))
        }
      case PartData     =>
        var i            = parserState.startIndex
        var outChunkTemp = parserState.outChunk
        // Look until boundary delimiter
        while (i < input.length && state == PartData) {
          if (delimiterRaw.byte(matchIndex) == input.byte(i)) {
            matchIndex = matchIndex + 1
            tempData = tempData ++ Chunk(input.byte(i))
          } else {
            matchIndex = 0
            partChunk = partChunk ++ tempData
            tempData = Chunk.empty
            // do look behind check
            if (delimiterRaw.byte(matchIndex) == input.byte(i)) {
              matchIndex = 1
              tempData = Chunk(input.byte(i))
            } else {
              partChunk = partChunk.appended(input.byte(i))
            }
          }
          if (matchIndex == delimiterRaw.length) {
            outChunkTemp = outChunkTemp ++ Chunk(ChunkedData(partChunk))
            partChunk = Chunk.empty
            matchIndex = 0
            tempData = Chunk.empty
            state = PartComplete
          }
          i = i + 1
        }
        if (i >= input.length && state == PartData && !partChunk.isEmpty) {
          outChunkTemp = outChunkTemp ++ Chunk(ChunkedData(partChunk))
          partChunk = Chunk.empty
        }
        if (i < input.length && state != PartData) {
          getMessages(
            input,
            ParserState(parserState.delimiter, state, matchIndex, CRLFIndex, tempData, partChunk, i, outChunkTemp),
          )
        } else {
          Right((outChunkTemp, ParserState(parserState.delimiter, state, matchIndex, CRLFIndex, tempData, partChunk)))
        }
      case PartComplete =>
        var i               = parserState.startIndex
        var outputChunkTemp = parserState.outChunk
        while (i < input.length && state == PartComplete) {
          if (dashDashBytesN.byte(matchIndex) == input.byte(i)) {
            matchIndex = matchIndex + 1
          } else {
            matchIndex = 0
          }
          if (CRLFBytes.byte(CRLFIndex) == input.byte(i)) {
            CRLFIndex = CRLFIndex + 1
          } else {
            CRLFIndex = 0
          }
          if (matchIndex == 0 && CRLFIndex == 0) {
            return Left("Invalid Request")
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
          getMessages(
            input,
            ParserState(
              parserState.delimiter,
              state,
              matchIndex,
              CRLFIndex,
              tempData,
              partChunk,
              i - 1,
              outputChunkTemp,
            ),
          )
        } else {
          Right(
            (outputChunkTemp, ParserState(parserState.delimiter, state, matchIndex, CRLFIndex, tempData, partChunk)),
          )
        }
      case End          =>
        Right(
          (parserState.outChunk, ParserState(parserState.delimiter, state, matchIndex, CRLFIndex, tempData, partChunk)),
        )
    }
  }

}
