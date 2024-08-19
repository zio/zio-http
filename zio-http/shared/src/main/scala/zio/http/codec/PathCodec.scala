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

package zio.http.codec

import scala.annotation.tailrec
import scala.collection.immutable.ListMap
import scala.collection.mutable
import scala.language.implicitConversions

import zio._

import zio.http._

/**
 * A codec for paths, which consists of segments, where each segment may be a
 * literal, an integer, a long, a string, a UUID, or the trailing path.
 *
 * {{{
 * import zio.http.endpoint.PathCodec._
 *
 * val pathCodec = empty / "users" / int("user-id") / "posts" / string("post-id")
 * }}}
 */
sealed trait PathCodec[A] { self =>
  import PathCodec._

  /**
   * Attaches documentation to the path codec, which may be used when generating
   * developer docs for a route.
   */
  def ??(doc: Doc): PathCodec[A] =
    self.annotate(MetaData.Documented(doc))

  final def ++[B](that: PathCodec[B])(implicit combiner: Combiner[A, B]): PathCodec[combiner.Out] =
    PathCodec.Concat(self, that, combiner)

  final def /[B](that: PathCodec[B])(implicit combiner: Combiner[A, B]): PathCodec[combiner.Out] =
    self ++ that

  final def /[Env, Err](routes: Routes[Env, Err])(implicit
    ev: PathCodec[A] <:< PathCodec[Unit],
  ): Routes[Env, Err] =
    routes.nest(ev(self))

  final def annotate(metaData: MetaData[A]): PathCodec[A] = {
    self match {
      case Annotated(codc, annotations) => Annotated(codc, annotations :+ metaData)
      case _                            => Annotated(self, Chunk(metaData))
    }
  }

  private[http] def orElse(value: PathCodec[Unit])(implicit ev: A =:= Unit): PathCodec[Unit] =
    Fallback(self.asInstanceOf[PathCodec[Unit]], value)

  private def fallbackAlternatives(f: Fallback[_]): List[PathCodec[Any]] = {
    @tailrec
    def loop(codecs: List[PathCodec[_]], result: List[PathCodec[_]]): List[PathCodec[_]] =
      if (codecs.isEmpty) result
      else
        codecs.head match {
          case PathCodec.Annotated(codec, _)              =>
            loop(codec :: codecs.tail, result)
          case PathCodec.Segment(SegmentCodec.Literal(_)) =>
            loop(codecs.tail, result :+ codecs.head)
          case PathCodec.Segment(SegmentCodec.Empty)      =>
            loop(codecs.tail, result)
          case Fallback(left, right)                      =>
            loop(left :: right :: codecs.tail, result)
          case other                                      =>
            throw new IllegalStateException(s"Alternative path segments should only contain literals, found: $other")
        }
    loop(List(f.left, f.right), List.empty).asInstanceOf[List[PathCodec[Any]]]
  }

  final def alternatives: List[PathCodec[A]] = {
    var alts                                                      = List.empty[PathCodec[Any]]
    def loop(codec: PathCodec[_], combiner: Combiner[_, _]): Unit = codec match {
      case Concat(left, right, combiner) =>
        loop(left, combiner)
        loop(right, combiner)
      case f: Fallback[_]                =>
        if (alts.isEmpty) alts = fallbackAlternatives(f)
        else
          alts ++= alts.flatMap { alt =>
            fallbackAlternatives(f).map(fa =>
              Concat(alt, fa.asInstanceOf[PathCodec[Any]], combiner.asInstanceOf[Combiner.WithOut[Any, Any, Any]]),
            )
          }
      case Segment(SegmentCodec.Empty)   =>
        alts :+= codec.asInstanceOf[PathCodec[Any]]
      case pc                            =>
        if (alts.isEmpty) alts :+= pc.asInstanceOf[PathCodec[Any]]
        else
          alts = alts
            .map(l =>
              Concat(l, pc.asInstanceOf[PathCodec[Any]], combiner.asInstanceOf[Combiner.WithOut[Any, Any, Any]])
                .asInstanceOf[PathCodec[Any]],
            )
    }
    loop(self, Combiner.leftUnit[Unit])
    alts.asInstanceOf[List[PathCodec[A]]]
  }

