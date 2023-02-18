package zio.http.forms

import zio._
import zio.test._

import java.nio.charset.StandardCharsets

object FormStateSpec extends ZIOSpecDefault {

  val CR = '\r'

  val formExample1 = s"""|--AaB03x${CR}
                         |Content-Disposition: form-data; name="submit-name"${CR}
                         |Content-Type: text/plain${CR}
                         |${CR}
                         |Larry${CR}
                         |--AaB03x${CR}
                         |Content-Disposition: form-data; name="files"; filename="file1.txt"${CR}
                         |Content-Type: text/plain${CR}
                         |${CR}
                         |... contents of file1.txt ...${CR}
                         |--AaB03x--${CR}""".stripMargin.getBytes(StandardCharsets.UTF_8)
  def spec         = suite("FormStateSpec")(
    test("FormStateAccum") {

      val lastByte = Some('\r')

      def wasNewline(byte: Byte): Boolean = lastByte.contains('\r') && byte == '\n'

      val id       = "AaB03x"
      val start    = Chunk.fromArray(s"--$id".getBytes())
      val end      = Chunk.fromArray(s"--$id--".getBytes())
      val boundary = Boundary(id)

      assertTrue(
        wasNewline('\n'),
        boundary.isEncapsulating(start),
        boundary.isClosing(end),
      )
    },
  )

}
