package zio.http.multipart.mixed

import java.nio.charset.{CharacterCodingException, StandardCharsets}

import scala.annotation.tailrec

import zio.{Chunk, ZIO, ZNothing}

import zio.stream.{ZChannel, ZPipeline, ZStream}

import zio.http._
import zio.http.internal.FormAST
import zio.http.multipart.mixed.MultipartMixed.Parser

final case class MultipartMixed(source: ZStream[Any, Throwable, Byte], boundary: Boundary, bufferSize: Int = 8192) {

  def parts: ZStream[Any, Throwable, MultipartMixed.Part] = ZStream
    .unwrapScoped[Any] {
      source.toChannel.toPull.map { pull =>
        new Parser(boundary, pull, bufferSize).result
      }
    }

}

object MultipartMixed {
  final case class Part(headers: Headers, bytes: ZStream[Any, Throwable, Byte]) {
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

  private[http] val crlf = Chunk[Byte]('\r', '\n')

  private[http] final class Parser(
    boundary: Boundary,
    pull: ZIO[Any, Throwable, Either[Any, Chunk[Byte]]],
    bufferSize: Int,
  ) {

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
      @tailrec def parseBody(
        buff: Chunk[Byte],
        pendingCrlf: Chunk[Byte],
        currLine: Chunk[Byte],
        seekingBoundary: Boolean,
      ): ZChannel[Any, ZNothing, Chunk[Byte], Any, Throwable, Chunk[Byte], (Chunk[Byte], Boolean)] = {
        if (buff.size >= bufferSize)
          ZChannel.write(buff) *> parseBodyAux(Chunk.empty, pendingCrlf, currLine, seekingBoundary)
        else {
          currLine.indexOfSlice(crlf) match {
            case -1                     =>
              // at this point we have a partial line, if this line is a prefix of the closing boundary we must keep it
              // otherwise we can stash it away or even emit it, making sure we keep enough bytes to match a CRLF exactly on the boundary
              // notice we're not actually looking for a prefix, but instead make sure we read enough bytes to rule out the possibility of matching a boundary (encapsulating or closing) followed by a crlf.
              // this approach ensures we can handle the case of a boundary followed by a split crlf (\r in this read, \n in the next), we have to make sure to keep buffering in this case as we don't have enough bytes to rule out a boundary.
              if (seekingBoundary && currLine.size < (boundary.closingBoundaryBytes.size + crlf.size)) {
                ZChannel
                  .readWithCause(
                    in => parseBodyAux(buff, pendingCrlf, currLine ++ in, true),
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
                if (t != currLine) {
                  // also if we had a pending crlf we now know it's part of the content so we move it to the buffered part
                  parseBody(buff ++ pendingCrlf ++ h, Chunk.empty, t, false)
                } else {
                  ZChannel.readWithCause(
                    in => parseBodyAux(buff ++ pendingCrlf ++ h, Chunk.empty, t ++ in, false),
                    err => ZChannel.write(buff ++ crlf ++ currLine) *> ZChannel.refailCause(err),
                    done =>
                      ZChannel.write(buff ++ crlf ++ currLine) *> ZChannel.fail(
                        new IllegalStateException("multipart/chunked body ended with no boundary"),
                      ),
                  )
                }
              }
            case idx if seekingBoundary => // potential boundary
              val (h, rest) = currLine.splitAt(idx)
              // if we found a boundary it 'consumes' both the pending and trailing crlf, notice pending crlf is optional (i.e. empty part)
              if (boundary.isClosing(h))
                ZChannel.write(buff) *> ZChannel.succeed((rest.drop(crlf.size), true))
              else if (boundary.isEncapsulating(h))
                ZChannel.write(buff) *> ZChannel.succeed((rest.drop(crlf.size), false))
              else {
                // the crlf we just found can either be part of a following boundary or part of the content
                val nextLine = rest.drop(crlf.size)
                parseBody(buff ++ pendingCrlf ++ h, crlf, nextLine, true)
              }
            case idx                    => // plain content
              // no need to check for boundary, just buffer and continue with parseBOL
              val (h, t) = currLine.splitAt(idx)
              parseBody(buff ++ h, crlf, t.drop(crlf.size), true)
          }
        }
      }

      // escape the tailrec compilation error
      def parseBodyAux(
        buff: Chunk[Byte],
        pendingCrlf: Chunk[Byte],
        currLine: Chunk[Byte],
        seekingBoundary: Boolean,
      ): ZChannel[Any, ZNothing, Chunk[Byte], Any, Throwable, Chunk[Byte], (Chunk[Byte], Boolean)] =
        parseBody(buff, pendingCrlf, currLine, seekingBoundary)

      val ch: ZChannel[Any, ZNothing, Chunk[Byte], Any, Throwable, Chunk[Byte], Unit] =
        parseBody(Chunk.empty, Chunk.empty, initialBuff, seekingBoundary = true)
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

  def fromBody(body: Body, bufferSize: Int = 8192): Option[MultipartMixed] = {
    body.boundary.map { b =>
      MultipartMixed(body.asStream, b, bufferSize)
    }
  }

  def fromParts(parts: ZStream[Any, Throwable, Part], boundary: Boundary, bufferSize: Int = 8192): MultipartMixed = {
    val sep   = boundary.encapsulationBoundaryBytes ++ crlf
    val term  = boundary.closingBoundaryBytes
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

    MultipartMixed(bytes, boundary, bufferSize)
  }

  def fromPartsUUID(parts: ZStream[Any, Throwable, Part], bufferSize: Int = 8192): ZIO[Any, Nothing, MultipartMixed] = {
    Boundary.randomUUID
      .map(fromParts(parts, _, bufferSize))
  }

}
