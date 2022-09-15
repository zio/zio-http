package zhttp.api.experiment

import zhttp.api.Parser
import zhttp.api.experiment.InputCodec.RouteType
import zhttp.http.Method
import zio._

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
  def get[Input](route: InputCodec[Input]): API[Input, Unit] =
    API(Method.GET, route)
}

// TODO:
// - move index to Atom itself
// - remove need for RouteParser Combine
//   - alternative: type member on InputCodec
//   - make / only accept Routes
sealed trait RouteParser[A]

object RouteParser {
  final case class Literal(value: String)           extends RouteParser[Unit]
  final case class ParseRoute[A](parser: Parser[A]) extends RouteParser[A]

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

  type CodecType

  def ++[Input2](that: InputCodec[Input2])(implicit combiner: Combiner[Input, Input2]): InputCodec[combiner.Out] =
    InputCodec.Combine(self, that, combiner)

  def map[Input2](f: Input => Input2): InputCodec[Input2] =
    InputCodec.Transform(self, f)

  def /[Input2](that: InputCodec[Input2])(implicit
    ev: IsRouteType[CodecType],
    ev2: IsRouteType[that.CodecType],
    combiner: Combiner[Input, Input2],
  ): InputCodec.WithType[CodecType, combiner.Out] =
    InputCodec
      .Combine(self, that, combiner.asInstanceOf[Combiner.WithOut[Input, Input2, combiner.Out]])
      .asInstanceOf[InputCodec.WithType[CodecType, combiner.Out]]
}

import scala.annotation.implicitNotFound

@implicitNotFound("${CodecType} is not a Route")
sealed trait IsRouteType[CodecType] extends (CodecType => InputCodec.RouteType)

object IsRouteType {

  implicit def isRouteType[Input](implicit
    ev: Input <:< InputCodec.RouteType,
  ): IsRouteType[Input] =
    new IsRouteType[Input] {
      def apply(input: Input): RouteType =
        ev(input)
    }
}

object InputCodec {
  sealed trait Atom[Input0] extends InputCodec[Input0]

  type WithType[CodecType0, Input] = InputCodec[Input] { type CodecType = CodecType0 }

  type EmptyType
  type RouteType
  type InputType
  type QueryType
  type HeaderType

  // InputCodec
  //   .get("users" / id)
  //   .input[User]
  //   .map { case (id, user) =>
  //     UserWithId(id, user)
  //   }
  //   .input("posts")
  // "users" / id / "posts"

  private[api] case object Empty extends Atom[Unit] { type CodecType = EmptyType }
  private[api] final case class Route[A](segmentParser: RouteParser[A])  extends Atom[A] { type CodecType = RouteType  }
  private[api] final case class InputBody[A](input: Schema[A])           extends Atom[A] { type CodecType = InputType  }
  private[api] final case class Query[A](queryParser: QueryParser[A])    extends Atom[A] { type CodecType = QueryType  }
  private[api] final case class Header[A](headerParser: HeaderParser[A]) extends Atom[A] { type CodecType = HeaderType }

  // - 0
  // - pre-index
  private final case class IndexedAtom[A](atom: Atom[A], index: Int)      extends Atom[A]
  private final case class Transform[X, A](api: InputCodec[X], f: X => A) extends InputCodec[A]

  private final case class Combine[A1, A2, B1, B2, A, B](
    left: InputCodec[A1],
    right: InputCodec[A2],
    inputCombiner: Combiner.WithOut[A1, A2, A],
  ) extends InputCodec[A]

  import zio.Chunk

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

  def literal(string: String): InputCodec.WithType[RouteType, Unit] =
    InputCodec.Route(RouteParser.literal(string))

  val int: InputCodec.WithType[RouteType, Int] =
    InputCodec.Route(RouteParser.int)

}

// Process for optimizing and zipping route atoms
//
// 1. extract Route Atoms from In
// 2. Zipper these Routes together (R0, R1, R2) [Any, Any, Any]
// 3. When one matches, we then will have Chunk[Any] that's just for the Route Atoms
// 4. Run the Header atoms and Query atoms and InputBody atom
// 5. Chunk[R0, R1, R2] Chunk[H0, H1] assemble the results in the original atom order
// 5. [R0, R1, H0, H1, R2]

final case class HandledAPI[-R, +E, In, Out](
  api: API[In, Out],
  handler: In => ZIO[R, E, Out],
)

final case class ZippableHandledAPI[-R, +E, Out](
  routeAtoms: Chunk[InputCodec.Route[_]],
  headerAtoms: Chunk[InputCodec.Header[_]],
  queryAtoms: Chunk[InputCodec.Query[_]],
  inputBodyAtom: Option[InputCodec.InputBody[_]],
  handler: Chunk[Any] => ZIO[R, E, Out],
)

