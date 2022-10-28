package zio.http.model.headers.values

import java.time.{ZoneId, ZonedDateTime}
import java.time.format.DateTimeFormatter
import scala.util.Try

sealed trait Warning {}

object Warning {

  final case class WarningValue(code: Int, agent: String, text: String, date: Option[ZonedDateTime]) extends Warning

  case object InvalidWarning extends Warning

  def toWarning(warningText: String): Warning = {
    // Make the warning
    val warningParts  = warningText.split(" ")
    val code: Int     = Try { Integer.parseInt(warningParts(0)) }.getOrElse(-1)
    val agent: String = warningParts(1)

    val messageStartIndex = warningText.indexOf('\"')
    val messageEndIndex   = warningText.indexOf("\"", warningText.indexOf("\"") + 1)
    val message           =
      Try { warningText.substring(messageStartIndex, messageEndIndex + 1) }.getOrElse("Error: Message is missing")

    // check if two substrings exist.
    // if yes, validate first as message and second as date.
    // If only one substring exists, validate that it is NOT a date. If it is a date, then message is missing
    // If only one substring exists and is not a date, then do normal validation.
    val dateStartIndex = warningText.indexOf("\"", messageEndIndex)

    def makeDate(dateString: String): ZonedDateTime = {
      val formatter   = DateTimeFormatter.ofPattern("EEE, dd MMM yyyy HH:mm:ss zzz")
      val messageDate = ZonedDateTime.parse(dateString, formatter)
      messageDate
    }

    val defaultDate = ZonedDateTime.of(0, 0, 0, 0, 0, 0, 0, ZoneId.systemDefault())
    val chosenDate = Try {
      val dateStartIndex = warningText.indexOf("\"", messageEndIndex)
      val dateEndIndex   = warningText.indexOf("\"", dateStartIndex)
      val dateSubstring  = warningText.substring(dateStartIndex, dateEndIndex)
      makeDate(dateSubstring)
    }.getOrElse(defaultDate)

    val warningDate = chosenDate match{
      case defaultDate => None
      case _ => Some(chosenDate)
    }

    val result = WarningValue(code, agent, message, warningDate)

    // Validate the warning
    val validCodesAndMessages = Map(
      110 -> "\"response is stale\"",
      111 -> "\"revalidation failed\"",
      112 -> "\"disconnected operation\"",
      113 -> "\"heuristic expiration\"",
      199 -> "\"miscellaneous warning\"",
      214 -> "\"transformation applied\"",
      299 -> "\"miscellaneous persistent warning\"",
    )

    if (validCodesAndMessages.getOrElse(result.code, "") == result.text.toLowerCase) {
      result
    } else {
      InvalidWarning
    }

  }

  def fromWarning(warning: Warning): String = {
    println(s"Inputting $warning")
    val result = warning match {
      case WarningValue(code, agent, text, date) => s"$code $agent $text"
      case InvalidWarning                        => ""
    }
    result
  }
}
