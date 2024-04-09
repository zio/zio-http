package zio.http.multipart.mixed

import java.nio.charset.{CharacterCodingException, StandardCharsets}

import zio.{Chunk, ZIO, ZNothing}

import zio.stream.{ZChannel, ZPipeline, ZStream}

import zio.http._
import zio.http.endpoint.openapi.JsonSchema.StringFormat.UUID
import zio.http.internal.FormAST
import zio.http.multipart.mixed.Mixed.Parser

case class Mixed(source: ZStream[Any, Throwable, Byte], boundary: Boundary, bufferSize: Int = 8192) {

  def parts: ZStream[Any, Throwable, Part] = ZStream
    .unwrapScoped[Any] {
      source.toChannel.toPull.map { pull =>
        new Parser(boundary, pull, bufferSize).result
      }
    }

}

case class Part(headers: Headers, bytes: ZStream[Any, Throwable, Byte]) {
  def contentType: Option[Header.ContentType] =
    headers.header(Header.ContentType)
  def mediaType: Option[MediaType]            =
    contentType.map(_.mediaType)

  // each part may be a multipart entity on its own
  def boundary: Option[Boundary] =
    contentType.flatMap(_.boundary)

  def toBody: Body = {
    val base = Body.fromStreamChunked(bytes)
    (this.mediaType, this.boundary) match {
      case (Some(mt), Some(boundary)) =>
        base.contentType(mt, boundary)
      case (Some(mt), None)           =>
        base.contentType(mt)
      case _                          =>
        base
    }
  }
}

object Mixed {
  val crlf = Chunk[Byte]('\r', '\n')

  class Parser(boundary: Boundary, pull: ZIO[Any, Throwable, Either[Any, Chunk[Byte]]], bufferSize: Int) {

    lazy val upstream: ZChannel[Any, Any, Any, Any, Throwable, Chunk[Byte], Any] = ZChannel
      .fromZIO(pull)
      .foldCauseChannel(
        ZChannel.refailCause(_),
        {
          case Left(_)      => ZChannel.unit
          case Right(chunk) => ZChannel.write(chunk) *> upstream
        },
      )

    def preamble(
      buff: Chunk[Byte] = Chunk.empty,
    ): ZChannel[Any, ZNothing, Chunk[Byte], Any, Throwable, Nothing, (Chunk[Byte], Boolean)] =
      buff.indexOfSlice(crlf) match {
        case -1  =>
          // have to read more input, can discard most of the buffer but must account to the case where current buffer's tail is the prefix of the next encapsulating/closing token
          val keep = buff.takeRight(crlf.size + boundary.closingBoundaryBytes.size - 1)
          ZChannel
            .readWithCause(
              in => preamble(keep ++ in),
              ZChannel.refailCause(_),
              done =>
                if (boundary.isClosing(buff))
                  ZChannel.succeed((Chunk.empty, true))
                else if (boundary.isEncapsulating(buff))
                  ZChannel.succeed((Chunk.empty, false))
                else
                  ZChannel.fail(new IllegalStateException("multipart/chunked body ended with no boundary")),
            )
        case idx =>
          val h    = buff.take(idx)
          val rest = buff.drop(idx + crlf.size)
          if (boundary.isClosing(h))
            ZChannel.succeed((rest, true))
          else if (boundary.isEncapsulating(h))
            ZChannel.succeed((rest, false))
          else
            preamble(rest)
      }

    def cont(buff: Chunk[Byte]): ZChannel[Any, ZNothing, Chunk[Byte], Any, Throwable, Part, Any] =
      parseHeaders(buff, Headers.empty).flatMap { case (headers, rest, closed) =>
        parseContent(headers, rest, closed)
      }

