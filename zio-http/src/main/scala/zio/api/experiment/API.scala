package zhttp.api.experiment

import zhttp.api.Parser
import zhttp.http.Method
import zio.Chunk

import java.util.UUID
import scala.language.implicitConversions

// TODO: Index Atom
trait Schema[A]

final case class API[Input, Output](
  method: Method,
  inputCodec: InputCodec[Input],
//  outputCodec: OutputCodec[Input],
) {
  def header[A](headerParser: HeaderParser[A])(implicit combiner: Combiner[Input, A]): API[combiner.Out, Output] =
    copy(inputCodec = inputCodec ++ InputCodec.Header(headerParser))
}

object API {
  def get[Input](route: RouteParser[Input]): API[Input, Unit] =
    API(Method.GET, InputCodec.liftRoute(route))
}

// TODO:
// - move index to Atom itself
// - remove need for RouteParser Combine
//   - alternative: type member on InputCodec
//   - make / only accept Routes
sealed trait RouteParser[A] { self =>
  def /[B](that: RouteParser[B])(implicit combiner: Combiner[A, B]): RouteParser[combiner.Out] =
    RouteParser.Combine(self, that, combiner)
}

object RouteParser {
  final case class Literal(value: String)           extends RouteParser[Unit]
  final case class ParseRoute[A](parser: Parser[A]) extends RouteParser[A]
  final case class Combine[A, B, C](
    lhs: RouteParser[A],
    rhs: RouteParser[B],
    combiner: Combiner.WithOut[A, B, C],
  ) extends RouteParser[C]

  implicit def literal(value: String): RouteParser[Unit] = Literal(value)
  val int: RouteParser[Int]                              = ParseRoute(Parser.intParser)
  val boolean: RouteParser[Boolean]                      = ParseRoute(Parser.booleanParser)
  val uuid: RouteParser[UUID]                            = ParseRoute(Parser.uuidParser)
//  def compile[A](index: Int, segmentParser: RouteParser[A]): Array[AnyRef] => Unit =
//    ???
}

sealed trait QueryParser[A]

object QueryParser {}

sealed trait HeaderParser[A]

object HeaderParser {
  final case class Header(name: String)                            extends HeaderParser[String]
  final case class ParseHeader[A](name: String, parser: Parser[A]) extends HeaderParser[A]
  def header(name: String): HeaderParser[String] = Header(name)
}

sealed trait InputCodec[Input] {
  self =>

  def ++[Input2](that: InputCodec[Input2])(implicit combiner: Combiner[Input, Input2]): InputCodec[combiner.Out] =
    InputCodec.Combine(self, that, combiner)

  def map[Input2](f: Input => Input2): InputCodec[Input2] =
    InputCodec.Transform(self, f)
}

object InputCodec {
  sealed trait Atom[Input0] extends InputCodec[Input0]

  // InputCodec
  //   .get("users" / id)
  //   .input[User]
  //   .map { case (id, user) =>
  //     UserWithId(id, user)
  //   }
  //   .input("posts")
  // "users" / id / "posts"

  case object Empty                                         extends Atom[Unit]
  final case class Route[A](segmentParser: RouteParser[A])  extends Atom[A]
  final case class InputBody[A](input: Schema[A])           extends Atom[A]
  final case class Query[A](queryParser: QueryParser[A])    extends Atom[A]
  final case class Header[A](headerParser: HeaderParser[A]) extends Atom[A]

  // - 0
  // - pre-index
  final case class IndexedAtom[A](atom: Atom[A], index: Int)      extends Atom[A]
  final case class Transform[X, A](api: InputCodec[X], f: X => A) extends InputCodec[A]

  final case class Combine[A1, A2, B1, B2, A, B](
    left: InputCodec[A1],
    right: InputCodec[A2],
    inputCombiner: Combiner.WithOut[A1, A2, A],
  ) extends InputCodec[A]

  import zio.Chunk

  def liftRoute[A](route: RouteParser[A]): InputCodec[A] = route match {
    case literal: RouteParser.Literal            => InputCodec.Route(literal)
    case parse: RouteParser.ParseRoute[A]        => InputCodec.Route(parse)
    case RouteParser.Combine(lhs, rhs, combiner) => InputCodec.Combine(liftRoute(lhs), liftRoute(rhs), combiner)
  }

  def flatten(api: InputCodec[_]): Chunk[Atom[_]] =
    api match {
      case Combine(left, right, _) => flatten(left) ++ flatten(right)
      case atom: Atom[_]           => Chunk(atom)
      case map: Transform[_, _]    => flatten(map.api)
    }

  type Constructor[+A]   = Chunk[Any] => A
  type Deconstructor[-A] = A => Chunk[Any]

  private def indexed[A](api: InputCodec[A]): InputCodec[A] =
    indexedImpl(api, 0)._1