  final def asType[B](implicit ev: A =:= B): PathCodec[B] = self.asInstanceOf[PathCodec[B]]

  /**
   * Decodes a method and path into a value of type `A`.
   */
  final def decode(path: Path): Either[String, A] = {
    import PathCodec.Opt._

    val instructions = optimize
    val segments     = path.segments

    var i                           = 0
    var j                           = 0
    var fail                        = ""
    val stack: java.util.Deque[Any] = new java.util.ArrayDeque[Any](2)

    // For root:
    stack.push(())

    while (i < instructions.length) {
      val opt = instructions(i)

      opt match {
        case Match(value)     =>
          if (j >= segments.length || segments(j) != value) {
            fail = "Expected path segment \"" + value + "\" but found end of path"
            i = instructions.length
          } else {
            stack.push(())
            j = j + 1
          }
        case MatchAny(values) =>
          if (j >= segments.length || !values.contains(segments(j))) {
            fail = "Expected one of the following path segments: " + values.mkString(", ") + " but found end of path"
            i = instructions.length
          } else {
            stack.push(())
            j = j + 1
          }

        case Combine(combiner0) =>
          val combiner = combiner0.asInstanceOf[Combiner[Any, Any]]
          val right    = stack.pop()
          val left     = stack.pop()
          stack.push(combiner.combine(left, right))

        case IntOpt =>
          if (j >= segments.length) {
            fail = "Expected integer path segment but found end of path"
            i = instructions.length
          } else {
            val segment = segments(j)
            j = j + 1
            try {
              stack.push(segment.toInt)
            } catch {
              case _: NumberFormatException =>
                fail = "Expected integer path segment but found \"" + segment + "\""
                i = instructions.length
            }
          }

        case LongOpt   =>
          if (j >= segments.length) {
            fail = "Expected long path segment but found end of path"
            i = instructions.length
          } else {
            val segment = segments(j)
            j = j + 1
            try {
              stack.push(segment.toLong)
            } catch {
              case _: NumberFormatException =>
                fail = s"Expected long path segment but found ${segment}"
                i = instructions.length
            }
          }
        case StringOpt =>
          if (j >= segments.length) {
            fail = "Expected text path segment but found end of path"
            i = instructions.length
          } else {
            val segment = segments(j)
            j = j + 1
            stack.push(segment)
          }

        case UUIDOpt =>
          if (j >= segments.length) {
            fail = "Expected UUID path segment but found end of path"
            i = instructions.length
          } else {
            val segment = segments(j)
            j = j + 1
            try {
              stack.push(java.util.UUID.fromString(segment))
            } catch {
              case _: IllegalArgumentException =>
                fail = s"Expected UUID path segment but found ${segment}"
                i = instructions.length
            }
          }

        case BoolOpt =>
          if (j >= segments.length) {
            fail = "Expected boolean path segment but found end of path"
            i = instructions.length
          } else {
            val segment = segments(j)
            j = j + 1

            if (segment.equalsIgnoreCase("true")) {
              stack.push(true)
            } else if (segment.equalsIgnoreCase("false")) {
              stack.push(false)
            } else {
              fail = s"Expected boolean path segment but found ${segment}"
              i = instructions.length
            }
          }

        case TrailingOpt =>
          // Consume all Trailing, possibly empty:
          if (j >= segments.length) {
            val result =
              if (path.hasTrailingSlash) Path.root else Path.empty

            stack.push(result)
          } else {
            val flags =
              if (j == 0) path.flags
              else if (path.hasTrailingSlash) Path.Flags(Path.Flag.TrailingSlash)
              else 0

            stack.push(Path(flags, segments.drop(j)))
            j = segments.length
          }

        case Unit =>
          stack.push(())

        case MapOrFail(f) =>
          f(stack.pop) match {
            case Left(failure) =>
              fail = failure
              i = instructions.length
            case Right(value)  =>
              stack.push(value)
          }

        case SubSegmentOpts(ops) =>
          val error = decodeSubstring(segments(j), ops, stack)
          if (error != null) {
            fail = error
            i = instructions.length
          } else {
            j += 1
          }
      }

      i = i + 1
    }
    if (fail != "") Left(fail)
    else {
      if (j < segments.length) {
        val rest = segments.drop(j).mkString("/")
        Left(s"Expected end of path but found: ${rest}")
      } else {
        Right(stack.pop().asInstanceOf[A])
      }
    }
  }

