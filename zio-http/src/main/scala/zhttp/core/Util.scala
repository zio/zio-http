package zhttp.core

import zhttp.html._

import java.io.{PrintWriter, StringWriter}

object Util {
  def prettyPrint(throwable: Throwable): String = {
    val sw = new StringWriter
    throwable.printStackTrace(new PrintWriter(sw))
    s"${sw.toString}"
  }

  def zioHttpContainer(title: String)(element: Html): Html = {
    html(
      head(),
      body(
        h1(title),
        element,
      ),
    )
  }

  def prettyPrintHtml(throwable: Throwable): String =
    zioHttpContainer("Internal Server Error") {
      pre(div(prettyPrint(throwable).split("\n").mkString("\n")))
    }.encode

  def listFilesHtml(dir: String, fileList: Seq[String]): String = {
    zioHttpContainer(s"Listing of ${dir}") {
      div(
        ul(
          fileList.map { file =>
            li(
              a(
                href := s"${file}",
                file,
              ),
            )
          },
        ),
      )

    }.encode
  }
}
