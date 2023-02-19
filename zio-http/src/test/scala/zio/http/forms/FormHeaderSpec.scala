package zio.http.forms

import zio.Scope
import zio.test._

import zio.http.forms.FormAST.Header
object FormHeaderSpec extends ZIOSpecDefault {

  val contentType1 = "Content-Type: text/html; charset=utf-8".getBytes()
  val contextType2 = "Content-Type: multipart/form-data; boundary=something".getBytes

  def spec = suite("HeaderSpec")(
    test("Header parsing") {

      val header = Header.fromBytes(contentType1)

      println(header.get.fields)

      assertTrue(
        header.get.name == "Content-Type",
        header.get.fields.get("charset").get == "utf-8",
        header.get.preposition == "text/html",
      )

    },
  )

}
