package zio.http.multipart.mixed

import zio.test.{Assertion, Spec, TestEnvironment}
import zio.{Chunk, Scope, ZIO}

import zio.stream.ZStream

import zio.http.{Body, Boundary, MediaType, ZIOHttpSpec}

object MixedSpec extends ZIOHttpSpec {

  override def spec: Spec[TestEnvironment with Scope, Any] = mixedSuite

  val mixedSuite = suiteAll("multipart/mixed") {

    val defaultSep = "simple boundary"

    def checkRoundtrip(mpm: Mixed) = {
      for {
        parsed0 <- mpm.parts.mapZIO { p =>
          p.toBody.asChunk
            .map(p.headers -> _)
        }.runCollect
        mpm2 = Mixed.fromParts(
          ZStream
            .fromChunk(parsed0)
            .map { case (header, bytes) =>
              Part(header, ZStream.fromChunk(bytes))
            },
          mpm.boundary,
          mpm.bufferSize,
        )

        parsed1 <- mpm2.parts.mapZIO { p =>
          p.toBody.asChunk
            .map(p.headers -> _)
        }.runCollect
      } yield {
        zio.test.assert(parsed1)(Assertion.equalTo(parsed0))
      }
    }

    suiteAll("empty") {
      val empty = Mixed.fromParts(ZStream.empty, Boundary(defaultSep))

      test("has no parts") {
        empty.parts.runCollect.map { collected =>
          zio.test.assert(collected)(Assertion.isEmpty)
        }
      }

      test("emoty no preamble, no epilogue") {
        val body = Body.fromString(s"--${defaultSep}--").contentType(MediaType.multipart.`mixed`, Boundary(defaultSep))
        val mpm  = Mixed.fromBody(body)

        ZIO
          .fromOption(mpm)
          .flatMap(_.parts.runCollect)
          .map { collected =>
            zio.test.assert(collected)(Assertion.isEmpty)
          }
      }

      test("empty no preamble, with epilogue") {
        val body = Body
          .fromString(s"--${defaultSep}--\r\nsome nasty epilogue")
          .contentType(MediaType.multipart.`mixed`, Boundary(defaultSep))
        val mpm  = Mixed.fromBody(body)

        ZIO
          .fromOption(mpm)
          .flatMap(_.parts.runCollect)
          .map { collected =>
            zio.test.assert(collected)(Assertion.isEmpty)
          }
      }
    }

    suiteAll("rfc-1341 sample") {
      val msg =
        """
          |
          |This is the preamble.  It is to be ignored, though it
          |is a handy place for mail composers to include an
          |explanatory note to non-MIME compliant readers.
          |--simple boundary
          |
          |This is implicitly typed plain ASCII text.
          |It does NOT end with a linebreak.
          |--simple boundary
          |Content-type: text/plain; charset=us-ascii
          |
          |This is explicitly typed plain ASCII text.
          |It DOES end with a linebreak.
          |
          |--simple boundary--
          |This is the epilogue.  It is also to be ignored.
          |""".stripMargin.replaceAll("\n", "\r\n")

      val body         = Body.fromString(msg).contentType(MediaType.multipart.`mixed`, Boundary(defaultSep))
      val mpm          = Mixed.fromBody(rechunk(11)(body))
      val expectedStrs = Chunk(
        """|This is implicitly typed plain ASCII text.
           |It does NOT end with a linebreak.""".stripMargin.replaceAll("\n", "\r\n"),
        """|This is explicitly typed plain ASCII text.
           |It DOES end with a linebreak.
           |""".stripMargin.replaceAll("\n", "\r\n"),
      )

      test("default 8k buffer") {
        ZIO
          .fromOption(mpm)
          .flatMap {
            _.parts.mapZIO(_.toBody.asString).runCollect
          }
          .map { collected =>
            zio.test.assert(collected)(Assertion.equalTo(expectedStrs))
          }
      }

      test("extremely small buffer") {
        ZIO
          .fromOption(mpm)
          .map(_.copy(bufferSize = 5))
          .flatMap {
            _.parts.mapZIO(_.toBody.asString).runCollect
          }
          .map { collected =>
            zio.test.assert(collected)(Assertion.equalTo(expectedStrs))
          }
      }

      test("roundtrip default buffer size") {
        ZIO
          .fromOption(mpm)
          .flatMap(checkRoundtrip(_))
      }

      test("roundtrip extremely small buffer") {
        ZIO
          .fromOption(mpm)
          .map(_.copy(bufferSize = 5))
          .flatMap(checkRoundtrip(_))
      }
    }
  }

  def rechunk(chunkSz: Int)(body: Body): Body = {
    val rechunkedStream = body.asStream.rechunk(chunkSz)
    val res0            = Body
      .fromStreamChunked(rechunkedStream)

    (body.mediaType, body.boundary) match {
      case (None, None)        => res0
      case (Some(mt), None)    => res0.contentType(mt)
      case (Some(mt), Some(b)) => res0.contentType(mt, b)
      case (None, Some(b))     =>
        sys.error("r u joking me?!?!")
    }

  }

}
