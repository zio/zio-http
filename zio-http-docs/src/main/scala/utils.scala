import scala.io.Source

object utils {

  def readSource(path: String): String = {
    val source  = Source.fromFile("../" + path)
    val content = source.getLines().mkString("\n")
    content
  }

  def fileExtension(path: String): String = {
    val javaPath      = java.nio.file.Paths.get(path)
    val fileExtension =
      javaPath.getFileName.toString
        .split('.')
        .lastOption
        .getOrElse("")
    fileExtension
  }

  def printSource(path: String, comment: Boolean = true, showLineNumbers: Boolean = true) {
    val title     = if (comment) s"""title="$path"""" else ""
    val showLines = if (showLineNumbers) "showLineNumbers" else ""
    println(s"""```${fileExtension(path)} ${title} ${showLines}"""")
    println(readSource(path))
    println("```")
  }

}
