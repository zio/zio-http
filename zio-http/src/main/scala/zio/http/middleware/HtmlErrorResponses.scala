package zio.http.middleware

import zio.http.html._
import zio.http.model.{HeaderNames, HeaderValues, Headers}
import zio.http.{Body, Request, Response, model}

import java.io.{PrintWriter, StringWriter}
import zio.stacktracer.TracingImplicits.disableAutoTrace // scalafix:ok;

private[zio] trait HtmlErrorResponses {

  protected def replaceErrorResponse(request: Request, response: Response): Response = {
    def htmlResponse: Body = {
      val message: String = response.httpError.map(_.message).getOrElse("")
      val data            = Template.container(s"${response.status}") {
        div(
          div(
            styles := Seq("text-align" -> "center"),
            div(s"${response.status.code}", styles := Seq("font-size" -> "20em")),
            div(message),
          ),
          div(
            response.httpError.get.foldCause(div()) { throwable =>
              div(h3("Cause:"), pre(prettify(throwable)))
            },
          ),
        )
      }
      Body.fromString("<!DOCTYPE html>" + data.encode)
    }

    def textResponse: Body = {
      Body.fromString(formatErrorMessage(response))
    }

    if (response.status.isError) {
      request.accept match {
        case Some(value) if value.toString.contains(HeaderValues.textHtml) =>
          response.copy(
            body = htmlResponse,
            headers = Headers(HeaderNames.contentType, model.HeaderValues.textHtml),
          )
        case Some(value) if value.toString.equals("*/*")                        =>
          response.copy(
            body = textResponse,
            headers = Headers(HeaderNames.contentType, model.HeaderValues.textPlain),
          )
        case _                                                                  => response
      }

    } else
      response
  }

  private def prettify(throwable: Throwable): String = {
    val sw = new StringWriter
    throwable.printStackTrace(new PrintWriter(sw))
    s"${sw.toString}"
  }

  private def formatCause(response: Response): String =
    response.httpError.get.foldCause("") { throwable =>
      s"${scala.Console.BOLD}Cause: ${scala.Console.RESET}\n ${prettify(throwable)}"
    }

  private def formatErrorMessage(response: Response) = {
    val errorMessage: String = response.httpError.map(_.message).getOrElse("")
    val status               = response.status.code
    s"${scala.Console.BOLD}${scala.Console.RED}${response.status} ${scala.Console.RESET} - " +
      s"${scala.Console.BOLD}${scala.Console.CYAN}$status ${scala.Console.RESET} - " +
      s"$errorMessage\n${formatCause(response)}"
  }
}
