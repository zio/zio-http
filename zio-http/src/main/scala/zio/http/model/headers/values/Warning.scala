package zio.http.model.headers.values

import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import scala.util.Try

sealed trait Warning {}

/*
The Warning HTTP header contains information about possible problems with the status of the message.
  More than one Warning header may appear in a response.

Warning header fields can, in general, be applied to any message.
  However, some warn-codes are specific to caches and can only be applied to response messages.
 */

object Warning {

  /*
     A warning has the following syntax: <warn-code> <warn-agent> <warn-text> [<warn-date>]
   */
  final case class WarningValue(code: Int, agent: String, text: String, date: Option[ZonedDateTime] = None)
      extends Warning

  private val validCodes = List(110, 111, 112, 113, 199, 214, 299)
  private val expectedDateFormat = DateTimeFormatter.ofPattern("EEE, dd MMM yyyy HH:mm:ss zzz")

  case object InvalidWarning extends Warning

  def toWarning(warningString: String): Warning = {
    /*
      <warn-code>
       A three-digit warning number.
         The first digit indicates whether the Warning is required to be deleted from a stored response after validation.

       1xx warn-codes describe the freshness or validation status of the response and will be deleted by a cache after deletion.

        2xx warn-codes describe some aspect of the representation that is not rectified by a validation and
           will not be deleted by a cache after validation unless a full response is sent.
     */
    val warnCode: Int = Try { Integer.parseInt(warningString.split(" ")(0)) }.getOrElse(-1)

    /*
       <warn-agent>
         The name or pseudonym of the server or software adding the Warning header (might be "-" when the agent is unknown).
     */
    val warnAgent: String = warningString.split(" ")(1)

    /*
       <warn-text>
       An advisory text describing the error.
     */
    val descriptionStartIndex = warningString.indexOf('\"')
    val descriptionEndIndex   = warningString.indexOf("\"", warningString.indexOf("\"") + 1)
    val description           =
      Try { warningString.substring(descriptionStartIndex, descriptionEndIndex + 1) }.getOrElse("")

    /*
    <warn-date>
    A date. This is optional. If more than one Warning header is sent, include a date that matches the Date header.
     */

    val dateStartIndex = warningString.indexOf("\"", descriptionEndIndex + 1)
    val dateEndIndex   = warningString.indexOf("\"", dateStartIndex + 1)
    val warningDate    = Try {
      val selectedDate   = warningString.substring(dateStartIndex + 1, dateEndIndex)
      ZonedDateTime.parse(selectedDate, expectedDateFormat)
    }.toOption

    val fullWarning = WarningValue(warnCode, warnAgent, description, warningDate)

    /*
    The HTTP Warn Codes registry at iana.org defines the namespace for warning codes.
      Registry is available here: https://www.iana.org/assignments/http-warn-codes/http-warn-codes.xhtml
     */
    def isCodeValid(warningCode: Int): Boolean = {
      if (validCodes.contains(warningCode)) true
      else false
    }

    def isAgentMissing(text: String): Boolean = {
      val textBeforeDescription = text.toList.take(descriptionStartIndex)
      if (textBeforeDescription.length <= 4) {
        true
      } else false
    }

    /*
    Date should confirm to the pattern "EEE, dd MMM yyyy HH:mm:ss zzz"
    For example: Wed, 21 Oct 2015 07:28:00 GMT
     */
    def isDateInvalid(warningText: String, warningDate: Option[ZonedDateTime]): Boolean = {
      val trimmedWarningText         = warningText.trim
      val descriptionEndIndexNoSpace = trimmedWarningText.indexOf("\"", trimmedWarningText.indexOf("\"") + 1)
      if (warningDate.isEmpty && trimmedWarningText.length - descriptionEndIndexNoSpace > 1) true
      else false
    }

    if (isDateInvalid(warningString, warningDate)) {
      InvalidWarning
    } else if (isAgentMissing(warningString)) {
      InvalidWarning
    } else if (isCodeValid(fullWarning.code) && fullWarning.text.nonEmpty) {
      fullWarning
    } else {
      InvalidWarning
    }

  }

  def fromWarning(warning: Warning): String = {
    val warningString = warning match {
      case WarningValue(code, agent, text, date) => {
        val formattedDate  = date match {
          case Some(value) => value.format(expectedDateFormat)
          case None        => ""
        }
        if (formattedDate.isEmpty) {
          s"$code $agent $text"
        } else {
          s"$code $agent $text \"${formattedDate}\""
        }
      }
      case InvalidWarning                        => ""
    }
    warningString
  }

}
