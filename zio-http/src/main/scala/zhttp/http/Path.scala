package zhttp.http

final case class Path(segments: Vector[String]) { self =>

  def /(segment: String): Path = copy(segments :+ segment)

  def /:(name: String): Path = copy(name +: segments)

  def drop(n: Int): Path = copy(segments.drop(n))

  def dropLast(n: Int): Path = copy(segments.dropRight(n))

  def encode: String = segments.mkString("/")

  def initial: Path = copy(segments.init)

  def isEmpty: Boolean = segments.isEmpty

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
  val empty: Path = Path(Vector.empty)

  /**
   * Decodes a path string into a Path. Can fail if the path is invalid.
   */
  def decode(path: String): Path = Path(path.split("/", -1).toVector)
}