object ZippableHandledAPI {
  def fromHandledAPI[R, E, Out](api: HandledAPI[R, E, _, Out]): ZippableHandledAPI[R, E, Out] = {
    val flattened                  = InputCodec.flatten(api.api.inputCodec)
    val routeAtoms                 = flattened.collect { case atom: InputCodec.Route[_] => atom }
    val headerAtoms                = flattened.collect { case atom: InputCodec.Header[_] => atom }
    val queryAtoms                 = flattened.collect { case atom: InputCodec.Query[_] => atom }
    val inputBodyAtom              = flattened.collectFirst { case atom: InputCodec.InputBody[_] => atom }
    def handler(chunk: Chunk[Any]) = ZIO.debug(s"Handling $chunk with API $api").asInstanceOf[ZIO[R, E, Out]]
    ZippableHandledAPI(routeAtoms, headerAtoms, queryAtoms, inputBodyAtom, handler)
  }

}

object ZippedHandledAPIs {
  final case class Handle[R, E](
    handler: Chunk[Any] => ZIO[R, E, Any],
  ) extends ZippedHandledAPIs[R, E]

  final case class Alternatives[R, E](
    // TODO: Maybe make specialized Option. None signifies Root Route
    alternatives: Map[Option[InputCodec.Route[_]], ZippedHandledAPIs[R, E]],
  ) extends ZippedHandledAPIs[R, E]

  def fromZippableHandledAPI[R, E](api: ZippableHandledAPI[R, E, _]): ZippedHandledAPIs[R, E] =
    api.routeAtoms.init.foldRight[ZippedHandledAPIs[R, E]](
      Alternatives(
        Map(
          Some(api.routeAtoms.last) -> Handle(chunk =>
            ZIO.debug(s"Handling $chunk with API $api").asInstanceOf[ZIO[R, E, Any]],
          ),
        ),
      ),
    ) { case (route, acc) => Alternatives(Map(Some(route) -> acc)) }

}

// TODO: If we want, further "compress"/"optimize" these maps into sets of chunks
sealed trait ZippedHandledAPIs[-R, +E] { self =>
  import ZippedHandledAPIs._

  // simplified model
  def parse(route: List[String], acc: Chunk[Any] = Chunk.empty): Option[ZIO[R, E, Any]] =
    route match {
      case Nil =>
        self match {
          case Handle(handler)            =>
            Some(handler(acc))
          case Alternatives(alternatives) =>
            alternatives.get(None) match {
              case Some(Handle(handler)) =>
                Some(handler(acc))
              case _                     => None
            }
        }

      case head :: tail =>
        self match {
          case Handle(_)                  => None
          case Alternatives(alternatives) =>
            alternatives.get(Some(InputCodec.Route(RouteParser.literal(head)))) match {
              case Some(route) =>
                route.parse(tail, acc ++ Chunk(head))
              case None        =>
                // TODO
                None
            }
        }
    }

  // combine two apis, sharing the same prefix
  def merge[R1 <: R, E1 >: E](that: ZippedHandledAPIs[R1, E1]): ZippedHandledAPIs[R1, E1] = {
    (self.asInstanceOf[ZippedHandledAPIs[_, _]], that.asInstanceOf[ZippedHandledAPIs[_, _]]) match {
      case (Alternatives(map1), Alternatives(map2)) =>
        Alternatives(mergeWith(map1, map2)(_ merge _))
          .asInstanceOf[ZippedHandledAPIs[R1, E1]]

      case (Alternatives(map1), Handle(handler2)) =>
        Alternatives(mergeWith(map1, Map(Option.empty[InputCodec.Route[_]] -> Handle(handler2)))(_ merge _))
          .asInstanceOf[ZippedHandledAPIs[R1, E1]]

      case (Handle(handler1), Alternatives(map2)) =>
        Alternatives(mergeWith(Map(Option.empty[InputCodec.Route[_]] -> Handle(handler1)), map2)(_ merge _))
          .asInstanceOf[ZippedHandledAPIs[R1, E1]]
      //

      case (_, right) =>
        right.asInstanceOf[ZippedHandledAPIs[R1, E1]] // TODO: Throw exception
    }
  }

  def mergeWith[K, V](left: Map[K, V], right: Map[K, V])(f: (V, V) => V): Map[K, V] =
    left.foldLeft(right) { case (acc, (k, v)) =>
      acc.get(k) match {
        case Some(v2) => acc.updated(k, f(v, v2))
        case None     => acc.updated(k, v)
      }
    }

  def indent(string: String, amount: Int): String =
    string.split("\n").map(s => " " * amount + s).mkString("\n")

  def render: String =
    self match {
      case Alternatives(map) =>
        map.map { case (k, v) => indent(s"$k -> ${v.render}", 2) }.mkString("Alternatives(\n", ",\n", "\n)")

      case Handle(f) => s"Handle($f)"
    }

