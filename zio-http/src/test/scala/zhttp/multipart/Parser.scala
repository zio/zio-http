package zhttp.experiment.multipart

import java.nio.charset.StandardCharsets

import zhttp.experiment.multipart.Util.getBytes
import zio.Chunk
import zio.test.Assertion._
import zio.test.{DefaultRunnableSpec, _}

object Util {
  def getBytes(input: Chunk[Message]): Chunk[Byte] = input
    .filter(_.isInstanceOf[ChunkedData])
    .asInstanceOf[Chunk[ChunkedData]]
    .map(_.chunkedData)
    .flatten
}
object ParserTest extends DefaultRunnableSpec {
  val boundary                    = "_5PHqf8_Pl1FCzBuT5o_mVZg36k67UYI"
  def makeHttpString(str: String) =
    augmentString(str).flatMap {
      case '\n' => "\r\n"
      case c    => c.toString
    }
  def spec                        = suite("Multipart Parser")(
    suite("Boundary")(
      test("Should parse boundary and header") {
        val data1          = """|--_5PHqf8_Pl1FCzBuT5o_mVZg36k67UYI
                       |Content-Disposition: form-data; name="upload"; filename="integration.txt"
                     """.stripMargin
        val processedData1 = makeHttpString(data1)
        val parser         = new Parser(boundary)
        val output1        = parser.getMessages(Chunk.fromArray(processedData1.getBytes()))
        val data2          = """|
                       |Content-Type: application/octet-stream
                       |Content-Transfer-Encoding: binary
                       |
                       |""".stripMargin
        val processedData2 = makeHttpString(data2)
        val output2        = parser.getMessages(Chunk.fromArray(processedData2.getBytes()))
        assert(output1)(equalTo(Chunk.empty)) && assert(output2)(
          equalTo(
            Chunk(
              MetaInfo(
                PartContentDisposition("upload", Some("integration.txt")),
                Some(PartContentType("application/octet-stream", None)),
                Some(PartContentTransferEncoding("binary")),
              ),
            ),
          ),
        )
      },
      test("Full Multipart body") {
        val data          = """|--_5PHqf8_Pl1FCzBuT5o_mVZg36k67UYI
                      |Content-Disposition: form-data; name="upload"; filename="integration.txt"
                      |Content-Type: application/octet-stream
                      |Content-Transfer-Encoding: binary
                      |
                      |this is a test
                      |here's another test
                      |catch me if you can!
                      |--_5PHqf8_Pl1FCzBuT5o_mVZg36k67UYI--""".stripMargin
        val processedData = makeHttpString(data)
        val parser        = new Parser(boundary)
        val outputBytes   = getBytes(
          parser
            .getMessages(Chunk.fromArray(processedData.getBytes())),
        )

        val outputString = new String(outputBytes.toArray, StandardCharsets.UTF_8)

        assert(outputString)(equalTo("this is a test\r\nhere's another test\r\ncatch me if you can!"))
      },
      test("Multiparts messages in parts") {
        val data           = """|--_5PHqf8_Pl1FCzBuT5o_mVZg36k67UYI
                      |Content-Disposition: form-data; name="upload"; filename="integration.txt"
                      |Content-Type: application/octet-stream
                      |Content-Transfer-Encoding: binary
                      |
                      |this is a test
                     """.stripMargin
        val processedData  = makeHttpString(data)
        val parser         = new Parser(boundary)
        val output         = new String(
          getBytes(parser.getMessages(Chunk.fromArray(processedData.getBytes()))).toArray,
          StandardCharsets.UTF_8,
        )
        val data2          = """here's another test
                      |catch me if you can!
                      |
                      |--_5PHqf8_Pl1FCzBuT5o_mVZg36k67UYI
                      |Content-Disposition: form-data; name="foo"
                      |
                      |bar
                      |--_5PHqf8_Pl1FCzBuT5o_mVZg36k67UYI--""".stripMargin
        val processedData2 = makeHttpString(data2)
        val output2        =
          new String(
            getBytes(parser.getMessages(Chunk.fromArray(processedData2.getBytes()))).toArray,
            StandardCharsets.UTF_8,
          )

        assert(output)(equalTo("this is a test")) && assert(output2)(
          equalTo("here's another test\r\ncatch me if you can!\r\n\r\nbar"),
        )
      },
    ),
  )
}
