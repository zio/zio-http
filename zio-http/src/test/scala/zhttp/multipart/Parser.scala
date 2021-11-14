package zhttp.experiment.multipart

import zhttp.http.Header
import zio.test.Assertion.equalTo
import zio.test.{DefaultRunnableSpec, assertM}
import zio.{Chunk, UIO, ZIO}

object ParserTest extends DefaultRunnableSpec {
  def makeHttpString(str: String): String =
    augmentString(str).flatMap {
      case '\n' => "\r\n"
      case c    => c.toString
    }
  val headers = List(Header("Content-Type", "multipart/form-data; boundary=_5PHqf8_Pl1FCzBuT5o_mVZg36k67UYI"))
  def spec    = suite("Multipart Parser")(
    suite("Request Parsing")(
      testM("Should extract boundary from request") {
        assertM(ZIO.fromEither(Parser.getBoundary(headers)))(equalTo("_5PHqf8_Pl1FCzBuT5o_mVZg36k67UYI"))
      },
      testM("Should store partial header data to parserState") {
        val data          =
          """|--_5PHqf8_Pl1FCzBuT5o_mVZg36k67UYI
             |Content-Disposition: form-data; name="upload"; filename="integration.txt"""".stripMargin
        val processedData = makeHttpString(data)
        val output        = Parser.getMessages(
          Chunk.fromArray(processedData.getBytes()),
          ParserState(Chunk.fromArray("_5PHqf8_Pl1FCzBuT5o_mVZg36k67UYI".getBytes)),
        ) match {
          case Left(_)      => ZIO.fail("Parsing Failed")
          case Right(value) => UIO(value)
        }
        assertM(output)(
          equalTo(
            (
              Chunk.empty,
              ParserState(
                Chunk.fromArray("_5PHqf8_Pl1FCzBuT5o_mVZg36k67UYI".getBytes),
                state = PartHeader,
                0,
                0,
                Chunk.fromArray(
                  "\r\nContent-Disposition: form-data; name=\"upload\"; filename=\"integration.txt\"".getBytes,
                ),
              ),
            ),
          ),
        )
      },
      testM("Should output header info and clears up temp data") {
        val data          = """|
                      |Content-Type: application/octet-stream
                      |Content-Transfer-Encoding: binary
                      |
                      |""".stripMargin
        val processedData = makeHttpString(data)
        val output        = Parser.getMessages(
          Chunk.fromArray(processedData.getBytes()),
          ParserState(
            Chunk.fromArray("_5PHqf8_Pl1FCzBuT5o_mVZg36k67UYI".getBytes),
            state = PartHeader,
            0,
            0,
            Chunk.fromArray(
              "\r\nContent-Disposition: form-data; name=\"upload\"; filename=\"integration.txt\"".getBytes,
            ),
          ),
        ) match {
          case Left(_)      => ZIO.fail("Parsing Failed")
          case Right(value) => UIO(value)
        }
        assertM(output)(
          equalTo(
            (
              Chunk(
                MetaInfo(
                  PartContentDisposition("upload", Some("integration.txt")),
                  Some(PartContentType("application/octet-stream", None)),
                  Some(PartContentTransferEncoding("binary")),
                ),
              ),
              ParserState(
                Chunk.fromArray("_5PHqf8_Pl1FCzBuT5o_mVZg36k67UYI".getBytes),
                state = PartData,
              ),
            ),
          ),
        )
      },
      testM("Should read data from the file upload and output in ChunkedData") {
        val data                                                    =
          """
            |this is a test""".stripMargin
        val processedData: String                                   = makeHttpString(data)
        val output: ZIO[Any, String, (Chunk[Message], ParserState)] = Parser.getMessages(
          Chunk.fromArray(processedData.getBytes),
          ParserState(Chunk.fromArray("_5PHqf8_Pl1FCzBuT5o_mVZg36k67UYI".getBytes), state = PartData),
        ) match {
          case Left(_)      => ZIO.fail("Parsing Failed")
          case Right(value) => UIO(value)
        }
        assertM(output)(
          equalTo(
            (
              Chunk(ChunkedData(Chunk.fromArray("\r\nthis is a test".getBytes))),
              ParserState(Chunk.fromArray("_5PHqf8_Pl1FCzBuT5o_mVZg36k67UYI".getBytes), state = PartData),
            ),
          ),
        )
      },
      testM("Should read data from the file upload and output in ChunkedData and should identify end of the body") {
        val data                                                    = """here's another test
                     |catch me if you can!
                     |
                     |--_5PHqf8_Pl1FCzBuT5o_mVZg36k67UYI--""".stripMargin
        val processedData                                           = makeHttpString(data)
        val output: ZIO[Any, String, (Chunk[Message], ParserState)] = Parser.getMessages(
          Chunk.fromArray(processedData.getBytes),
          ParserState(Chunk.fromArray("_5PHqf8_Pl1FCzBuT5o_mVZg36k67UYI".getBytes), state = PartData),
        ) match {
          case Left(_)      => ZIO.fail("Parsing Failed")
          case Right(value) => UIO(value)
        }
        assertM(output)(
          equalTo(
            (
              Chunk(ChunkedData(Chunk.fromArray("here's another test\r\ncatch me if you can!\r\n".getBytes)), BodyEnd),
              ParserState(Chunk.fromArray("_5PHqf8_Pl1FCzBuT5o_mVZg36k67UYI".getBytes), state = End),
            ),
          ),
        )
      },
    ),
  )
}
