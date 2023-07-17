/*
 * Copyright 2021 - 2023 Sporta Technologies PVT LTD & the ZIO HTTP contributors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package zio.http

import scala.collection.mutable

import zio.{Chunk, ChunkBuilder}

/**
 * Path is an immutable representation of the path of a URL. Internally it
 * stores each element of a path in a sequence of text, together with flags on
 * whether there are leading and trailing slashes in the path. This allows for a
 * combination of precision and performance and supports a rich API.
 */
final case class Path private (flags: Path.Flags, segments: Chunk[String]) { self =>
  import Path.{Flag, Flags}

  /**
   * Appends a segment at the end of the path.
   *
   * If there is already a trailing slash when you append a segment, then the
   * trailing slash will be removed (more precisely, it becomes the slash that
   * separates the existing path from the new segment).
   */
  def /(name: String): Path =
    if (name == "") addTrailingSlash
    else if (isRoot) Path(Flags(Flag.LeadingSlash), Chunk(name))
    else Path(Flag.TrailingSlash.remove(flags), segments = segments :+ name)

  /**
   * Prepends the path with the provided segment.
   *
   * If there is already a leading slash when you prepend a segment, then the
   * leading slash will be removed (more precisely, it becomes the slash that
   * separates the new segment from the existing path).
   */
  def /:(name: String): Path =
    if (name == "") addLeadingSlash
    else if (isRoot) Path(Flags(Flag.TrailingSlash), Chunk(name))
    else Path(Flag.LeadingSlash.remove(flags), segments = name +: segments)

  /**
   * Combines two paths together to create a new one, having a leading slash if
   * only the left path has a leading slash, and a trailing slash if only the
   * right path has a trailing slash. Note that if you concat the empty path to
   * any other path, either on the left or right hand side, you get back that
   * same path unmodified.
   */
  def ++(that: Path): Path =
    if (self.isEmpty) that
    else if (that.isEmpty) self
    else Path(Flags.concat(self.normalize.flags, that.normalize.flags), self.segments ++ that.segments)

  /**
   * Prepends a leading slash to the path.
   */
  def addLeadingSlash: Path =
    if (hasLeadingSlash) self
    else if (segments.length == 0) Path(Flags(Flag.LeadingSlash), Chunk.empty)
    else Path(Flag.LeadingSlash.add(flags), segments)

  /**
   * Appends a trailing slash to the path.
   */
  def addTrailingSlash: Path =
    if (hasTrailingSlash) self
    else if (segments.length == 0) Path(Flags(Flag.TrailingSlash), Chunk.empty)
    else Path(Flag.TrailingSlash.add(flags), segments)

  /**
   * Named alias to `++` operator.
   */
  def concat(other: Path): Path = self ++ other

  /**
   * Drops the first n segments from the path, treating both leading and
   * trailing slashes as segments.
   */
  def drop(n: Int): Path =
    if (n <= 0) self
    else {
      if (isRoot) Path.empty
      else if (hasLeadingSlash) dropLeadingSlash.drop(n - 1)
      else copy(segments = segments.drop(n))
    }

  /**
   * Drops the last n segments from the path, treating both leading and trailing
   * slashes as segments.
   */
  def dropRight(n: Int): Path = take(size - n)

  /**
   * Drops the leading slash if available.
   */
  def dropLeadingSlash: Path =
    if (isRoot) Path.empty
    else if (!Flag.LeadingSlash.check(flags)) self
    else copy(Flag.LeadingSlash.remove(flags))

  /**
   * Drops the trailing slash if available.
   */
  def dropTrailingSlash: Path =
    if (isRoot) Path.empty
    else if (!Flag.TrailingSlash.check(flags)) self
    else copy(flags = Flag.TrailingSlash.remove(flags))

  /**
   * Encodes the current path into a valid string.
   */
  def encode: String =
    if (self == Path.empty) ""
    else if (self == Path.root) "/"
    else segments.mkString(if (hasLeadingSlash) "/" else "", "/", if (hasTrailingSlash) "/" else "")

  override def equals(that: Any): Boolean =
    that match {
      case that: Path =>
        val normalLeft  = self.normalize
        val normalRight = that.normalize

        Flags.equivalent(normalLeft.flags, normalRight.flags) && normalLeft.segments == normalRight.segments

      case _ => false
    }

  /**
   * Checks if the path contains a leading slash.
   */
  def hasLeadingSlash: Boolean = Flag.LeadingSlash.check(flags)

  /**
   * Checks if the path contains a trailing slash.
   */
  def hasTrailingSlash: Boolean = Flag.TrailingSlash.check(flags)

  override def hashCode: Int = {
    val normalized = normalize

    var result = 17
    result = 31 * result + Flags.essential(normalized.flags)
    result = 31 * result + normalized.segments.hashCode
    result
  }

  /**
   * Checks if the path is equal to "".
   */
  def isEmpty: Boolean = segments.isEmpty && (flags == Flags.none)

  /**
   * Checks if the path is equal to "/".
   * @return
   */
  def isRoot: Boolean = segments.isEmpty && (hasLeadingSlash || hasTrailingSlash)

  /**
   * Checks if the path is not equal to "".
   */
  def nonEmpty: Boolean = !isEmpty

  /**
   * Normalizes the path for proper equals/hashCode treatment.
   */
  def normalize: Path =
    if (segments.isEmpty) {
      if (hasLeadingSlash) Path.root
      else if (hasTrailingSlash) Path.root
      else Path.empty
    } else self

  /**
   * Creates a new path from this one with it's segments reversed.
   */
  def reverse: Path = Path(Flags.reverse(flags), segments.reverse)

  /**
   * Returns the "size" of a path, which counts leading and trailing slash, if
   * present.
   */
  def size: Int =
    if (isEmpty) 0
    else if (isRoot) 1
    else segments.length + (if (hasLeadingSlash) 1 else 0) + (if (hasTrailingSlash) 1 else 0)

  /**
   * Checks if the path starts with the provided path
   */
  def startsWith(other: Path): Boolean =
    (self.hasLeadingSlash == other.hasLeadingSlash) && segments.startsWith(other.segments)

  /**
   * Returns a new path containing the first n segments of the path, treating
   * both leading and trailing slashes as segments.
   */
  def take(n: Int): Path =
    if (n <= 0) Path.empty
    else {
      if (n >= size) self
      else Path(Flag.TrailingSlash.remove(flags), segments = segments.take(n - (if (hasLeadingSlash) 1 else 0)))
    }

  override def toString: String = encode

  lazy val unapply: Option[(String, Path)] =
    if (hasLeadingSlash) Some(("", drop(1)))
    else if (segments.nonEmpty) Some((segments.head, copy(segments = segments.drop(1))))
    else if (hasTrailingSlash) Some(("", Path.empty))
    else None

  lazy val unapplyRight: Option[(Path, String)] =
    if (hasTrailingSlash) Some((dropRight(1), ""))
    else if (segments.nonEmpty) Some((copy(segments = segments.dropRight(1)), segments.last))
    else if (hasLeadingSlash) Some((Path.empty, ""))
    else None

  /**
   * Unnests a path that has been nested at the specified prefix. If the path is
   * not nested at the specified prefix, the same path is returned.
   */
  def unnest(prefix: Path): Path =
    if (startsWith(prefix)) drop(prefix.size) else self
}

