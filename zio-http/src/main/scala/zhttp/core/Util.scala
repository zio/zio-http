package zhttp.core

import zhttp.html._

import java.io.{PrintWriter, StringWriter}

object Util {
  def prettyPrint(throwable: Throwable): String = {
    val sw = new StringWriter
    throwable.printStackTrace(new PrintWriter(sw))
    s"${sw.toString}"
  }

  def prettyPrintHtml(throwable: Throwable): String = {
    html(
      head(),
      body(
        h1("Internal Server Error"),
        pre(div(prettyPrint(throwable).split("\n").mkString("\n"))),
      ),
    ).encode
  }
}
