package zhttp.core

import java.io.{PrintWriter, StringWriter}

object Util {
  def prettyPrint(throwable: Throwable): String = {
    val sw = new StringWriter
    throwable.printStackTrace(new PrintWriter(sw))
    s"${throwable.toString}:\n${sw.toString}"
  }

  def prettyPrintHtml(throwable: Throwable): String = {
    s"""
       |<html>
       |  <head>
       |  </head>
       |  <body>
       |   <h1>Internal Server Error</h1>
       |   <pre>${prettyPrint(throwable).split("\n").map(str => s"<div>${str}</div>").mkString("")}</pre>
       |  </body>
       |</html>
       |""".stripMargin
  }
}
