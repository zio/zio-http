package zio.http.multipart.mixed

import zio.Scope
import zio.http.{Boundary, ZIOHttpSpec}
import zio.stream.ZStream
import zio.test.{Assertion, Spec, TestEnvironment}

object MixedSpec  extends ZIOHttpSpec {

  override def spec: Spec[TestEnvironment with Scope, Any] = mixedSuite

  val mixedSuite = suiteAll("multipart/mixed") {

    val defaultSep = "simple boundary"

    suiteAll("empty") {
      val empty = Mixed.fromParts(ZStream.empty, Boundary(defaultSep))


      test("has no parts") {
        empty
          .parts
          .runCollect
          .map{collected =>
            zio.test.assert(collected)(Assertion.isEmpty)
          }
      }
    }


  }

}
