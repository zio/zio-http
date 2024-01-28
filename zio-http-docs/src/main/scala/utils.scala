import scala.io.Source

object utils {

  def readSource(path: String, lines: Seq[(Int, Int)]): String = {
    if (lines.isEmpty) {
      val source  = Source.fromFile("../" + path)
      val content = source.getLines().mkString("\n")
      content
    } else {
      val chunks = for {
        (from, to) <- lines
        source  = Source.fromFile("../" + path)
        content = source.getLines().toArray[String]
      } yield content.slice(from - 1, to).mkString("\n")

      chunks.mkString("\n\n")
    }
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

  def printSource(
    path: String,
    lines: Seq[(Int, Int)] = Seq.empty,
    comment: Boolean = true,
    showLineNumbers: Boolean = true,
  ) {
    val title     = if (comment) s"""title="$path"""" else ""
    val showLines = if (showLineNumbers) "showLineNumbers" else ""
    println(s"""```${fileExtension(path)} ${title} ${showLines}"""")
    println(readSource(path, lines))
    println("```")
  }

}
