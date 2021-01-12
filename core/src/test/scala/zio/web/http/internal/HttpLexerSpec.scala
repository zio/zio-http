package zio.web.http.internal

import zio.random.Random
import zio.test.Assertion._
import zio.test.TestAspect.samples
import zio.test._
import zio.web.http.internal.HttpLexer.HeaderParseError._
import zio.web.http.internal.HttpLexer.{ TokenChars, parseHeaders }
import zio.web.http.model.{ Method, Version }
import zio.{ Chunk, Task }

import java.io.{ Reader, StringReader }
import scala.util.{ Random => ScRandom }

object HttpLexerSpec extends DefaultRunnableSpec {

  override def spec =
    suite("All tests")(startLineSuite, headerSuite)

  def startLineSuite = suite("HTTP start line parsing")(
    test("check OPTIONS method") {
      val (method, _, _) = HttpLexer.parseStartLine(new StringReader("OPTIONS /hello.htm HTTP/1.1\r\nheaders and body"))
      assert(method)(equalTo(Method.OPTIONS))
    },
    test("check GET method") {
      val (method, _, _) = HttpLexer.parseStartLine(new StringReader("GET /hello.htm HTTP/1.1\r\nheaders and body"))
      assert(method)(equalTo(Method.GET))
    },
    test("check HEAD method") {
      val (method, _, _) = HttpLexer.parseStartLine(new StringReader("HEAD /hello.htm HTTP/1.1\r\nheaders and body"))
      assert(method)(equalTo(Method.HEAD))
    },
    test("check POST method") {
      val (method, _, _) = HttpLexer.parseStartLine(new StringReader("POST /hello.htm HTTP/1.1\r\nheaders and body"))
      assert(method)(equalTo(Method.POST))
    },
    test("check PUT method") {
      val (method, _, _) = HttpLexer.parseStartLine(new StringReader("PUT /hello.htm HTTP/1.1\r\nheaders and body"))
      assert(method)(equalTo(Method.PUT))
    },
    test("check PATCH method") {
      val (method, _, _) = HttpLexer.parseStartLine(new StringReader("PATCH /hello.htm HTTP/1.1\r\nheaders and body"))
      assert(method)(equalTo(Method.PATCH))
    },
    test("check DELETE method") {
      val (method, _, _) = HttpLexer.parseStartLine(new StringReader("DELETE /hello.htm HTTP/1.1\r\nheaders and body"))
      assert(method)(equalTo(Method.DELETE))
    },
    test("check TRACE method") {
      val (method, _, _) = HttpLexer.parseStartLine(new StringReader("TRACE /hello.htm HTTP/1.1\r\nheaders and body"))
      assert(method)(equalTo(Method.TRACE))
    },
    test("check CONNECT method") {
      val (method, _, _) = HttpLexer.parseStartLine(new StringReader("CONNECT /hello.htm HTTP/1.1\r\nheaders and body"))
      assert(method)(equalTo(Method.CONNECT))
    },
    test("check HTTP 1.1 version") {
      val (_, _, version) = HttpLexer.parseStartLine(new StringReader("POST /hello.htm HTTP/1.1\r\nheaders and body"))
      assert(version)(equalTo(Version.V1_1))
    },
    test("check HTTP 2 version") {
      val (_, _, version) = HttpLexer.parseStartLine(new StringReader("POST /hello.htm HTTP/2.0\r\nheaders and body"))
      assert(version)(equalTo(Version.V2))
    },
    test("check long URI") {
      val longString = "a" * 2021
      val (_, _, version) = HttpLexer.parseStartLine(
        new StringReader(s"POST https://absolute.uri/$longString HTTP/2.0\r\nheaders and body")
      )
      assert(version)(equalTo(Version.V2))
    },
    testM("check too long URI") {
      val longString = "a" * 2028
      val result = Task(
        HttpLexer
          .parseStartLine(new StringReader(s"POST https://absolute.uri/$longString HTTP/2.0\r\nheaders and body"))
      ).run
      assertM(result)(fails(isSubtype[IllegalStateException](hasMessage(equalTo("Malformed HTTP start-line")))))
    },
    testM("check corrupted HTTP request (no space)") {
      val result = Task(HttpLexer.parseStartLine(new StringReader("POST/hello.htm HTTP/2.0\r\nheaders and body"))).run
      assertM(result)(fails(isSubtype[IllegalStateException](hasMessage(equalTo("Malformed HTTP start-line")))))
    },
    testM("check corrupted HTTP request (double CR)") {
      val result =
        Task(HttpLexer.parseStartLine(new StringReader("POST /hello.htm HTTP/2.0\r\r\nheaders and body"))).run
      assertM(result)(fails(isSubtype[IllegalStateException](hasMessage(equalTo("Malformed HTTP start-line")))))
    },
    testM("check corrupted HTTP request (random string)") {
      val result = Task(HttpLexer.parseStartLine(new StringReader(new ScRandom().nextString(2048)))).run
      assertM(result)(fails(isSubtype[IllegalStateException](hasMessage(equalTo("Malformed HTTP start-line")))))
    },
    testM("check corrupted HTTP request (very long random string)") {
      val result = Task(HttpLexer.parseStartLine(new StringReader(new ScRandom().nextString(4096000)))).run
      assertM(result)(fails(isSubtype[IllegalStateException](hasMessage(equalTo("Malformed HTTP start-line")))))
    },
    testM("check invalid HTTP method") {
      val result = Task(HttpLexer.parseStartLine(new StringReader("GRAB /hello.htm HTTP/2.0\r\nheaders and body"))).run
      assertM(result)(fails(isSubtype[IllegalArgumentException](hasMessage(equalTo("Unable to handle method: GRAB")))))
    },
    testM("check invalid HTTP version") {
      val result = Task(HttpLexer.parseStartLine(new StringReader("POST /hello.htm HTTP2.0\r\nheaders and body"))).run
      assertM(result)(
        fails(isSubtype[IllegalArgumentException](hasMessage(equalTo("Unable to handle version: HTTP2.0"))))
      )
    },
    testM("check empty input") {
      val result = Task(HttpLexer.parseStartLine(new StringReader(""))).run
      assertM(result)(fails(isSubtype[IllegalStateException](hasMessage(equalTo("Malformed HTTP start-line")))))
    },
    test("check URI") {
      val (_, uri, _) = HttpLexer.parseStartLine(new StringReader("OPTIONS /hello.htm HTTP/1.1\r\nheaders and body"))
      assert(uri.toString)(equalTo("/hello.htm"))
    }
  )

