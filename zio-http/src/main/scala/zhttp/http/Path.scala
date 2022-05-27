package zhttp.http

final case class Path(segments: Vector[String], leadingSlash: Boolean, trailingSlash: Boolean) { self =>
  def /(segment: String): Path = copy(segments :+ segment)

  def /:(name: String): Path = copy(name +: segments)

  def drop(n: Int): Path = copy(segments.drop(n))

  def dropLast(n: Int): Path = copy(segments.reverse.drop(n))

  def encode: String = {
    val ls = if (leadingSlash) "/" else ""
    val ss = segments.filter(_.nonEmpty).mkString("/")
    val ts = if (trailingSlash && segments.nonEmpty) "/" else ""
    ls + ss + ts
  }

  def initial: Path = copy(segments.init)

  def isEnd: Boolean = segments.isEmpty

  def last: Option[String] = segments.lastOption

  def reverse: Path = copy(segments.reverse)

  def startsWith(other: Path): Boolean = segments.startsWith(other.segments)

  def take(n: Int): Path = copy(segments.take(n))

  def toList: List[String] = segments.toList
}

object Path {
  val empty: Path = Path(Vector.empty, true, false)

  /**
   * Decodes a path string into a Path. Can fail if the path is invalid.
   */
  def decode(path: String): Path = {
    val segments      = path.split("/").toVector.filter(_.nonEmpty)
    val leadingSlash  = path.startsWith("/")
    val trailingSlash = segments.nonEmpty && path.endsWith("/")
    Path(segments, leadingSlash, trailingSlash)
  }
}
