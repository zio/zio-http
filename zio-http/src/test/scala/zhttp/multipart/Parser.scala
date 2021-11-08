package zhttp.experiment.multipart

import zio.Chunk
import zio.stream.ZStream
import zio.test.Assertion._
import zio.test.{DefaultRunnableSpec, _}
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
        val data1          = """
                      |--_5PHqf8_Pl1FCzBuT5o_mVZg36k67UYI
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
        assert(output1)(equalTo(Chunk(Boundary))) && assert(output2)(
          equalTo(Chunk.empty),
        )
      },
      test("multiple parts") {
        val data          = """
                     |--_5PHqf8_Pl1FCzBuT5o_mVZg36k67UYI
                     |Content-Disposition: form-data; name="upload"; filename="integration.txt"
                     |Content-Type: application/octet-stream
                     |Content-Transfer-Encoding: binary
                     |
                     |this is a test
                     |here's another test
                     |catch me if you can!
                     |
                     |--_5PHqf8_Pl1FCzBuT5o_mVZg36k67UYI
                     |Content-Disposition: form-data; name="foo"
                     |
                     |bar
                     |--_5PHqf8_Pl1FCzBuT5o_mVZg36k67UYI--""".stripMargin
        val processedData = makeHttpString(data)
        val parser        = new Parser(boundary)
        val output        = parser.getMessages(Chunk.fromArray(processedData.getBytes()))
        assert(output)(equalTo(Chunk(Boundary)))
      },
      test("multiple parts") {
        val data           = """
                     |--_5PHqf8_Pl1FCzBuT5o_mVZg36k67UYI
                     |Content-Disposition: form-data; name="upload"; filename="integration.txt"
                     |Content-Type: application/octet-stream
                     |Content-Transfer-Encoding: binary
                     |
                     |this is a test
                     """.stripMargin
        val processedData  = makeHttpString(data)
        val parser         = new Parser(boundary)
        val output         = parser.getMessages(Chunk.fromArray(processedData.getBytes()))
        val data2          = """here's another test
                      |catch me if you can!
                      |
                      |--_5PHqf8_Pl1FCzBuT5o_mVZg36k67UYI
                      |Content-Disposition: form-data; name="foo"
                      |
                      |bar
                      |--_5PHqf8_Pl1FCzBuT5o_mVZg36k67UYI--""".stripMargin
        val processedData2 = makeHttpString(data2)
        val output2        = parser.getMessages(Chunk.fromArray(processedData2.getBytes()))

        assert(output)(equalTo(Chunk(Boundary))) && assert(output2)(equalTo(Chunk(Boundary)))
      },
      testM("multiple parts stream") {
        val data                                              = """
                     |--_5PHqf8_Pl1FCzBuT5o_mVZg36k67UYI
                     |Content-Disposition: form-data; name="upload"; filename="integration.txt"
                     |Content-Type: application/octet-stream
                     |Content-Transfer-Encoding: binary
                     |
                     |this is a test
                     |here's another test
                     |catch me if you can!
                     |
                     |--_5PHqf8_Pl1FCzBuT5o_mVZg36k67UYI
                     |Content-Disposition: form-data; name="foo"
                     |
                     |bar
                     |--_5PHqf8_Pl1FCzBuT5o_mVZg36k67UYI--""".stripMargin
        val processedData: ZStream[Any, Nothing, Chunk[Byte]] =
          ZStream.fromIterable(makeHttpString(data).getBytes()).grouped(4)
        val parser                                            = new Parser(boundary)
        val output                                            = parser.byteStreamToMessageStream(processedData)
        val a                                                 = output.runCollect
        assertM(a)(equalTo(Chunk.empty))
      },
    ),
  )
}