object Path {
  def apply(path: String): Path = decode(path)

  /**
   * Decodes a path string into a Path.
   */
  def decode(path: String): Path =
    if (path.length == 0) Path.empty
    else {
      val chunkBuilder = ChunkBuilder.make[String]()

      var flags: Path.Flags = Path.Flags.none

      val max       = path.length - 1
      var lastSlash = -1

      var i    = 0
      var loop = true
      while (loop) {
        val char = path.charAt(i)
        if (char == '/') {
          if (i == 0) {
            flags = Path.Flag.LeadingSlash.add(flags)
          }
          if (i == max) {
            if (i != 0) flags = Path.Flag.TrailingSlash.add(flags)
            loop = false
          }
          val segmentLen = (i - 1) - lastSlash

          if (segmentLen > 0) {
            chunkBuilder += path.substring(lastSlash + 1, i)
          }

          lastSlash = i
        } else if (i == max) {
          loop = false

          val segmentLen = i - lastSlash
          if (segmentLen > 0) {
            chunkBuilder += path.substring(lastSlash + 1, i + 1)
          }
        }
        i = i + 1
      }

      Path(flags, chunkBuilder.result())
    }

  /**
   * Represents a empty path which is rendered as "".
   */
  val empty: Path = Path(Flags.none, Chunk.empty)

  /**
   * Represents a slash or a root path which is equivalent to "/".
   */
  val root: Path = Path(Flags(Flag.LeadingSlash, Flag.TrailingSlash), Chunk.empty)

  type Flags = Int
  object Flags {
    def apply(flag: Flag, flags: Flag*): Flags =
      flags.foldLeft(flag.mask)((acc, flag) => acc | flag.mask)

    def concat(first: Flags, last: Flags): Int = {
      var result = 0
      if (Flag.LeadingSlash.check(first)) result = result | Flag.LeadingSlash.mask
      if (Flag.TrailingSlash.check(last)) result = result | Flag.TrailingSlash.mask
      result
    }

    def equivalent(left: Flags, right: Flags): Boolean =
      essential(left) == essential(right)

    def essential(flags: Flags): Flags = flags

    val none: Flags = 0

    def reverse(flags: Flags): Int = {
      var result = 0
      if (Flag.LeadingSlash.check(flags)) result |= Flag.TrailingSlash.mask
      if (Flag.TrailingSlash.check(flags)) result |= Flag.LeadingSlash.mask
      result
    }

  }

  sealed trait Flag {
    private[http] val shift: Int
    private[http] val mask: Int
    private[http] val invertMask: Int

    private[http] def check(flags: Int): Boolean = (flags & mask) != 0

    private[http] def add(flags: Flags): Flags = flags | mask

    private[http] def remove(flags: Flags): Flags = flags & invertMask
  }
  object Flag       {
    case object LeadingSlash  extends Flag {
      private[http] final val shift      = 0
      private[http] final val mask       = 1 << shift
      private[http] final val invertMask = ~mask

      def apply(path: Path): Path = path.addLeadingSlash

      def unapply(path: Path): Option[Path] =
        if (path.hasLeadingSlash) Some(path.dropLeadingSlash) else None
    }
    case object TrailingSlash extends Flag {
      private[http] final val shift      = 1
      private[http] final val mask       = 1 << shift
      private[http] final val invertMask = ~mask

      def apply(path: Path): Path = path.addTrailingSlash

      def unapply(path: Path): Option[Path] =
        if (path.hasTrailingSlash) Some(path.dropTrailingSlash) else None
    }
  }
}