  private def decodeSubstring(
    value: String,
    instructions: Array[Opt],
    stack: java.util.Deque[Any],
  ): String = {
    import Opt._

    var i    = 0
    var j    = 0
    val size = value.length
    while (i < instructions.length) {
      val opt = instructions(i)
      opt match {
        case Match(toMatch)     =>
          val size0 = toMatch.length
          if ((size - j) < size0) {
            return "Expected \"" + toMatch + "\" in segment " + value + " but found end of segment"
          } else if (value.startsWith(toMatch, j)) {
            stack.push(())
            j += size0
          } else {
            return "Expected \"" + toMatch + "\" in segment " + value + " but found: " + value.substring(j)
          }
        case Combine(combiner0) =>
          val combiner = combiner0.asInstanceOf[Combiner[Any, Any]]
          val right    = stack.pop()
          val left     = stack.pop()
          stack.push(combiner.combine(left, right))
        case StringOpt          =>
          // Here things get "interesting" (aka annoying). We don't have a way of knowing when a string ends,
          // so we have to look ahead to the next operator and figure out where it begins
          val end = indexOfNextCodec(value, instructions, i, j)
          if (end == -1) { // If this wasn't the last codec, let the error handler of the next codec handle this
            stack.push(value.substring(j))
            j = size
          } else {
            stack.push(value.substring(j, end))
            j = end
          }
        case IntOpt             =>
          val isNegative = value(j) == '-'
          if (isNegative) j += 1
          var end        = j
          while (end < size && value(end).isDigit) end += 1
          if (end == j) {
            return "Expected integer path segment but found end of segment"
          } else if (end - j > 10) {
            return "Expected integer path segment but found: " + value.substring(j, end)
          } else {

            try {
              val int = Integer.parseInt(value, j, end, 10)
              j = end
              if (isNegative) stack.push(-int) else stack.push(int)
            } catch {
              case _: NumberFormatException =>
                return "Expected integer path segment but found: " + value.substring(j, end)
            }
          }
        case LongOpt            =>
          val isNegative = value(j) == '-'
          if (isNegative) j += 1
          var end        = j
          while (end < size && value(end).isDigit) end += 1
          if (end == j) {
            return "Expected long path segment but found end of segment"
          } else if (end - j > 19) {
            return "Expected long path segment but found: " + value.substring(j, end)
          } else {
            try {
              val long = java.lang.Long.parseLong(value, j, end, 10)
              j = end
              if (isNegative) stack.push(-long) else stack.push(long)
            } catch {
              case _: NumberFormatException => return "Expected long path segment but found: " + value.substring(j, end)
            }
          }
        case UUIDOpt            =>
          if ((size - j) < 36) {
            return "Remaining path segment " + value.substring(j) + " is too short to be a UUID"
          } else {
            val sub = value.substring(j, j + 36)
            try {
              stack.push(java.util.UUID.fromString(sub))
            } catch {
              case _: IllegalArgumentException => return "Expected UUID path segment but found: " + sub
            }
            j += 36
          }
        case BoolOpt            =>
          if (value.regionMatches(true, j, "true", 0, 4)) {
            stack.push(true)
            j += 4
          } else if (value.regionMatches(true, j, "false", 0, 5)) {
            stack.push(false)
            j += 5
          } else {
            return "Expected boolean path segment but found end of segment"
          }
        case TrailingOpt        =>
          // TrailingOpt must be invalid, since it wants to extract a path,
          // which is not possible in a sub part of a segment.
          // The equivalent of trailing here is just StringOpt
          throw new IllegalStateException("TrailingOpt is not allowed in a sub segment")
        case _                  =>
          throw new IllegalStateException("Unexpected instruction in substring decoder")
      }
      i += 1
    }
    if (j != size) "Expected end of segment but found: " + value.substring(j)
    else null
  }

