package zhttp.http

final case class Path(segments: Vector[String], trailingSlash: Boolean) { self =>
  def /(segment: String): Path = copy(segments :+ segment)

  def /:(name: String): Path = copy(name +: segments)

  def drop(n: Int): Path = copy(segments.drop(n))

  def dropLast(n: Int): Path = copy(segments.reverse.drop(n))

  def encode: String = {
    val ss = segments.filter(_.nonEmpty).mkString("/")
    ss match {
      case "" if trailingSlash  => "/"
      case "" if !trailingSlash => ""
      case ss                   => "/" + ss + (if (trailingSlash) "/" else "")
    }
  }

  def initial: Path = copy(segments.init)

  def isEmpty: Boolean = segments.isEmpty && !trailingSlash

  def isEnd: Boolean = segments.isEmpty

  def last: Option[String] = segments.lastOption

  def nonEmpty: Boolean = !isEmpty

  def reverse: Path = copy(segments.reverse)

  def startsWith(other: Path): Boolean = segments.startsWith(other.segments)

  def take(n: Int): Path = copy(segments.take(n))

  def toList: List[String] = segments.toList

  override def toString: String = encode
}

object Path {
  val empty: Path = Path(Vector.empty, false)

  /**
   * Decodes a path string into a Path. Can fail if the path is invalid.
   */
  def decode(path: String): Path = {
    val segments = path.split("/").toVector.filter(_.nonEmpty)
    segments.isEmpty match {
      case true if path.endsWith("/") => Path(Vector.empty, true)
      case true                       => Path(Vector.empty, false)
      case _                          => Path(segments, path.endsWith("/"))
    }
  }
}