  // def longestCommonPrefix[A](left: Chunk[A], right: Chunk[A]): Chunk[A] = {
  //   val leftIterator  = left.chunkIterator
  //   val rightIterator = right.chunkIterator
  //   val builder       = ChunkBuilder.make[A]()
  //   var index         = 0
  //   var loop          = true
  //   while (loop && leftIterator.hasNextAt(index) && rightIterator.hasNextAt(index)) {
  //     val leftValue  = leftIterator.nextAt(index)
  //     val rightValue = rightIterator.nextAt(index)
  //     index += 1
  //     if (leftValue == rightValue) builder += leftValue
  //     else loop = false
  //   }
  //   builder.result()
  // }
}

// Alternatives(
//   Chunk(Route(Literal(users)),Route(int)),
//   Chunk(
//     Alternatives(
//       Chunk(Route(Literal(posts)),Route(int)),
//       Chunk(
//         Handle(zhttp.api.experiment.ZippedHandledAPIs$$$Lambda$12/0x00000008000b3840@7d68ef40)
//       )
//    ),
//    Handle(zhttp.api.experiment.ZippedHandledAPIs$$$Lambda$12/0x00000008000b3840@5b0abc94))
// )

object CommonPrefixTesting extends App {
  import InputCodec._
  val api1 = API.get(literal("users") / int / literal("posts") / int)
  val api2 = API.get(literal("users") / int)
  val api3 = API.get(literal("users") / int / literal("posts"))

  val handled1 = HandledAPI(api1, (r: Any) => ZIO.debug(s"RESULT 1: $r"))
  val handled2 = HandledAPI(api2, (r: Any) => ZIO.debug(s"RESULT 2: $r"))
  val handled3 = HandledAPI(api3, (r: Any) => ZIO.debug(s"RESULT 3: $r"))

  val zippable1 = ZippableHandledAPI.fromHandledAPI(handled1)
  val zippable2 = ZippableHandledAPI.fromHandledAPI(handled2)
  val zippable3 = ZippableHandledAPI.fromHandledAPI(handled3)

  val combined =
    ZippedHandledAPIs.fromZippableHandledAPI(zippable1) merge
      ZippedHandledAPIs.fromZippableHandledAPI(zippable2) merge
      ZippedHandledAPIs.fromZippableHandledAPI(zippable3)

  println(combined.render)

}

// we've grouped the different atom types
// with the information we need to recombine their results

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

  val result = Chunk((), 500, (), 900)

  val constructor = thread(sample)
  val threaded    = constructor(result)

  println(threaded)
}

object Example3 extends App {

  import zio.Chunk
  import InputCodec._

  val sample = (literal("users") ++ int.map(n => ("COOL", n * 2)) ++ literal("posts") ++ int).map(n => ("Adam", n))

  val result = Chunk((), 500, (), 900)

  val constructor = thread(sample)
  val threaded    = constructor(result)

  println(threaded)
}

object Example4 extends App {

  import zio.Chunk
  import InputCodec._

  val sample = literal("users") / int / literal("posts") / int // / InputCodec.Header(HeaderParser.header("AUTH"))

  val result = Chunk((), 500, (), 900, "some-auth-token")

  val constructor = thread(sample)
  val threaded    = constructor(result)

  println(threaded)
}

// object Example4 extends App {

//   import zio.Chunk
//   import InputCodec._

//   val x = literal("users") / int / literal("posts") / int // / InputCodec.Header(HeaderParser.header("origin"))

//   import InputCodec._

// API, InputCodec
// QueryParser, HeaderParser, InputCodec, API, ....

// val tapir =
//   endpoint
//     .in("user" / param[Int] / "posts" / param[UUID])
//     .in(query[String]("hello") ++ query[String]("hello"))
//     .in(header[String]("hello") ++ header[String]("hello"))

// val oauthCode: Header[Code] = header[String]("Authorization").transform(Code(_), _.value)
// def oauth(name: String) = header(name).transform(_ => ???, _ => ???)
// val oauth = header("path").transform(_ => ???, _ => ???)

// val sample = API
//   .post("user" / int / "posts" / uuid)
//   .in(header("lifespan") zip header("name") zip query("hello"))
//   .inputCodec

// val sample2 =
//   route("user") ++ int ++ route("posts") ++ header("lifespan") ++ header("name") ++ query("hello") ++ uuid ++ get

// val sample = API
//   .get("user" / int / "posts" / uuid)
//   .in(
//     header("lifespan").int ++ header("name") ++ query("hello").asUUID
//   )
//   .inputCodec

// val flattened: Chunk[Atom[_]] = flatten(sample)
// println(flattened)

// val result = Chunk((), 500, (), 900, "some-auth-token")

// val constructor = thread(sample)
// val threaded    = constructor(result)

// println(threaded)
// }