  private def indexOfNextCodec(value: String, instructions: Array[Opt], fromI: Int, idx: Int): Int = {
    import Opt._

    var nextOpt = null.asInstanceOf[Opt]
    var j1      = fromI + 1

    while ((nextOpt eq null) && j1 < instructions.length) {
      instructions(j1) match {
        case op @ (Match(_) | IntOpt | LongOpt | UUIDOpt | BoolOpt) =>
          nextOpt = op
        case _                                                      =>
          j1 += 1
      }
    }

    nextOpt match {
      case null             =>
        -1
      case Match(toMatch)   =>
        if (idx + toMatch.length > value.length) -1
        else if (toMatch.length == 1) value.indexOf(toMatch.charAt(0).toInt, idx)
        else value.indexOf(toMatch, idx)
      case IntOpt | LongOpt =>
        value.indexWhere(_.isDigit, idx)
      case BoolOpt          =>
        val t = value.regionMatches(true, idx, "true", 0, 4)
        if (t) idx + 4 else if (value.regionMatches(true, idx, "false", 0, 5)) idx + 5 else -1
      case UUIDOpt          =>
        val until = SegmentCodec.UUID.inUUIDUntil(value, idx)
        if (until == -1) -1 else idx + until
      case MatchAny(values) =>
        var end      = -1
        val valuesIt = values.iterator
        while (valuesIt.hasNext && end == -1) {
          val value = valuesIt.next()
          val index = value.indexOf(value, idx)
          if (index != -1) end = index
        }
        end
      case _                =>
        throw new IllegalStateException("Unexpected instruction in substring decoder: " + nextOpt)
    }
  }

  /**
   * Returns the documentation for the path codec, if any.
   */
  def doc: Doc =
    self match {
      case Segment(_)                    => Doc.empty
      case TransformOrFail(api, _, _)    => api.doc
      case Concat(left, right, _)        => left.doc + right.doc
      case Annotated(codec, annotations) =>
        codec.doc + annotations.collectFirst { case MetaData.Documented(doc) => doc }.getOrElse(Doc.empty)
      case Fallback(left, right)         => left.doc + right.doc
    }

  /**
   * Encodes a value of type `A` into the method and path that this route
   * pattern would successfully match against.
   */
  final def encode(value: A): Either[String, Path] = format(value)

  private[http] final def erase: PathCodec[Any] = self.asInstanceOf[PathCodec[Any]]

  final def example(name: String, example: A): PathCodec[A] =
    annotate(MetaData.Examples(Map(name -> example)))

  final def examples(examples: (String, A)*): PathCodec[A] =
    annotate(MetaData.Examples(examples.toMap))

  /**
   * Formats a value of type `A` into a path. This is useful for embedding paths
   * into HTML that is rendered by the server.
   */
  final def format(value: A): Either[String, Path] = {
    def loop(path: PathCodec[_], value: Any): Either[String, Path] = path match {
      case PathCodec.Annotated(codec, _)           =>
        loop(codec, value)
      case PathCodec.Concat(left, right, combiner) =>
        val (leftValue, rightValue) = combiner.separate(value.asInstanceOf[combiner.Out])

        for {
          leftPath  <- loop(left, leftValue)
          rightPath <- loop(right, rightValue)
        } yield leftPath ++ rightPath

      case PathCodec.Segment(segment) =>
        Right(segment.format(value.asInstanceOf[segment.Type]))

      case PathCodec.TransformOrFail(api, _, g) =>
        g.asInstanceOf[Any => Either[String, Any]](value).flatMap(loop(api, _))
      case Fallback(left, _)                    =>
        loop(left, value)
    }

    loop(self, value).map { path =>
      if (path.nonEmpty) path.addLeadingSlash else path
    }
  }

