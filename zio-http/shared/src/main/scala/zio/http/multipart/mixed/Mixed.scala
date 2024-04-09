package zio.http.multipart.mixed

import zio.http.endpoint.openapi.JsonSchema.StringFormat.UUID
import zio.http.internal.FormAST
import zio.http.multipart.mixed.Mixed.Parser
import zio.{Chunk, ZIO, ZNothing}
import zio.http.{Body, Boundary, Header, Headers, MediaType, boolean}
import zio.stream.{ZChannel, ZPipeline, ZStream}

import java.nio.charset.{CharacterCodingException, StandardCharsets}

case class Mixed(source: ZStream[Any, Throwable, Byte],
                 boundary: Boundary,
                 bufferSize: Int = 8192) {

  def parts: ZStream[Any, Throwable, Part] = ZStream
    .unwrapScoped[Any]{
      source
        .toChannel
        .toPull
        .map{ pull =>
          new Parser(boundary, pull, bufferSize)
            .result
        }
    }

}

case class Part( headers : Headers,
                 bytes : ZStream[Any, Throwable, Byte]) {
  def contentType: Option[Header.ContentType] =
    headers.header(Header.ContentType)
  def mediaType: Option[MediaType] =
    contentType.map(_.mediaType)

  //each part may be a multipart entity on its own
  def boundary : Option[Boundary] =
    contentType.flatMap(_.boundary)

  def toBody: Body = {
    val base = Body.fromStreamChunked(bytes)
    (this.mediaType, this.boundary) match {
      case (Some(mt), Some(boundary)) =>
        base.contentType(mt, boundary)
      case (Some(mt), None) =>
        base.contentType(mt)
      case _ =>
        base
    }
  }
}

object Mixed {
  val crlf = Chunk[Byte]('\r', '\n')