  final case class HeaderLines(s: String) extends AnyVal {
    def toStringWithCRLF: String = s.stripMargin.replaceAll("\n", "\r\n") + "\r\n\r\n"
  }

  private val TestHeaderSizeLimit = 50

  private lazy val failureScenarios =
    Gen.fromIterable(
      Seq(
        ""                              -> UnexpectedEnd,
        "\r"                            -> ExpectedLF(-1),
        "a"                             -> UnexpectedEnd,
        "a:"                            -> UnexpectedEnd,
        "a: "                           -> UnexpectedEnd,
        "a: b"                          -> UnexpectedEnd,
        "a: b\r"                        -> ExpectedLF(-1),
        "a: b\r\n"                      -> UnexpectedEnd,
        "a: b\r\na"                     -> UnexpectedEnd,
        "space-after-header-name : ..." -> InvalidCharacterInName(' '),
        "X-EnormousHeader: " +
          "x" * TestHeaderSizeLimit -> HeaderTooLarge,
        // TODO: handling of this case could be improved, as the spec allows for
        //       multiline headers, even though that construct is deprecated
        // "A server that receives an obs-fold in a request message that is not
        //  within a message/http container MUST either reject the message by
        //  sending a 400 (Bad Request), preferably with a representation
        //  explaining that obsolete line folding is unacceptable, or replace
        //  each received obs-fold with one or more SP octets prior to
        //  interpreting the field value or forwarding the message downstream."
        // https://tools.ietf.org/html/rfc7230#section-3.2.4
        multilineHeader.toStringWithCRLF -> InvalidCharacterInName(' ')
      )
    )

  private lazy val multilineHeader =
    HeaderLines("""foo: obsolete
                  |     multiline
                  |     header""")

  private lazy val headerName = Gen.string1(Gen.elements(TokenChars: _*))

  private lazy val headerValue = Gen.string(whitespaceOrPrintableOrExtended).map(_.trim)