  /**
   * Determines if this pattern matches the specified method and path. Rather
   * than use this method, you should just try to decode it directly, for higher
   * performance, otherwise the same information will be decoded twice.
   */
  final def matches(path: Path): Boolean =
    decode(path).isRight

  private var _optimize: Array[Opt] = null.asInstanceOf[Array[Opt]]

  private[http] def optimize: Array[Opt] = {

    def loopSegment(segment: SegmentCodec[_], fresh: Boolean)(implicit b: mutable.ArrayBuilder[Opt]): Unit =
      segment match {
        case SegmentCodec.Empty                           => b += Opt.Unit
        case SegmentCodec.Literal(value)                  => b += Opt.Match(value)
        case SegmentCodec.IntSeg(_)                       => b += Opt.IntOpt
        case SegmentCodec.LongSeg(_)                      => b += Opt.LongOpt
        case SegmentCodec.Text(_)                         => b += Opt.StringOpt
        case SegmentCodec.UUID(_)                         => b += Opt.UUIDOpt
        case SegmentCodec.BoolSeg(_)                      => b += Opt.BoolOpt
        case SegmentCodec.Trailing                        => b += Opt.TrailingOpt
        case SegmentCodec.Combined(left, right, combiner) =>
          val ab = if (fresh) mutable.ArrayBuilder.make[Opt] else b
          loopSegment(left, fresh = false)(ab)
          loopSegment(right, fresh = false)(ab)
          ab += Opt.Combine(combiner)
          if (fresh) b += Opt.SubSegmentOpts(ab.result().asInstanceOf[Array[Opt]])
      }

    def loop(pattern: PathCodec[_])(implicit b: mutable.ArrayBuilder[Opt]): Unit =
      pattern match {
        case PathCodec.Annotated(codec, _) =>
          loop(codec)
        case PathCodec.Segment(segment)    =>
          loopSegment(segment, fresh = true)
        case f: Fallback[_]                =>
          b += Opt.MatchAny(fallbacks(f))
        case Concat(left, right, combiner) =>
          loop(left)
          loop(right)
          b += Opt.Combine(combiner)
        case TransformOrFail(api, f, _)    =>
          loop(api)
          b += Opt.MapOrFail(f.asInstanceOf[Any => Either[String, Any]])
      }

    if (_optimize eq null) {
      val b: mutable.ArrayBuilder[Opt] = mutable.ArrayBuilder.make[Opt]
      loop(self)(b)
      _optimize = b.result()
    }

    _optimize
  }

  private def fallbacks(f: Fallback[_]): Set[String] = {
    @tailrec
    def loop(codecs: List[PathCodec[_]], result: Set[String]): Set[String] =
      if (codecs.isEmpty) result
      else
        codecs.head match {
          case PathCodec.Annotated(codec, _)                  =>
            loop(codec :: codecs.tail, result)
          case PathCodec.Segment(SegmentCodec.Literal(value)) =>
            loop(codecs.tail, result + value)
          case PathCodec.Segment(SegmentCodec.Empty)          =>
            loop(codecs.tail, result)
          case Fallback(left, right)                          =>
            loop(left :: right :: codecs.tail, result)
          case other                                          =>
            throw new IllegalStateException(s"Alternative path segments should only contain literals, found: $other")
        }
    loop(List(f.left, f.right), Set.empty)
  }

  /**
   * Renders the path codec as a string.
   */
  def render: String =
    render("{", "}")

  /**
   * Renders the path codec as a string. Surrounds the path variables with the
   * specified prefix and suffix.
   */
  def render(prefix: String, suffix: String): String = {
    def loop(path: PathCodec[_]): String = path match {
      case PathCodec.Annotated(codec, _)        =>
        loop(codec)
      case PathCodec.Concat(left, right, _)     =>
        loop(left) + loop(right)
      case PathCodec.Segment(segment)           =>
        segment.render(prefix, suffix)
      case PathCodec.TransformOrFail(api, _, _) =>
        loop(api)
      case PathCodec.Fallback(left, _)          =>
        loop(left)
    }

    loop(self)
  }