  private def indexedImpl[A](api: InputCodec[A], start: Int): (InputCodec[A], Int) =
    api.asInstanceOf[InputCodec[_]] match {
      case Combine(left, right, inputCombiner) =>
        val (left2, leftEnd)   = indexedImpl(left, start)
        val (right2, rightEnd) = indexedImpl(right, leftEnd)
        (Combine(left2, right2, inputCombiner).asInstanceOf[InputCodec[A]], rightEnd)
      case atom: Atom[_]                       =>
        (IndexedAtom(atom, start).asInstanceOf[InputCodec[A]], start + 1)
      case Transform(api, f)                   =>
        val (api2, end) = indexedImpl(api, start)
        (Transform(api2, f).asInstanceOf[InputCodec[A]], end)
    }

  def thread[A](api: InputCodec[A]): Constructor[A] =
    threadIndexed(indexed(api))

  private def threadIndexed[A](api: InputCodec[A]): Constructor[A] = {
    def coerce(any: Any): A = any.asInstanceOf[A]

    api match {
      case Combine(left, right, inputCombiner) =>
        val leftThread  = threadIndexed(left)
        val rightThread = threadIndexed(right)

        chunk => {
          val leftValue  = leftThread(chunk)
          val rightValue = rightThread(chunk)

          inputCombiner.combine(leftValue, rightValue)
        }

      case indexedAtom: IndexedAtom[_] =>
        chunk => coerce(chunk(indexedAtom.index))

      case transform: Transform[_, A] =>
        val threaded = threadIndexed(transform.api)
        chunk => transform.f(threaded(chunk))

      case atom: Atom[_] =>
        throw new RuntimeException(s"Atom $atom should have been wrapped in IndexedAtom")
    }
  }

  // # Constructors

  def literal(string: String): InputCodec[Unit] =
    InputCodec.Route(RouteParser.literal(string))

  val int: InputCodec[Int] =
    InputCodec.Route(RouteParser.int)

}

sealed trait Combiner[L, R] {
  type Out

  def combine(l: L, r: R): Out

  def separate(out: Out): (L, R)
}

object Combiner extends CombinerLowPriority1 {
  type WithOut[L, R, Out0] = Combiner[L, R] { type Out = Out0 }

  implicit def leftUnit[A]: Combiner.WithOut[Unit, A, A] =
    new Combiner[Unit, A] {
      type Out = A

      def combine(l: Unit, r: A): A = r

      def separate(out: A): (Unit, A) = ((), out)
    }
}

trait CombinerLowPriority1 extends CombinerLowPriority2 {
  implicit def rightUnit[A]: Combiner.WithOut[A, Unit, A] =
    new Combiner[A, Unit] {
      type Out = A

      def combine(l: A, r: Unit): A = l

      def separate(out: A): (A, Unit) = (out, ())
    }
}

trait CombinerLowPriority2 extends CombinerLowPriority3 {
  // (A, B) + C -> (A, B, C)
  implicit def combine2[A, B, C]: Combiner.WithOut[(A, B), C, (A, B, C)] =
    new Combiner[(A, B), C] {
      type Out = (A, B, C)

      def combine(l: (A, B), r: C): (A, B, C) = (l._1, l._2, r)

      def separate(out: (A, B, C)): ((A, B), C) =
        ((out._1, out._2), out._3)
    }
}

trait CombinerLowPriority3 {

  implicit def combine[A, B]: Combiner.WithOut[A, B, (A, B)] =
    new Combiner[A, B] {
      type Out = (A, B)

      def combine(l: A, r: B): (A, B) = (l, r)

      def separate(out: (A, B)): (A, B) = (out._1, out._2)
    }
}

object Example extends App {

  import InputCodec._

  val sample: InputCodec[(Int, Int, Int)] =
    literal("users") ++ int ++ literal("posts") ++ int ++ literal("comments") ++ int
  val result                              = Chunk((), 100, (), 500, (), 900)
  val constructor                         = thread(sample)
  val threaded                            = constructor(result)

  println(threaded)
}

object Example2 extends App {

  import zio.Chunk
  import InputCodec._

  val sample: InputCodec[(String, Int, Int)] =
    literal("users") ++ int.map(n => ("Adam", n * 2)) ++ literal("posts") ++ int
  val flattened: Chunk[Atom[_]]              = flatten(sample)
  println(flattened)

  val result = Chunk((), 500, (), 900)

  val constructor = thread(sample)
  val threaded    = constructor(result)

  println(threaded)
}

object Example3 extends App {

  import zio.Chunk
  import InputCodec._

  val sample = (literal("users") ++ int.map(n => ("COOL", n * 2)) ++ literal("posts") ++ int).map(n => ("Adam", n))
  val flattened: Chunk[Atom[_]] = flatten(sample)
  println(flattened)

  val result = Chunk((), 500, (), 900)

  val constructor = thread(sample)
  val threaded    = constructor(result)

  println(threaded)
}

object Example4 extends App {

  import zio.Chunk
  import InputCodec._
  import RouteParser.literal

  val sample = API
    .get("users" / RouteParser.int / "posts" / RouteParser.int)
    .header(HeaderParser.header("AUTH"))
    .inputCodec

  val flattened: Chunk[Atom[_]] = flatten(sample)
  println(flattened)

  val result = Chunk((), 500, (), 900, "some-auth-token")

  val constructor = thread(sample)
  val threaded    = constructor(result)

  println(threaded)
}