    def parseHeaders(
      buff: Chunk[Byte],
      res: Headers,
    ): ZChannel[Any, ZNothing, Chunk[Byte], Any, Throwable, Nothing, (Headers, Chunk[Byte], Boolean)] = {
      buff.indexOfSlice(crlf) match {
        case -1  =>
          // read more:
          ZChannel
            .readWithCause(
              in => parseHeaders(buff ++ in, res),
              ZChannel.refailCause(_),
              _ => ZChannel.fail(new IllegalStateException("multipart/chunked body ended while parsing part's headers")),
            )
        case 0   =>
          // empty line, hence no more headers
          ZChannel.succeed((res, buff.drop(crlf.size), false))
        case idx =>
          val h    = buff.take(idx)
          val rest = buff.drop(idx + crlf.size)
          // not sure this is actually valid
          if (boundary.isClosing(h))
            ZChannel.succeed((res, rest, true))
          else if (boundary.isEncapsulating(h)) // ditto
            ZChannel.succeed(res, rest, false)
          else {
            // todo: this is a private class, either avoid using it or refactor
            FormAST.Header
              .fromBytes(h.toArray, StandardCharsets.UTF_8)
              .map { case FormAST.Header(name, value) =>
                parseHeaders(rest, res.addHeader(name, value))
              }
              .getOrElse(parseHeaders(rest, res))
          }
      }
    }

    def subStream(
      pr: zio.Promise[Throwable, (Chunk[Byte], Boolean)],
      initialBuff: Chunk[Byte],
    ): ZStream[Any, Throwable, Byte] = {
      // when at beginning of line
      def parseBOL(
        buff: Chunk[Byte],
        pendingCrlf: Chunk[Byte],
        currLine: Chunk[Byte],
      ): ZChannel[Any, ZNothing, Chunk[Byte], Any, Throwable, Chunk[Byte], (Chunk[Byte], Boolean)] = {
        if (buff.size >= bufferSize)
          ZChannel.write(buff) *> parseBOL(Chunk.empty, pendingCrlf, currLine)
        else {
          currLine.indexOfSlice(crlf) match {
            case -1  =>
              // at this point we have a partial line, if this line is a prefix of the closing boundary we must keep it
              // otherwise we can stash it away or even emit it, making sure we keep enough bytes to match a CRLF exactly on the boundary
              if (boundary.closingBoundaryBytes.startsWith(currLine)) {
                ZChannel
                  .readWithCause(
                    in => parseBOL(buff, pendingCrlf, currLine ++ in),
                    err => ZChannel.write(buff) *> ZChannel.refailCause(err),
                    done => {
                      // still possible that the current line is encapsulating or closing boundary
                      if (boundary.isClosing(currLine))
                        ZChannel.write(buff) *> ZChannel.succeed((Chunk.empty, true))
                      else if (boundary.isEncapsulating(currLine))
                        ZChannel.write(buff) *> ZChannel.succeed((Chunk.empty, false))
                      else
                        ZChannel.write(buff) *> ZChannel.fail(
                          new IllegalStateException("multipart/chunked body ended with no boundary"),
                        )
                    },
                  )
              } else {
                // we're no longer at beginning of a line, hence no need to look for boundary until we encounter a new line
                val (h, t) = currLine.splitAt(currLine.size - crlf.size + 1)
                // also if we had a pending crlf we now know it's part of the content so we move it to the buffered part
                parseMOL(buff ++ pendingCrlf ++ h, t)
              }
            case idx =>
              val (h, rest) = currLine.splitAt(idx)
              // if we found a boundary it 'consumes' both the pending and trailing crlf, notice pending crlf is optional (i.e. empty part)
              if (boundary.isClosing(h))
                ZChannel.write(buff) *> ZChannel.succeed((rest.drop(crlf.size), true))
              else if (boundary.isEncapsulating(h))
                ZChannel.write(buff) *> ZChannel.succeed((rest.drop(crlf.size), false))
              else {
                // the crlf we just found can either be part of a following boundary or part of the content
                parseBOL(buff ++ pendingCrlf ++ h, crlf, rest.drop(crlf.size))
              }
          }
        }
      }
      // when at middle of line (after eliminating this line as a boundary)
      def parseMOL(
        buff: Chunk[Byte],
        currLine: Chunk[Byte],
      ): ZChannel[Any, ZNothing, Chunk[Byte], Any, Throwable, Chunk[Byte], (Chunk[Byte], Boolean)] = {
        if (buff.size >= bufferSize)
          ZChannel.write(buff) *> parseMOL(Chunk.empty, currLine)
        else {
          currLine.indexOfSlice(crlf) match {
            case -1 =>
              // keep just enough of the current line to match a crlf 'sitting' on the boundary,
              // stash or even emit the rest
              val (h, t) = currLine.splitAt(currLine.size - crlf.size + 1)
              ZChannel
                .readWithCause(
                  in => parseMOL(buff ++ h, t ++ in),
                  err => ZChannel.write(buff ++ h) *> ZChannel.refailCause(err),
                  done =>
                    ZChannel.write(buff ++ currLine) *> ZChannel.fail(
                      new IllegalStateException("multipart/chunked body ended with no boundary"),
                    ),
                )

            case idx =>
              // no need to check for boundary, just buffer and continue with parseBOL
              val (h, t) = currLine.splitAt(idx)
              parseBOL(buff ++ h, crlf, t.drop(crlf.size))
          }
        }
      }

      val ch: ZChannel[Any, ZNothing, Chunk[Byte], Any, Throwable, Chunk[Byte], Unit] =
        parseBOL(Chunk.empty, Chunk.empty, initialBuff)
          .foldCauseChannel(
            err => ZChannel.fromZIO(pr.failCause(err)) *> ZChannel.refailCause(err),
            tup => ZChannel.fromZIO(pr.succeed(tup)).unit,
          )
      val pl: ZPipeline[Any, Throwable, Byte, Byte]                                   = ch.toPipeline
      upstream.toStream >>> pl
    }