  private[zio] def renderIgnoreTrailing: String =
    renderIgnoreTrailing("{", "}")

  private[zio] def renderIgnoreTrailing(prefix: String, suffix: String): String = {
    def loop(path: PathCodec[_]): String = path match {
      case PathCodec.Annotated(codec, _)    =>
        loop(codec)
      case PathCodec.Concat(left, right, _) =>
        loop(left) + loop(right)

      case PathCodec.Segment(SegmentCodec.Trailing) => ""

      case PathCodec.Segment(segment) => segment.render(prefix, suffix)

      case PathCodec.TransformOrFail(api, _, _) => loop(api)

      case PathCodec.Fallback(left, _) => loop(left)
    }

    loop(self)
  }

  /**
   * Returns the segments of the path codec.
   */
  def segments: Chunk[SegmentCodec[_]] = {
    def loop(path: PathCodec[_]): Chunk[SegmentCodec[_]] = path match {
      case PathCodec.Annotated(codec, _) =>
        loop(codec)
      case PathCodec.Segment(segment)    => Chunk(segment)

      case PathCodec.Concat(left, right, _) =>
        loop(left) ++ loop(right)

      case PathCodec.TransformOrFail(api, _, _) =>
        loop(api)

      case PathCodec.Fallback(left, _) =>
        loop(left)
    }

    loop(self)
  }

  override def toString(): String = render

  final def transform[A2](f: A => A2)(g: A2 => A): PathCodec[A2] =
    PathCodec.TransformOrFail[A, A2](self, in => Right(f(in)), output => Right(g(output)))

  final def transformOrFail[A2](f: A => Either[String, A2])(g: A2 => Either[String, A]): PathCodec[A2] =
    PathCodec.TransformOrFail[A, A2](self, f, g)

  final def transformOrFailLeft[A2](f: A => Either[String, A2])(g: A2 => A): PathCodec[A2] =
    PathCodec.TransformOrFail[A, A2](self, f, output => Right(g(output)))

  final def transformOrFailRight[A2](f: A => A2)(g: A2 => Either[String, A]): PathCodec[A2] =
    PathCodec.TransformOrFail[A, A2](self, in => Right(f(in)), g)
}
object PathCodec          {

  /**
   * Constructs a path codec from a method and a path literal.
   */
  def apply(value: String): PathCodec[Unit] = {
    val path = Path(value)

    (path.segments: @unchecked) match {
      case Chunk()                 => PathCodec.empty
      case Chunk(first, rest @ _*) =>
        rest.foldLeft[PathCodec[Unit]](Segment(SegmentCodec.literal(first))) { (pathSpec, segment) =>
          pathSpec / Segment(SegmentCodec.literal(segment))
        }
    }

  }

  def bool(name: String): PathCodec[Boolean] = Segment(SegmentCodec.bool(name))

  /**
   * The empty / root path codec.
   */
  def empty: PathCodec[Unit] = Segment[Unit](SegmentCodec.Empty)

  def int(name: String): PathCodec[Int] = Segment(SegmentCodec.int(name))

  def literal(value: String): PathCodec[Unit] = apply(value)

  def long(name: String): PathCodec[Long] = Segment(SegmentCodec.long(name))

  implicit def path(value: String): PathCodec[Unit] = apply(value)

  implicit def segment[A](codec: SegmentCodec[A]): PathCodec[A] = Segment(codec)

  def string(name: String): PathCodec[String] = Segment(SegmentCodec.string(name))

  def trailing: PathCodec[Path] = Segment(SegmentCodec.Trailing)

  def uuid(name: String): PathCodec[java.util.UUID] = Segment(SegmentCodec.uuid(name))

  private[http] final case class Fallback[A](left: PathCodec[Unit], right: PathCodec[Unit]) extends PathCodec[A]

