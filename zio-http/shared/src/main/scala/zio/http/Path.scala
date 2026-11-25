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

import zio.{Chunk, ChunkBuilder}

import zio.http.codec.PathCodec
import zio.http.internal.{QueryParamEncoding, ThreadLocals}

/**
 * Path is an immutable representation of the path of a URL. Internally it
 * stores each element of a path in a sequence of text, together with flags on
 * whether there are leading and trailing slashes in the path. This allows for a
 * combination of precision and performance and supports a rich API.
 *
 * The leading and trailing slashes count in `size`, `take`, `drop` and
 * `dropRight`. Two paths are equal when they have the same segments and the
 * same slash flags, so `/a` and `a` are different paths.
 *
 * {{{
 * val path    = Path("/users") / "42"
 * val encoded = path.encode // /users/42
 * val parent  = path.dropRight(1).encode // /users
 * val isUsers = path.startsWith(Path("/users")) // true
 * }}}
 *
 * @see
 *   [[https://zio.dev/zio-http/reference/routing/routes/ Routes]]
 * @param flags
 *   the slash flags, a bit set of `Path.Flag.LeadingSlash` and
 *   `Path.Flag.TrailingSlash`
 * @param segments
 *   the elements of the path, without slashes
 */
final case class Path private[http] (flags: Path.Flags, segments: Chunk[String]) { self =>
  import Path.{Flag, Flags}

  /**
   * Appends a segment at the end of the path.
   *
   * If there is already a trailing slash when you append a segment, then the
   * trailing slash will be removed (more precisely, it becomes the slash that
   * separates the existing path from the new segment). An empty `name` adds a
   * trailing slash instead.
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
   * the left path has a leading slash, and a trailing slash if the right path
   * has a trailing slash. Note that if you concat the empty path to any other
   * path, either on the left or right hand side, you get back that same path
   * unmodified.
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
    else if (segments.isEmpty) Path(Flags(Flag.LeadingSlash), Chunk.empty)
    else Path(Flag.LeadingSlash.add(flags), segments)

  /**
   * Appends a trailing slash to the path.
   */
  def addTrailingSlash: Path =
    if (hasTrailingSlash) self
    else if (segments.isEmpty) Path(Flags(Flag.TrailingSlash), Chunk.empty)
    else Path(Flag.TrailingSlash.add(flags), segments)

  /**
   * Named alias to `++` operator.
   */
  def concat(other: Path): Path = self ++ other

  /**
   * Drops the first n segments from the path. A leading slash counts as one
   * segment. A trailing slash is not dropped, so dropping every segment of a
   * path that has a trailing slash leaves `/`. A non-positive `n` returns the
   * path unchanged.
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
   * slashes as segments. A non-positive `n` returns the path unchanged.
   */
  def dropRight(n: Int): Path = take(size - n)

  /**
   * Drops the leading slash if available. The root path becomes the empty path.
   */
  def dropLeadingSlash: Path =
    if (isRoot) Path.empty
    else if (!Flag.LeadingSlash.check(flags)) self
    else copy(Flag.LeadingSlash.remove(flags))

  /**
   * Drops the trailing slash if available. The root path becomes the empty
   * path.
   */
  def dropTrailingSlash: Path =
    if (isRoot) Path.empty
    else if (!Flag.TrailingSlash.check(flags)) self
    else copy(flags = Flag.TrailingSlash.remove(flags))

  /**
   * Returns the path as a string: the segments joined with `/`, with a leading
   * `/` and a trailing `/` when the path has them. The empty path gives `""`
   * and the root path gives `"/"`.
   */
  def encode: String =
    if (isEmpty) ""
    else if (self == Path.root) "/"
    else segments.mkString(if (hasLeadingSlash) "/" else "", "/", if (hasTrailingSlash) "/" else "")

  private[http] def encodeBuilder: java.lang.StringBuilder = {
    val sb      = ThreadLocals.stringBuilder
    if (hasLeadingSlash) sb.append('/')
    var idx     = 0
    val lastIdx = segments.length - 1
    while (idx <= lastIdx) {
      // isPath = true allows ':' and '@' unescaped per RFC 3986 pchar rule
      QueryParamEncoding.encodeComponentInto(segments(idx), Charsets.Http, sb, "%20", isPath = true)
      if (hasTrailingSlash || idx != lastIdx) sb.append('/')
      idx += 1
    }
    sb
  }

  /**
   * Returns `true` when `that` is a `Path` with the same segments and the same
   * slash flags, after both are normalized.
   */
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

  /** Returns a hash code that is consistent with `equals`. */
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
  def isEmpty: Boolean = (self eq Path.empty) || segments.isEmpty && (flags == Flags.none)

  /**
   * Checks if the path is equal to "/". A path with no segments and any slash,
   * such as the one decoded from `//`, is the root path.
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
   * RFC 3986 § 5.2.4 Remove Dot Segments
   * @return
   *   the Path with `.` and `..` resolved and removed
   */
  def removeDotSegments: Path = {
    // See https://www.rfc-editor.org/rfc/rfc3986#section-5.2.4
    // Fast path: skip all allocations when no dot segments exist (the common case)
    var noDots = true
    var j      = 0
    while (j < self.segments.length && noDots) {
      val s = self.segments(j)
      if (s == "." || s == "..") noDots = false
      j += 1
    }
    if (noDots) self
    else {
      val segments     = new Array[String](self.segments.length)
      var segmentCount = 0
      // leading/trailing slashes may change but is unlikely
      var flags        = self.flags

      var i   = 0
      val max = self.segments.length

      if (!Flag.LeadingSlash.check(flags)) {
        // § 5.2.4.2.A/D no leading slash, so skip all initial `./` and `../`
        while (i < max && (self.segments(i) == "." | self.segments(i) == "..")) {
          i += 1
        }
        // if the entire input was consumed, there is no more trailing slash
        if (i == max) flags = Flag.TrailingSlash.remove(flags)
      }

      var loop = i < max
      while (loop) {
        val segment = self.segments(i)

        i += 1
        loop = i < max

        if (segment == "..") {
          segmentCount = (segmentCount - 1).max(0)
          // § 5.2.4.2.C resolving `/..` and `/../` removes preceding slashes and is itself replaced by a slash
          // so if we popped the first one we definitely have a leading slash
          if (segmentCount == 0) flags = Flag.LeadingSlash.add(flags)
          // § 5.2.4.2.C resolving `/..` and `/../` are both as-if replaced by a `/`
          // so if this is the last segment, then we have a trailing slash
          if (i == max) flags = Flag.TrailingSlash.add(flags)
        } else if (segment == ".") {
          // § 5.2.4.2.B resolving `/.` and `/./` are both as-if replaced by a `/`
          // so if this is the last segment, then we have a trailing slash
          if (i == max) flags = Flag.TrailingSlash.add(flags)
        } else {
          segments(segmentCount) = segment
          segmentCount += 1
        }
      }

      Path(flags, Chunk.fromArray(segments.take(segmentCount)))
    }
  }

  /**
   * Creates a new path from this one with its segments reversed. The leading
   * and trailing slash flags swap, so `/a/b` becomes `b/a/`.
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
   * Checks if the path starts with the provided path: both paths must have the
   * same leading slash flag, and the segments of `other` must be a prefix of
   * the segments of this path. The trailing slash flags are not compared. For
   * example, `/a/b` starts with `/a` but does not start with `a`.
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

  /** Returns the same string as `encode`. */
  override def toString: String = encode

  /**
   * Splits off the first element of the path, for pattern matching. A path with
   * a leading slash gives `("", rest)` where `rest` is the path without the
   * leading slash, so `/a/b` gives `("", a/b)`. Otherwise the first segment is
   * returned together with the rest of the path, so `a/b` gives `("a", b)`. The
   * empty path gives `None`.
   */
  lazy val unapply: Option[(String, Path)] =
    if (hasLeadingSlash) Some(("", drop(1)))
    else if (segments.nonEmpty) Some((segments.head, copy(segments = segments.drop(1))))
    else if (hasTrailingSlash) Some(("", Path.empty))
    else None

  /**
   * Splits off the last element of the path, for pattern matching. A path with
   * a trailing slash gives `(rest, "")` where `rest` is `dropRight(1)`, so
   * `/a/` gives `(/a, "")`. Otherwise the last segment is returned together
   * with the rest of the path, so `a/b` gives `(a, "b")`. The root path gives
   * `(Path.empty, "")`, and the empty path gives `None`.
   */
  lazy val unapplyRight: Option[(Path, String)] =
    if (hasTrailingSlash) Some((dropRight(1), ""))
    else if (segments.nonEmpty) Some((copy(segments = segments.dropRight(1)), segments.last))
    else if (hasLeadingSlash) Some((Path.empty, ""))
    else None

  /**
   * Unnests a path that has been nested at the specified prefix. If the path is
   * not nested at the specified prefix, the same path is returned. When
   * `startsWith(prefix)` holds, it drops `prefix.size` elements, where a
   * leading and a trailing slash of the prefix count as one each, so `/a/b/c`
   * unnested at `/a` is `b/c`.
   */
  def unnest(prefix: Path): Path =
    if (startsWith(prefix)) drop(prefix.size) else self
}