  private lazy val whitespaceOrPrintableOrExtended =
    Gen.elements('\t' +: (' ' to 0xff): _*)

  private lazy val optionalWhitespace = Gen.string(Gen.elements(' ', '\t'))

  private def duplicateSome[R1, R2 >: R1, A](
    as: Iterable[A],
    factor: Gen[R2, Int]
  ): Gen[R2, List[A]] = {
    val listOfGenDuplicates: Iterable[Gen[R2, List[A]]] =
      as.map { a =>
        Gen
          .const(a)
          .crossWith(factor)((a, factor) => List.fill(factor)(a))
      }
    Gen
      .crossAll(listOfGenDuplicates)
      .map(_.flatten)
  }

  private lazy val duplicationFactor =
    Gen.weighted(
      Gen.const(1) -> 90,
      Gen.const(2) -> 8,
      Gen.const(3) -> 2
    )

  private def selectSome[A](as: Iterable[A], decide: Gen[Random, Boolean]) =
    as.map(Gen.const(_))
      .foldLeft(Gen.const(List.empty[A]): Gen[Random, List[A]]) { (acc, genA) =>
        for {
          decision <- decide
          a        <- genA
          as       <- acc
        } yield if (decision) a :: as else as
      }

  private def selectSome1[A](as: Iterable[A], decide: Gen[Random, Boolean] = Gen.boolean) =
    selectSome(as, decide).flatMap {
      case Nil        => Gen.elements(as.toSeq: _*).map(List(_))
      case selectedAs => Gen.const(selectedAs)
    }

  val testGen =
    for {
      distinctHeaderNames <- Gen.setOf(headerName)
      headerNamesWithDups <- duplicateSome(distinctHeaderNames, duplicationFactor)
      headerNames         <- Gen.fromRandom(_.shuffle(headerNamesWithDups))
      headerValues        <- Gen.listOfN(headerNames.size)(headerValue)
      owss                <- Gen.listOfN(headerNames.size * 2)(optionalWhitespace)
      body                <- Gen.alphaNumericString
      absentHeaderNames   <- Gen.setOf(headerName)
      headersToExtract <- selectSome1(distinctHeaderNames ++ absentHeaderNames)
                           .map(headerNames => headerNames.map(_.toLowerCase).distinct)
    } yield {
      val pairedOwss = owss.grouped(2).map(_.toSeq).toSeq

      val headerLines =
        headerNames
          .zip(headerValues)
          .zip(pairedOwss)
          .map {
            case ((name, value), Seq(leftOws, rightOws)) =>
              s"$name:$leftOws$value$rightOws\r\n"
          }

      val headerNameToValuesMap: Map[String, List[String]] =
        headerNames
          .zip(headerValues)
          .groupBy(_._1.toLowerCase)
          .map { case (k, kvs) => k -> kvs.map(_._2) }

      val extractedHeaders =
        headersToExtract.map { k =>
          Chunk.fromIterable(
            headerNameToValuesMap.getOrElse(k, List.empty)
          )
        }

      (
        headerLines.mkString + "\r\n" + body,
        headersToExtract,
        body,
        extractedHeaders
      )
    }

  def headerSuite =
    suite("http header lexer")(
      testM("generated positive cases") {
        check(testGen) {
          case (msg, headersToExtract, expectedBody, expectedHeaders) =>
            val reader        = new StringReader(msg)
            val actualHeaders = parseHeaders(headersToExtract.toArray, reader).toSeq
            val actualBody    = mkString(reader)
            assert(actualHeaders)(hasSameElements(expectedHeaders)) &&
            assert(actualBody)(equalTo(expectedBody))
        }
      } @@ samples(1000),
      testM("failure scenarios") {
        checkM(failureScenarios) {
          case (request, expectedError) =>
            assertM(
              Task(
                parseHeaders(
                  Array("some-header"),
                  new StringReader(request),
                  TestHeaderSizeLimit
                )
              ).run
            )(fails(equalTo(expectedError)))
        }
      }
    )

  private def mkString(reader: Reader) = {
    var c       = -1
    val builder = new StringBuilder()
    while ({ c = reader.read(); c != -1 }) builder.append(c.toChar)
    builder.toString
  }
}