  private[http] final case class Segment[A](segment: SegmentCodec[A]) extends PathCodec[A]

  private[http] final case class Concat[A, B, C](
    left: PathCodec[A],
    right: PathCodec[B],
    combiner: Combiner.WithOut[A, B, C],
  ) extends PathCodec[C]

  private[http] final case class TransformOrFail[X, A](
    api: PathCodec[X],
    f: X => Either[String, A],
    g: A => Either[String, X],
  ) extends PathCodec[A] {
    type In  = X
    type Out = A
  }

  final case class Annotated[A](codec: PathCodec[A], annotations: Chunk[MetaData[A]]) extends PathCodec[A] {

    override def equals(that: Any): Boolean =
      codec.equals(that)

  }

  sealed trait MetaData[A] extends Product with Serializable

  object MetaData {
    final case class Documented[A](value: Doc)             extends MetaData[A]
    final case class Examples[A](examples: Map[String, A]) extends MetaData[A]
  }

  private[http] val someUnit = Some(())

  /**
   * An optimized representation of the process of decoding a path and producing
   * a value. This is built for an evaluator that uses a stack.
   */
  private[http] sealed trait Opt
  private[http] object Opt {
    final case class Match(value: String)                     extends Opt
    final case class MatchAny(values: Set[String])            extends Opt
    final case class Combine(combiner: Combiner[_, _])        extends Opt
    case object IntOpt                                        extends Opt
    case object LongOpt                                       extends Opt
    case object StringOpt                                     extends Opt
    case object UUIDOpt                                       extends Opt
    case object BoolOpt                                       extends Opt
    case object TrailingOpt                                   extends Opt
    case object Unit                                          extends Opt
    final case class SubSegmentOpts(ops: Array[Opt])          extends Opt
    final case class MapOrFail(f: Any => Either[String, Any]) extends Opt
  }