/**
 * Constructors and building blocks for `Path`.
 */
object Path {

  /** Returns the same path as `Path.decode`. */
  def apply(path: String): Path = decode(path)

  /**
   * Decodes a path string into a Path. The string is split at `/`, empty
   * segments are dropped, and a leading or trailing `/` sets the matching slash
   * flag. Dot segments are removed as by `removeDotSegments`. The empty string
   * gives `Path.empty`.
   */
  def decode(path: String): Path =
    if (path.isEmpty) Path.empty
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

      Path(flags, chunkBuilder.result()).removeDotSegments
    }

  /**
   * Decodes a percent-encoded (raw) path string into a Path. This splits by '/'
   * first, then decodes each segment, so encoded slashes (%2F) within segments
   * are preserved as literal '/' characters in the segment value (not as path
   * separators).
   */
  private[http] def decodeRaw(path: String): Path =
    if (path.isEmpty) Path.empty
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
            val rawSegment = path.substring(lastSlash + 1, i)
            chunkBuilder += QueryParamEncoding.decodePathSegment(rawSegment, Charsets.Http)
          }

          lastSlash = i
        } else if (i == max) {
          loop = false

          val segmentLen = i - lastSlash
          if (segmentLen > 0) {
            val rawSegment = path.substring(lastSlash + 1, i + 1)
            chunkBuilder += QueryParamEncoding.decodePathSegment(rawSegment, Charsets.Http)
          }
        }
        i = i + 1
      }

      Path(flags, chunkBuilder.result()).removeDotSegments
    }

  /**
   * Represents an empty path which is rendered as "".
   */
  val empty: Path = Path(Flags.none, Chunk.empty)

  /**
   * Represents a slash or a root path which is equivalent to "/".
   */
  val root: Path = Path(Flags(Flag.LeadingSlash, Flag.TrailingSlash), Chunk.empty)

  /**
   * Creates a PathCodec[Unit] from this path for convenient round-trip
   * conversion. Useful for encoding paths back into PathCodec form. Each
   * segment becomes a literal, the leading and trailing slashes are not
   * represented, and the empty and root paths give `PathCodec.empty`.
   *
   * @return
   *   a PathCodec[Unit] that matches this path
   */
  def toPathCodec(path: Path): PathCodec[Unit] = {
    if (path.isEmpty || path.isRoot) PathCodec.empty
    else {
      path.segments.foldLeft[PathCodec[Unit]](PathCodec.empty) { (codec, segment) =>
        codec / PathCodec.literal(segment)
      }
    }
  }

  /** The slash flags of a path, stored as the bits of an `Int`. */
  type Flags = Int

  /** Operations on `Path.Flags` values. */
  object Flags {

    /**
     * Returns the flags with the bits of `flag` and of every flag in `flags`
     * set.
     */
    def apply(flag: Flag, flags: Flag*): Flags =
      flags.foldLeft(flag.mask)((acc, flag) => acc | flag.mask)

    /**
     * Returns the flags of the concatenation of two paths: the leading slash
     * flag of `first` and the trailing slash flag of `last`.
     */
    def concat(first: Flags, last: Flags): Int = {
      var result = 0
      if (Flag.LeadingSlash.check(first)) result = result | Flag.LeadingSlash.mask
      if (Flag.TrailingSlash.check(last)) result = result | Flag.TrailingSlash.mask
      result
    }

    /** Returns `true` when both flags have the same bits. */
    def equivalent(left: Flags, right: Flags): Boolean =
      essential(left) == essential(right)

    /** Returns the flags unchanged. */
    def essential(flags: Flags): Flags = flags

    /** The flags with no slash set. */
    val none: Flags = 0

    /** Returns the flags with the leading and trailing slash bits swapped. */
    def reverse(flags: Flags): Int = {
      var result = 0
      if (Flag.LeadingSlash.check(flags)) result |= Flag.TrailingSlash.mask
      if (Flag.TrailingSlash.check(flags)) result |= Flag.LeadingSlash.mask
      result
    }

  }

  /**
   * One of the slash flags of a path: `Flag.LeadingSlash` or
   * `Flag.TrailingSlash`.
   */
  sealed trait Flag {
    private[http] val shift: Int
    private[http] val mask: Int
    private[http] val invertMask: Int

    private[http] def check(flags: Int): Boolean = (flags & mask) != 0

    private[http] def add(flags: Flags): Flags = flags | mask

    private[http] def remove(flags: Flags): Flags = flags & invertMask
  }

  /** The two slash flags. */
  object Flag {

    /**
     * The flag of a path that starts with `/`. Used as a function, it adds a
     * leading slash to a path, and used as an extractor it matches a path that
     * has one and returns the path without it.
     */
    case object LeadingSlash extends Flag {
      private[http] final val shift      = 0
      private[http] final val mask       = 1 << shift
      private[http] final val invertMask = ~mask

      /** Returns `path.addLeadingSlash`. */
      def apply(path: Path): Path = path.addLeadingSlash

      /**
       * Matches a path with a leading slash and returns
       * `path.dropLeadingSlash`.
       */
      def unapply(path: Path): Option[Path] =
        if (path.hasLeadingSlash) Some(path.dropLeadingSlash) else None
    }

    /**
     * The flag of a path that ends with `/`. Used as a function, it adds a
     * trailing slash to a path, and used as an extractor it matches a path that
     * has one and returns the path without it.
     */
    case object TrailingSlash extends Flag {
      private[http] final val shift      = 1
      private[http] final val mask       = 1 << shift
      private[http] final val invertMask = ~mask

      /** Returns `path.addTrailingSlash`. */
      def apply(path: Path): Path = path.addTrailingSlash

      /**
       * Matches a path with a trailing slash and returns
       * `path.dropTrailingSlash`.
       */
      def unapply(path: Path): Option[Path] =
        if (path.hasTrailingSlash) Some(path.dropTrailingSlash) else None
    }
  }
}