  class Parser(boundary: Boundary,
               pull : ZIO[Any, Throwable, Either[Any, Chunk[Byte]]],
               bufferSize: Int
              ) {

    lazy val upstream: ZChannel[Any, Any, Any, Any, Throwable, Chunk[Byte], Any] = ZChannel
      .fromZIO(pull)
      .foldCauseChannel(
        ZChannel.refailCause(_),
        {
          case Left(_) => ZChannel.unit
          case Right(chunk) => ZChannel.write(chunk) *> upstream
        }
      )

    def preamble(buff : Chunk[Byte] = Chunk.empty) : ZChannel[Any, ZNothing, Chunk[Byte], Any, Throwable, Nothing, (Chunk[Byte], Boolean)] =
      buff.indexOfSlice(crlf) match {
        case -1 =>
          //have to read more input, can discard most of the buffer
          val keep = if(buff.nonEmpty && buff.last == '\r')
            Chunk.single(buff.last)
          else
            Chunk.empty
          ZChannel
            .readWithCause(
              in => preamble(keep ++ in),
              ZChannel.refailCause(_),
              done =>
                if(boundary.isClosing(buff))
                  ZChannel.succeed((Chunk.empty, true))
                else if(boundary.isEncapsulating(buff))
                  ZChannel.succeed((Chunk.empty, false))
                else
                  ZChannel.fail(new IllegalStateException("multipart/chunked body ended with no boundary"))
            )
        case idx =>
          val h = buff.take(idx)
          val rest = buff.drop(idx + crlf.size)
          if(boundary.isClosing(h))
            ZChannel.succeed((rest, true))
          else if(boundary.isEncapsulating(h))
            ZChannel.succeed((rest, false))
          else
            preamble(rest)
      }

    def cont(buff : Chunk[Byte]): ZChannel[Any, ZNothing, Chunk[Byte], Any, Throwable, Part, Any] =
      parseHeaders(buff, Headers.empty)
        .flatMap{
          case (headers, rest, closed) =>
            parseContent(headers, rest, closed)
        }

    def parseHeaders(buff : Chunk[Byte], res : Headers) : ZChannel[Any, ZNothing, Chunk[Byte], Any, Throwable, Nothing, (Headers, Chunk[Byte], Boolean)] = {
      buff.indexOfSlice(crlf) match {
        case -1 =>
          //read more:
          ZChannel
            .readWithCause(
              in => parseHeaders(buff ++ in, res),
              ZChannel.refailCause(_),
              _ => ZChannel.fail(new IllegalStateException("multipart/chunked body ended with no boundary"))
            )
        case 0 =>
          //empty line, hence no more headers
          ZChannel.succeed((res, buff.drop(crlf.size), false))
        case idx =>
          val h = buff.take(idx)
          val rest = buff.drop(idx + crlf.size)
          //not sure this is actually valid
          if(boundary.isClosing(h))
            ZChannel.succeed((res, rest, true))
          else if(boundary.isEncapsulating(h))  //ditto
            ZChannel.succeed(res, rest, false)
          else {
            //todo: this is a private class, either avoid using it or refactor
            FormAST
              .Header
              .fromBytes(h.toArray, StandardCharsets.UTF_8)
              .map{
                case FormAST.Header(name, value) =>
                  parseHeaders(rest, res.addHeader(name, value))
              }
              .getOrElse(parseHeaders(rest, res))
          }
      }
    }

    def subStream(pr : zio.Promise[Throwable, (Chunk[Byte], Boolean)], initialBuff : Chunk[Byte]) : ZStream[Any, Throwable, Byte] = {
      def ch(buff : Chunk[Byte]) : ZChannel[Any, ZNothing, Chunk[Byte], Any, Throwable, Chunk[Byte], Any] = {
        buff.indexOfSlice(crlf) match {
          case -1 if buff.size > bufferSize =>
            val (toEmit, rest) = buff.splitAt((buff.size - boundary.closingBoundaryBytes.size + 1) max 0)
            ZChannel
            .write(toEmit)  *>
            ZChannel.readWithCause(
              in => ch(rest ++ in),
              c => ZChannel.fromZIO(pr.failCause(c)) *> ZChannel.refailCause(c),
              done => {
                val ex = new IllegalStateException("multipart/chunked body ended with no boundary")
                ZChannel.fromZIO(pr.fail(ex)) *> ZChannel.fail(ex)
              }
            )
          case -1 =>
            ZChannel.readWithCause(
              in => ch(buff ++ in),
              c => ZChannel.fromZIO(pr.failCause(c)) *> ZChannel.refailCause(c),
              done => {
                val ex = new IllegalStateException("multipart/chunked body ended with no boundary")
                ZChannel.fromZIO(pr.fail(ex)) *> ZChannel.fail(ex)
              }
            )
          case idx =>
            val (h, rest) = buff.splitAt(idx)
            if(boundary.isClosing(h))
              ZChannel.fromZIO(pr.succeed((rest.drop(crlf.size), true)))
            else if(boundary.isEncapsulating(h))
              ZChannel.fromZIO(pr.succeed((rest.drop(crlf.size), false)))
            else
              ZChannel.write(h ++ crlf) *> ch(rest.drop(crlf.size))
        }
      }

      val pl: ZPipeline[Any, Throwable, Byte, Byte] = ch(initialBuff).toPipeline
      upstream.toStream >>> pl
    }

    def parseContent(headers : Headers, buff : Chunk[Byte], closed : Boolean) : ZChannel[Any, ZNothing, Chunk[Byte], Any, Throwable, Part, Any] = {
      if(closed)
        ZChannel.write(Part(headers, ZStream.empty))  *>
        epilogue //ignore the epilogue
      else {
        ZChannel
          .unwrap{
            zio
              .Promise
              .make[Throwable, (Chunk[Byte], Boolean)]
              .map{pr =>
                val part = Part(headers, subStream(pr, buff))
                ZChannel.write(part)  *>
                ZChannel
                  .fromZIO(pr.await)
                  .flatMap {
                    case (rest, true) =>
                      epilogue
                    case (rest, false) =>
                      cont(rest)
                  }
              }
          }
      }
    }

    val epilogue: ZChannel[Any, ZNothing, Chunk[Byte], Any, ZNothing, Nothing, Any] = ZChannel.identity[ZNothing, Chunk[Byte], Any].drain

    val startCh: ZChannel[Any, ZNothing, Chunk[Byte], Any, Throwable, Chunk[Part], Any] = preamble(Chunk.empty)
      .flatMap{
        case (leftover, false) =>
          cont(leftover)
        case (_, true) =>
          epilogue
      }
      .mapOut(Chunk.single(_))
    val startPl: ZPipeline[Any, Throwable, Byte, Part] = startCh.toPipeline

    val result: ZStream[Any, Throwable, Part] = upstream.toStream >>> startPl
  }

  def fromBody(body : Body,
               bufferSize: Int = 8192) : Option[Mixed] = {
    body
      .boundary
      .map{b =>
        Mixed(body.asStream, b, bufferSize)
      }
  }

  def fromParts(parts : ZStream[Any, Throwable, Part],
                boundary: Boundary,
                bufferSize: Int = 8192) : Mixed = {
    val sep = crlf ++ boundary.encapsulationBoundaryBytes
    val term = crlf ++ boundary.closingBoundaryBytes
    val bytes =
      parts
        .flatMap{
          case Part(headers, bytes) =>
            val headersBytes: ZStream[Any, CharacterCodingException, Byte] = ZStream
              .fromIterable(headers)
              .map{h =>
                s"${h.headerName}: ${h.renderedValue}\r\n"
              }  >>>
              ZPipeline.utf8Encode

            ZStream
              .concatAll( Chunk(ZStream.fromChunk(sep),
                headersBytes,
                ZStream.fromChunk(crlf),
                bytes
              ))
        }
        .concat( ZStream.fromChunk(term))

    Mixed(bytes, boundary, bufferSize)
  }

  def fromPartsUUID(parts : ZStream[Any, Throwable, Part],
                bufferSize: Int = 8192): ZIO[Any, Nothing, Mixed] = {
    Boundary
      .randomUUID
      .map(fromParts(parts, _, bufferSize))
  }

}