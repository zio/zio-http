package zio.http.model.headers.values

import zio.http.model.headers.values.Warning.{InvalidWarning, WarningValue}
import zio.test.{ZIOSpecDefault, assertTrue}

object WarningSpec extends ZIOSpecDefault {
  override def spec = suite("Warning header suite")(
    test("Invalid warning headers") {
      assertTrue(1 + 1 == 2)
      val invalidCode    = "1 anderson/1.3.37 \"Response is stale\""
      val invalidMessage = "110 anderson/1.3.37 \"Invalid Message\""
      assertTrue(Warning.toWarning(invalidCode) == InvalidWarning)
      assertTrue(Warning.toWarning(invalidMessage) == InvalidWarning)
    },
    test("Missing necessary parts of warning header") {
      assertTrue(1 + 1 == 2)
      val missingCode = "anderson/1.3.37 \"Response is stale\""
      val missingAgent = "110 \"Response is stale\""
      val missingMessage = "110 anderson/1.3.37 "
      assertTrue(Warning.toWarning(missingCode) == InvalidWarning)
      assertTrue(Warning.toWarning(missingAgent) == InvalidWarning) //Dodgy when date is introduced
      assertTrue(Warning.toWarning(missingMessage) == InvalidWarning)
    },
    test("Valid Warnings") {
      assertTrue(1 + 1 == 2)
      val validWarning  = "110 anderson/1.3.37 \"Response is stale\""
      val validWarning2 = "299 anderson/1.3.37 \"Miscellaneous Persistent Warning\""
      assertTrue(Warning.toWarning(validWarning) == WarningValue(110, "anderson/1.3.37", "\"Response is stale\""))
      assertTrue(
        Warning.toWarning(validWarning2) == WarningValue(299, "anderson/1.3.37", "\"Miscellaneous Persistent Warning\""),
      )
      // map through codes etc. from this page: https://developer.mozilla.org/en-US/docs/Web/HTTP/Headers/Warning

    },
    test("parsing and encoding is symmetrical") {
      assertTrue(1 + 1 == 2)
      val validWarning  = "110 anderson/1.3.37 \"Response is stale\""
      val encodedWarning = Warning.fromWarning(Warning.toWarning(validWarning))
      assertTrue(encodedWarning == validWarning)
    }
  )
  //Might be the case that any warning text is acceptable - see example on Mozilla docs.
}