  private[http] final case class SegmentSubtree[+A](
    literals: ListMap[String, SegmentSubtree[A]],
    others: ListMap[SegmentCodec[_], SegmentSubtree[A]],
    literalsRaceOthers: Set[String],
    value: Chunk[A],
  ) {
    self =>
    def ++[A1 >: A](that: SegmentSubtree[A1]): SegmentSubtree[A1] = {
      val newLiterals          = mergeMaps(self.literals, that.literals)(_ ++ _)
      val newOthers            = mergeMaps(self.others, that.others)(_ ++ _)
      val newLiteralRaceOthers = calculateLiteralRaceOthers(newLiterals.keySet, newOthers.keys)
      SegmentSubtree(
        newLiterals,
        newOthers,
        newLiteralRaceOthers,
        self.value ++ that.value,
      )
    }

    def add[A1 >: A](segments: Iterable[SegmentCodec[_]], value: A1): SegmentSubtree[A1] =
      self ++ SegmentSubtree.single(segments, value)

    def get(path: Path): Chunk[A] =
      get(path, 0)

    private def get(path: Path, from: Int, skipLiteralsFor: Set[Int] = Set.empty): Chunk[A] = {
      val segments  = path.segments
      val nSegments = segments.length
      var subtree   = self
      var result    = subtree.value
      var i         = from

      var trySkipLiteralIdx: Int = -1

      while (i < nSegments) {
        val segment = segments(i)

        // Fast path, jump down the tree:
        if (!skipLiteralsFor.contains(i) && subtree.literals.contains(segment)) {

          // this subtree segment have race with others
          // will try others if result was empty
          if (subtree.literalsRaceOthers.contains(segment)) {
            trySkipLiteralIdx = i
          }

          subtree = subtree.literals(segment)

          result = subtree.value
          i += 1
        } else {
          val flattened = subtree.othersFlat

          subtree = null
          flattened.length match {
            case 0 => // No predicates to evaluate
            case 1 => // Only 1 predicate to evaluate (most common)
              val (codec, subtree0) = flattened(0)
              val matched           = codec.matches(segments, i)
              if (matched > 0) {
                subtree = subtree0
                result = subtree0.value
                i += matched
              }
            case n => // Slowest fallback path. Have to to find the first predicate where the subpath returns a result
              val matches         = Array.ofDim[Int](n)
              var index           = 0
              var nPositive       = 0
              var lastPositiveIdx = -1
              while (index < n) {
                val (codec, _) = flattened(index)
                val n          = codec.matches(segments, i)
                if (n > 0) {
                  matches(index) = n
                  nPositive += 1
                  lastPositiveIdx = index
                }
                index += 1
              }

              nPositive match {
                case 0 => ()
                case 1 =>
                  subtree = flattened(lastPositiveIdx)._2
                  result = subtree.value
                  i += matches(lastPositiveIdx)
                case _ =>
                  index = 0
                  while (index < n && (subtree eq null)) {
                    val matched = matches(index)
                    if (matched > 0) {
                      val (_, subtree0) = flattened(index)
                      if (subtree0.get(path, i + matched).nonEmpty) {
                        subtree = subtree0
                        result = subtree.value
                        i += matched
                      }
                    }
                    index += 1
                  }
              }
          }

          if (subtree eq null) {
            result = Chunk.empty
            i = nSegments
          }
        }
      }

      // Might be some other matches because trailing matches everything:
      if (subtree ne null) {
        subtree.others.get(SegmentCodec.trailing) match {
          case Some(subtree) =>
            result = result ++ subtree.value
          case None          =>
        }
      }

      if ((trySkipLiteralIdx != -1) && result.isEmpty) {
        get(path, from, skipLiteralsFor + trySkipLiteralIdx)
      } else result
    }

    def map[B](f: A => B): SegmentSubtree[B] =
      SegmentSubtree(
        literals.map { case (k, v) => k -> v.map(f) },
        ListMap(others.toSeq.map { case (k, v) => k -> v.map(f) }: _*),
        literalsRaceOthers,
        value.map(f),
      )

    private var _othersFlat = null.asInstanceOf[Chunk[(SegmentCodec[_], SegmentSubtree[Any])]]

    private def othersFlat: Chunk[(SegmentCodec[_], SegmentSubtree[A])] = {
      if (_othersFlat eq null) _othersFlat = Chunk.fromIterable(others)
      _othersFlat.asInstanceOf[Chunk[(SegmentCodec[_], SegmentSubtree[A])]]
    }
  }
  object SegmentSubtree    {
    def single[A](segments: Iterable[SegmentCodec[_]], value: A): SegmentSubtree[A] =
      segments.collect { case x if x.nonEmpty => x }
        .foldRight[SegmentSubtree[A]](SegmentSubtree(ListMap(), ListMap(), Set.empty, Chunk(value))) {
          case (segment, subtree) =>
            val literals =
              segment match {
                case SegmentCodec.Literal(value) => ListMap(value -> subtree)
                case _                           => ListMap.empty[String, SegmentSubtree[A]]
              }

            val others =
              ListMap[SegmentCodec[_], SegmentSubtree[A]]((segment match {
                case SegmentCodec.Literal(_) => Chunk.empty
                case _                       => Chunk((segment, subtree))
              }): _*)

            SegmentSubtree(literals, others, Set.empty, Chunk.empty)
        }

    val empty: SegmentSubtree[Nothing] =
      SegmentSubtree(ListMap(), ListMap(), Set.empty, Chunk.empty)
  }

  private def mergeMaps[A, B](left: ListMap[A, B], right: ListMap[A, B])(f: (B, B) => B): ListMap[A, B] =
    right.foldLeft(left) { case (acc, (k, v)) =>
      acc.get(k) match {
        case None     => acc.updated(k, v)
        case Some(v0) => acc.updated(k, f(v0, v))
      }
    }

  private def calculateLiteralRaceOthers(literals: Set[String], others: Iterable[SegmentCodec[_]]): Set[String] = {
    literals.filter { literal =>
      others.exists { o =>
        o.inSegmentUntil(literal, 0) != -1
      }
    }
  }
}