    def parseContent(
      headers: Headers,
      buff: Chunk[Byte],
      closed: Boolean,
    ): ZChannel[Any, ZNothing, Chunk[Byte], Any, Throwable, Part, Any] = {
      if (closed)
        ZChannel.write(Part(headers, ZStream.empty)) *>
          epilogue // ignore the epilogue
      else {
        ZChannel.unwrap {
          zio.Promise
            .make[Throwable, (Chunk[Byte], Boolean)]
            .map { pr =>
              val part = Part(headers, subStream(pr, buff))
              ZChannel.write(part) *>
                ZChannel
                  .fromZIO(pr.await)
                  .flatMap {
                    case (rest, true)  =>
                      epilogue
                    case (rest, false) =>
                      cont(rest)
                  }
            }
        }
      }
    }

    val epilogue: ZChannel[Any, ZNothing, Chunk[Byte], Any, ZNothing, Nothing, Any] =
      ZChannel.identity[ZNothing, Chunk[Byte], Any].drain

    val startCh: ZChannel[Any, ZNothing, Chunk[Byte], Any, Throwable, Chunk[Part], Any] =
      preamble(Chunk.empty).flatMap {
        case (leftover, false) =>
          cont(leftover)
        case (_, true)         =>
          epilogue
      }
        .mapOut(Chunk.single(_))
    val startPl: ZPipeline[Any, Throwable, Byte, Part]                                  = startCh.toPipeline

    val result: ZStream[Any, Throwable, Part] = upstream.toStream >>> startPl
  }

  def fromBody(body: Body, bufferSize: Int = 8192): Option[Mixed] = {
    body.boundary.map { b =>
      Mixed(body.asStream, b, bufferSize)
    }
  }

  def fromParts(parts: ZStream[Any, Throwable, Part], boundary: Boundary, bufferSize: Int = 8192): Mixed = {
    val sep   = boundary.encapsulationBoundaryBytes ++ crlf
    val term  = boundary.closingBoundaryBytes ++ crlf
    val bytes =
      parts.flatMap { case Part(headers, bytes) =>
        val headersBytes: ZStream[Any, CharacterCodingException, Byte] = ZStream
          .fromIterable(headers)
          .map { h =>
            s"${h.headerName}: ${h.renderedValue}\r\n"
          } >>>
          ZPipeline.utf8Encode

        ZStream
          .concatAll(
            Chunk(ZStream.fromChunk(sep), headersBytes, ZStream.fromChunk(crlf), bytes, ZStream.fromChunk(crlf)),
          )
      }
        .concat(ZStream.fromChunk(term))

    Mixed(bytes, boundary, bufferSize)
  }

  def fromPartsUUID(parts: ZStream[Any, Throwable, Part], bufferSize: Int = 8192): ZIO[Any, Nothing, Mixed] = {
    Boundary.randomUUID
      .map(fromParts(parts, _, bufferSize))
  }

}
