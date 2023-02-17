package zio.http.model.headers.values

import java.time.{ZoneId, ZonedDateTime}

import zio.test.{ZIOSpecDefault, assertTrue}

import zio.http.model.headers.values.Warning.{InvalidWarning, WarningValue}

object WarningSpec extends ZIOSpecDefault {

  val validWarning            = "110 anderson/1.3.37 \"Response is stale\""
  val validWarningWithDate    = "112 - \"cache down\" \"Wed, 21 Oct 2015 07:28:00 GMT\""
  val stubDate: ZonedDateTime = ZonedDateTime.of(2015, 10, 21, 7, 28, 0, 0, ZoneId.of("GMT"))

  override def spec = suite("Warning header suite")(
    test("Rejects Invalid Warning Code") {
      val invalidCode = "1 anderson/1.3.37 \"Response is stale\""
      assertTrue(Warning.toWarning(invalidCode) == InvalidWarning)
    },
    test("Rejects Invalid Warning Date") {
      val invalidDate = validWarning + " " + "invalidDate"
      assertTrue(Warning.toWarning(invalidDate) == InvalidWarning)
    },
    test("Rejects Missing Warning Code") {
      val missingCode = "anderson/1.3.37 \"Response is stale\""
      assertTrue(Warning.toWarning(missingCode) == InvalidWarning)
    },
    test("Rejects Missing Warning Agent") {
      val missingAgent = "110 \"Response is stale\""
      assertTrue(Warning.toWarning(missingAgent) == InvalidWarning)
    },
    test("Rejects Missing Warning Agent with date") {
      val missingAgentWithDate = "112 \"cache down\" \"Wed, 21 Oct 2015 07:28:00 GMT\""
      assertTrue(Warning.toWarning(missingAgentWithDate) == InvalidWarning)
    },
    test("Rejects Missing Warning Description") {
      val missingDescription = "110 anderson/1.3.37 "
      assertTrue(Warning.toWarning(missingDescription) == InvalidWarning)
    },
    test("Accepts Valid Warning with Date") {
      assertTrue(Warning.toWarning(validWarningWithDate) == WarningValue(112, "-", "\"cache down\"", Some(stubDate)))
    },
    test("Accepts Valid Warning without Date") {
      assertTrue(Warning.toWarning(validWarning) == WarningValue(110, "anderson/1.3.37", "\"Response is stale\""))
    },
    test("parsing and encoding is symmetrical for warning with Date") {
      val encodedWarningwithDate = Warning.fromWarning(Warning.toWarning(validWarningWithDate))
      assertTrue(encodedWarningwithDate == validWarningWithDate)
    },
    test("parsing and encoding is symmetrical for warning without Date") {
      val encodedWarning = Warning.fromWarning(Warning.toWarning(validWarning))
      assertTrue(encodedWarning == validWarning)
    },
  )

}
