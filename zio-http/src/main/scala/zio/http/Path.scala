package zio.http

import zio.http.Path.Segment
import zio.http.Path.Segment.Text

import scala.collection.mutable
import zio.stacktracer.TracingImplicits.disableAutoTrace // scalafix:ok;

/**
 * Path is an immutable representation of a urls path. Internally it stores each
 * element of a path in a sequence of `Segment`. This allows for powerful
 * compositional APIs.
 */
final case class Path private (segments: Vector[Segment]) { self =>

  /**
   * Appends a segment at the end of the path. To append a trailing slash use an
   * empty string.
   */
  def /(name: String): Path = Path(segments :+ Segment(name))

  /**
   * Prepends the path with the provided segment. To prepend a leading slash use
   * an empty string.
   */
  def /:(name: String): Path = Path(Segment(name) +: segments)

  /**
   * Combines two paths together to create a new one. In the process it will
   * remove all extra slashes from the final path, leaving only the ones that
   * are at the ends.
   */
  def ++(other: Path): Path = Path(self.segments ++ other.segments)

  /**
   * Appends a trailing slash to the path
   */
  def addTrailingSlash: Path =
    lastSegment match {
      case Some(Segment.Root) => self
      case _                  => self / ""
    }

  /**
   * Named alias to `++` operator
   */
  def concat(other: Path): Path = self ++ other

  /**
   * Drops segments from the beginning of the path.
   */
  def drop(n: Int): Path = Path(segments = segments.drop(n))

  /**
   * Drops segments from the end of the path.
   */
  def dropLast(n: Int): Path = Path(segments = segments.dropRight(n))

  /**
   * Drops the trailing slash if available
   */
  def dropTrailingSlash: Path =
    lastSegment match {
      case Some(Segment.Root) => self.dropLast(1)
      case _                  => self
    }

  /**
   * Encodes the current path into a valid string
   */
  def encode: String = {
    val b        = new mutable.StringBuilder()
    var i        = 0
    val len      = segments.length
    var addSlash = segments.headOption match {
      case Some(Segment.Root) => true
      case _                  => false
    }
    while (i < len) {
      val segment = segments(i)
      if (addSlash) {
        b.append('/')
        addSlash = false
      }

      segment match {
        case Segment.Root       => ()
        case Segment.Text(text) =>
          b.append(text)
          addSlash = true
      }

      i += 1
    }

    b.toString()
  }

  /**
   * Returns a new path that contains only the inital segments, leaving the last
   * segment.
   */
  def initial: Path = Path(segments = segments.init)

  /**
   * Checks if the path is equal to ""
   */
  def isEmpty: Boolean = segments.isEmpty

  /**
   * Checks if the path is equal to "/"
   * @return
   */
  def isRoot: Boolean = segments match {
    case Vector(Segment.Root) => true
    case _                    => false
  }

  /**
   * Returns a the last element of the path. If the path contains a trailing
   * slash, `None` will be returned.
   */
  def last: Option[String] = segments.lastOption match {
    case Some(Text(text)) => Some(text)
    case _                => None
  }

  /**
   * Returns the last segment of the path
   */
  def lastSegment: Option[Segment] = segments.lastOption

  /**
   * Checks if the path contains a leading slash.
   */
  def leadingSlash: Boolean = segments.headOption.contains(Segment.root)

  /**
   * Checks if the path is not equal to ""
   */
  def nonEmpty: Boolean = !isEmpty

  /**
   * Creates a new path from this one with it's segments reversed.
   */
  def reverse: Path = Path(segments.reverse)

  /**
   * Checks if the path starts with the provided path
   */
  def startsWith(other: Path): Boolean = segments.startsWith(other.segments)

  /**
   * Creates a new path with the provided `n` initial segments.
   */
  def take(n: Int): Path = Path(segments.take(n))

  def textSegments: Vector[String] = segments.collect { case Segment.Text(text) => text }

  override def toString: String = encode

  /**
   * Checks if the path contains a trailing slash.
   */
  def trailingSlash: Boolean = segments.lastOption.contains(Segment.root)
}

object Path {

  /**
   * Represents a empty path which is equal to "".
   */
  val empty: Path = new Path(Vector.empty)

  /**
   * Represents a slash or a root path which is equivalent to "/".
   */
  val root: Path = new Path(Vector(Segment.root))

  def apply(segments: Vector[Segment]): Path = new Path({
    val trailingSlash = segments.lastOption.contains(Segment.root)
    val leadingSlash  = segments.headOption.contains(Segment.root)

    val head = if (leadingSlash) Vector(Segment.Root) else Vector.empty
    val tail = if (trailingSlash) Vector(Segment.Root) else Vector.empty

    val nonRoot = segments.filter(_ != Segment.Root)

    if (nonRoot.isEmpty && (leadingSlash || trailingSlash)) Vector(Segment.Root)
    else head ++ nonRoot ++ tail
  })

  /**
   * Decodes a path string into a Path. Can fail if the path is invalid.
   */
  def decode(path: String): Path = {
    if (path == "") Path.empty
    else if (path == "/") Path.root
    else Path(path.split("/", -1).toVector.map(Segment(_)))
  }

  sealed trait Segment extends Product with Serializable

  object Segment {
    def apply(text: String): Segment = text match {
      case ""   => Root
      case text => Text(text)
    }

    def root: Segment = Root

    final case class Text(text: String) extends Segment

    case object Root extends Segment
  }

}
