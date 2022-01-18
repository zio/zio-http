package zhttp.core

import java.io.{PrintWriter, StringWriter}

object Util {
  def prettyPrint(throwable: Throwable): String = {
    val sw = new StringWriter
    throwable.printStackTrace(new PrintWriter(sw))
    s"${sw.toString}"
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

  def listFilesHtml(dirPath: java.nio.file.Path): String = {
    val buf = new StringBuilder()
      .append("<!DOCTYPE html>\r\n")
      .append("<html><head><meta charset='utf-8' /><title>")
      .append("Listing of: ")
      .append(dirPath)
      .append("</title></head><body>\r\n")
      .append("<h3>Listing of: ")
      .append(dirPath)
      .append("</h3>\r\n")
      .append("<ul>")
      .append("<li><a href=\"../\">..</a></li>\r\n")

    val files = dirPath.toFile.listFiles()
    files.foreach(f => {
      if (f.canRead) {
        val name = f.getName
        buf
          .append("<li><a href=\"")
          .append(name)
          .append("\">")
          .append(name)
          .append("</a></li>\r\n")
      }
    })
    buf.append("</ul></body></html>\r\n")
    buf.toString()
  }
}
