package zio.http

import zio.test._
import zio.{Chunk, Scope, ZIO, ZNothing}

import zio.stream.{ZChannel, ZPipeline, ZStream}

import zio.http.Headers.fromIterable
import zio.http.multipart.mixed.MultipartMixed
import zio.http.multipart.mixed.MultipartMixed.Part

object MultipartMixedSpec extends ZIOHttpSpec {

  override def spec: Spec[TestEnvironment with Scope, Any] = mixedSuite

  val mixedSuite = suiteAll("multipart/mixed") {

    val defaultSep = "simple boundary"

    suiteAll("empty") {
      val empty = MultipartMixed.fromParts(ZStream.empty, Boundary(defaultSep))

      test("has no parts") {
        empty.parts.runCollect.map { collected =>
          zio.test.assert(collected)(Assertion.isEmpty)
        }
      }

      test("empty no preamble, no epilogue") {
        val body = Body.fromString(s"--${defaultSep}--").contentType(MediaType.multipart.`mixed`, Boundary(defaultSep))
        val mpm  = MultipartMixed.fromBody(body)

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
        val mpm  = MultipartMixed.fromBody(body)

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
      val mpm          = MultipartMixed.fromBody(rechunk(11)(body))
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

      test("as TestCase") {
        val boundary = Boundary("simple boundary")
        val testCase = new TestCase(
          utf8("""This is the preamble.  It is to be ignored, though it
                 |is a handy place for mail composers to include an
                 |explanatory note to non-MIME compliant readers.""".stripMargin),
          boundary,
          Chunk(
            new TestPart(
              Headers(),
              utf8("""This is implicitly typed plain ASCII text.
                     |It does NOT end with a linebreak.""".stripMargin),
              boundary,
            ),
            new TestPart(
              Headers("Content-type", "text/plain; charset=us-ascii"),
              utf8("""This is explicitly typed plain ASCII text.
                     |It DOES end with a linebreak.
                     |""".stripMargin),
              boundary,
            ),
          ),
          utf8("This is the epilogue.  It is also to be ignored."),
          15,
          gens.breaker.fixed(32),
        )
        testCase.runTests
      }
    }

    test("empty test case") {
      val tc = TestCase(
        Chunk(),
        Boundary("(((b010903d-0d3d-4d22-b5c4-f3f0bf574468)))", Charsets.Utf8),
        Chunk(),
        Chunk(),
        7412,
        gens.breaker.alternating(Chunk(10, 10)),
        Chunk(45, 45, 40, 40, 40, 98, 48, 49, 48, 57, 48, 51, 100, 45, 48, 100, 51, 100, 45, 52, 100, 50, 50, 45, 98,
          53, 99, 52, 45, 102, 51, 102, 48, 98, 102, 53, 55, 52, 52, 54, 56, 41, 41, 41, 45, 45),
      )

      tc.testFromRepr
    }

    test("closing boundary trailing crlf is split between two reads") {
      val tc = TestCase(
        Chunk(44),
        Boundary("(((ba4584f8-ca16-457a-921e-adf1a01996a7)))", Charsets.Utf8),
        Chunk(
          TestPart(
            Headers(),
            Chunk(),
            Chunk(45, 45, 40, 40, 40, 98, 97, 52, 53, 56, 52, 102, 56, 45, 99, 97, 49, 54, 45, 52, 53, 55, 97, 45, 57,
              50, 49, 101, 45, 97, 100, 102, 49, 97, 48, 49, 57, 57, 54, 97, 55, 41, 41, 41, 13, 10, 13, 10, 13, 10),
          ),
        ),
        Chunk(-59, 119, 33, 117, -6, 15, 101),
        5711,
        gens.breaker.alternating(Chunk(10)),
        Chunk(44, 13, 10, 45, 45, 40, 40, 40, 98, 97, 52, 53, 56, 52, 102, 56, 45, 99, 97, 49, 54, 45, 52, 53, 55, 97,
          45, 57, 50, 49, 101, 45, 97, 100, 102, 49, 97, 48, 49, 57, 57, 54, 97, 55, 41, 41, 41, 13, 10, 13, 10, 13, 10,
          45, 45, 40, 40, 40, 98, 97, 52, 53, 56, 52, 102, 56, 45, 99, 97, 49, 54, 45, 52, 53, 55, 97, 45, 57, 50, 49,
          101, 45, 97, 100, 102, 49, 97, 48, 49, 57, 57, 54, 97, 55, 41, 41, 41, 45, 45, 13, 10, -59, 119, 33, 117, -6,
          15, 101),
      )
      tc.testFromRepr
    }

    test("breaker.alternating(10,10)") {
      val inp     = ZStream.range(0, 40, 9).map(_.toByte)
      val breaker = gens.breaker.alternating(Chunk(10, 10))

      inp.via(breaker.breakerPl).chunks.runCollect.map { collected =>
        zio.test.assert(collected) {
          Assertion.equalTo {
            Chunk(
              Chunk.range(0, 10),
              Chunk.range(10, 20),
              Chunk.range(20, 30),
              Chunk.range(30, 40),
            )
              .map(_.map(_.toByte))
          }
        }
      }
    }

    test("property") {
      check(gens.genTestCase) { testCase =>
        zio.Console.printLine(testCase) *> testCase.runTests
      }
    } @@ TestAspect.shrinks(0)

    suiteAll("nested") {
      test("single(single)") {
        val inner = new TestCase(utf8("few preamble bytes"), Boundary("inner_boundary"),
          Chunk(
            new TestPart(Headers(Header.ContentType(MediaType.text.`plain`)),
              utf8("some textual content\r\nmake it multiline textual content!\r\nbut not ending with a CRLF"),
              Boundary("inner_boundary")
            )
          ),
          utf8("an epilogue, nothing special here"),
          1024,
          gens.breaker.fixed(512)
        )
        val outer = new TestCase(utf8("the outer preamble, make it multiline\r\nhere it is!\r\n"), Boundary("outer_boundary"),
          Chunk(
            new TestPart(Headers(Header.ContentType(MediaType.multipart.`mixed`, Some(inner.boundary)), Header.Custom("author", "me!")),
              inner.binaryRepr,
              Boundary("outer_boundary")
            )
          ),
          Chunk.empty,
          1024,
          gens.breaker.fixed(512)
        )

        val innerTests = inner.runTests.debug("inner")
        val outerTests = outer.runTests.debug("outer")
        val nestedTests = {
          val materialized = {
            val outerMpm = outer.multipartFromRepr
            outerMpm
              .parts
              .zipWithIndex
              .mapZIO { case (p, idx) =>
                for {
                  innerMpm <- ZIO.fromOption(MultipartMixed.fromBody(p.toBody)).orElseFail(new IllegalStateException(s"outer part #$idx is not a valid multipart/mixed"))
                  innerParts <- innerMpm.parts.zipWithIndex.mapZIO { case (p1, j) =>
                      p1.bytes.runCollect.map { bytes =>
                        new TestPart(p1.headers, bytes, innerMpm.boundary).withRawHeaders
                      }
                    }
                    .runCollect
                } yield {
                  innerParts
                }
              }
              .runCollect
          }

          val expected = Chunk(
            Chunk(
              inner.parts.head.withRawHeaders
            )
          )

          materialized.map{collected =>
            zio.test.assert(collected)(Assertion.equalTo(expected))
          }
        }
        .debug("nested")

        (innerTests <*> outerTests <*> nestedTests)
          .map{
            case (i, o, n) =>
              i && o && n
          }
      }
    }
  }

  def utf8(str: String): Chunk[Byte] =
    Chunk.fromArray(str.getBytes(Charsets.Utf8))

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

  def checkRoundtrip(mpm: MultipartMixed) = {
    for {
      parsed0 <- mpm.parts.mapZIO { p =>
        p.toBody.asChunk
          .map(p.headers -> _)
      }.runCollect
      mpm2 = MultipartMixed.fromParts(
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

  case class TestPart(headers: Headers, contents: Chunk[Byte], binaryRepr: Chunk[Byte]) {
    def this(headers: Headers, contents: Chunk[Byte], boundary: Boundary) =
      this(
        headers,
        contents, {
          val headersStr  = headers
            .map(h => s"${h.headerName}: ${h.renderedValue}\r\n")
            .mkString
          val headerBytes = Chunk.fromArray(headersStr.getBytes(Charsets.Utf8))

          boundary.encapsulationBoundaryBytes ++
            MultipartMixed.crlf ++
            headerBytes ++
            MultipartMixed.crlf ++
            contents ++
            MultipartMixed.crlf
        },
      )

    def toPart = MultipartMixed.Part(headers, ZStream.fromChunk(contents))

    def withRawHeaders = {
      val customs = headers.flatMap {h =>
        headers.rawHeader(h.headerType).map(Header.Custom(h.headerName, _))
      }
      copy(headers = Headers(customs))
    }
  }
  case class TestCase(
    preamble: Chunk[Byte],
    boundary: Boundary,
    parts: Chunk[TestPart],
    epilogue: Chunk[Byte],
    bufferSize: Int,
    breaker: Breaker,
    binaryRepr: Chunk[Byte],
  ) {
    def this(
      preamble: Chunk[Byte],
      boundary: Boundary,
      parts: Chunk[TestPart],
      epilogue: Chunk[Byte],
      bufferSize: Int,
      breaker: Breaker,
    ) =
      this(
        preamble,
        boundary,
        parts,
        epilogue,
        bufferSize,
        breaker, {
          {
            preamble match {
              case pb if pb.nonEmpty =>
                pb ++ MultipartMixed.crlf
              case _                 =>
                Chunk.empty
            }
          } ++
            parts.flatMap(_.binaryRepr) ++
            boundary.closingBoundaryBytes ++ {
              epilogue match {
                case epBytes if epBytes.nonEmpty =>
                  MultipartMixed.crlf ++ epBytes
                case _                           =>
                  Chunk.empty
              }
            }
        },
      )

    def strip = new TestCase(Chunk.empty, boundary, parts, Chunk.empty, bufferSize, breaker)

    def multipartFromParts = {
      val res0 = MultipartMixed.fromParts(ZStream.fromChunk(parts).map(_.toPart), boundary, bufferSize)
      res0.copy(source = res0.source >>> breaker.breakerPl)
    }
    def multipartFromRepr  = MultipartMixed(ZStream.fromChunk(binaryRepr) >>> breaker.breakerPl, boundary, bufferSize)

    def testFromParts = {
      multipartFromParts.source.runCollect
        .map(zio.test.assert(_)(Assertion.equalTo(this.strip.binaryRepr)))
    }

    def testFromRepr = {
      multipartFromRepr.parts.mapZIO { p =>
        p.bytes.runCollect
          .map(new TestPart(fromIterable(p.headers.toVector), _, boundary).withRawHeaders)
      }.runCollect
        .map{ collected =>
          val partsWithRowHeaders = parts.map(_.withRawHeaders)
          zio.test.assert(collected)(Assertion.equalTo(partsWithRowHeaders))
        }
    }

    def runTests =
      testFromRepr.zipWith(testFromParts)(_ && _)
  }

  // thin wrapper over pipeline, gives a usable string repr
  class Breaker(val breakerPl: ZPipeline[Any, Nothing, Byte, Byte], override val toString: String)

  object gens {
    val genBytes = Gen.chunkOf(Gen.byte)

    val genHeaderName  = Gen.alphaNumericString.filter(_.nonEmpty)
    val genHeaderValue = Gen.alphaNumericString.map(_.filterNot(Set('\r', '\n'))).filter(_.nonEmpty)

    val genHeader = for {
      name <- genHeaderName
      v    <- genHeaderValue
    } yield {
      Header.Custom(name, v)
    }

    val genBoundary = Gen.fromZIO(Boundary.randomUUID)

    def genTestPart(boundary: Boundary) = for {
      headers <- Gen.chunkOf(genHeader)
      content <- genBytes
    } yield {
      new TestPart(Headers(headers), content, boundary)
    }

    object breaker {
      def fixed(n: Int): Breaker      = new Breaker(ZPipeline.rechunk[Byte](n), s"fixed($n)")
      val genFixed: Gen[Any, Breaker] = Gen.fromIterable(Seq(10, 15, 30, 100, 256)).map(fixed)

      def alternatingPl(sizes: Chunk[Int]): ZPipeline[Any, Nothing, Byte, Byte] = {

        def ch(
          buffered: Chunk[Byte],
          leftovers: Chunk[Byte],
          rem: Int,
          nextSz: Iterator[Int],
        ): ZChannel[Any, ZNothing, Chunk[Byte], Any, Nothing, Chunk[Byte], Any] = {
          if (0 == rem)
            ZChannel.write(buffered) *>
              ch(Chunk.empty, leftovers, nextSz.next(), nextSz)
          else if (leftovers.nonEmpty)
            ch(buffered ++ leftovers.take(rem), leftovers.drop(rem), (rem - leftovers.size) max 0, nextSz)
          else
            ZChannel
              .readWithCause(
                in => ch(buffered, in, rem, nextSz),
                ZChannel.refailCause(_),
                _ => if (buffered.nonEmpty) ZChannel.write(buffered) else ZChannel.unit,
              )
        }

        ZPipeline.suspend {
          val it                                                                            = Iterator
            .continually(sizes)
            .flatMap(_.iterator)
          val channel: ZChannel[Any, ZNothing, Chunk[Byte], Any, Nothing, Chunk[Byte], Any] =
            ch(Chunk.empty, Chunk.empty, it.next(), it)
          val pl: ZPipeline[Any, Nothing, Byte, Byte]                                       = channel.toPipeline
          pl
        }
      }
      def alternating(sizes: Chunk[Int]): Breaker =
        new Breaker(alternatingPl(sizes), s"alternating(${sizes.mkString("Chunk(", ",", ")")})")
      def genAlternating: Gen[Any, Breaker]       = Gen
        .chunkOf(Gen.fromIterable(Seq(10, 15, 30, 100, 256)))
        .filter(_.nonEmpty)
        .map(alternating(_))

      val asIs                       = new Breaker(ZPipeline.identity[Byte], "asIs")
      val genAsIs: Gen[Any, Breaker] = Gen.const(asIs)

      val genBreaker: Gen[Any, Breaker] = Gen.oneOf(genFixed, genAlternating, genAsIs)
    }

    val genTestCase =
      for {
        boundary   <- genBoundary
        preamble   <- Gen.oneOf(Gen.const(Chunk.empty), genBytes)
        parts      <- Gen.chunkOf(genTestPart(boundary))
        epilogue   <- Gen.oneOf(Gen.const(Chunk.empty), genBytes)
        bufferSize <- Gen.int(10, 8 * 1024)
        breaker    <- breaker.genBreaker
      } yield {
        new TestCase(preamble, boundary, parts, epilogue, bufferSize, breaker)
      }
  }

}
