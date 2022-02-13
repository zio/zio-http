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

  def listFilesHtml(dirPath: java.nio.file.Path): String = {
    zioHttpContainer(s"Listing of ${dirPath}") {
      div(
        ul(
          dirPath.toFile.listFiles.toList.map { file =>
            li(
              a(
                href := s"/${file.getName}",
                file.getName,
              ),
            )
          },
        ),
      )

    }.encode
  }
}
