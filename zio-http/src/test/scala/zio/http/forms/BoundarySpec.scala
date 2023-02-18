package zio.http.forms

import java.nio.charset.StandardCharsets

import zio._
import zio.test._

import zio.http.forms.Fixtures._

object BoundarySpec extends ZIOSpecDefault {

  val multipartFormBytes3 = Chunk.fromArray(s"""------heythere--${CRLF}""".getBytes(StandardCharsets.UTF_8))
  val bad1                = Chunk.fromArray(s"""-heythere${CRLF}""".getBytes(StandardCharsets.UTF_8))
  val bad2                = Chunk.fromArray(s"""--heythere${CR}""".getBytes(StandardCharsets.UTF_8))
  val bad3                = Chunk.fromArray(s"--heythere\n".getBytes(StandardCharsets.UTF_8))
  val bad4                = Chunk.fromArray(s"--heythere".getBytes(StandardCharsets.UTF_8))

  val fromContentSuite = suite("fromContent")(
    test("parse success") {
      val boundary1 = Boundary.fromContent(multipartFormBytes1)
      val boundary2 = Boundary.fromContent(multipartFormBytes2)
      val boundary3 = Boundary.fromContent(multipartFormBytes3)
      assertTrue(
        boundary1.get.id == "AaB03x",
        boundary2.get.id == "(((AaB03x)))",
        boundary3.get.id == "----heythere--",
        boundary3.get.encapsulationBoundary == "------heythere--",
        boundary3.get.closingBoundary == "------heythere----",
      )
    },
    test("parse failure") {

      val boundary1 = Boundary.fromContent(bad1)
      val boundary2 = Boundary.fromContent(bad2)
      val boundary3 = Boundary.fromContent(bad3)
      val boundary4 = Boundary.fromContent(bad4)

      assertTrue(
        boundary1.isEmpty,
        boundary2.isEmpty,
        boundary3.isEmpty,
        boundary4.isEmpty,
      )
    },
  )

  val spec = suite("BoundarySpec")(
    fromContentSuite,
  )
}
