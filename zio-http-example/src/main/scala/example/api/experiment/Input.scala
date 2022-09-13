package example.api.experiment

sealed trait Input[A] {}

object Input {
  final case class Path[A](a: String => Option[A])         extends Input[A]
  final case class Header[A](a: String => Option[A])       extends Input[A]
  final case class Zip[A, B](lhs: Input[A], rhs: Input[B]) extends Input[(A, B)]

  def parse[A](string: String, path: Input[A]): Option[A] = ???
}

trait Zipper[A, B] {
  type Out

  def apply(a: A, b: B): Out
}

object Zipper extends LowPriorityZipper {

//  type Aux[A, B, Out0] = Zipper[A, B] { type Out = Out0 }
  // Unit + A = A
  implicit def unitLeft[A] = new Zipper[Unit, A] {
    type Out = A

    def apply(a: Unit, b: A): A = b
  }

  // A + Unit = A
  implicit def unitRight[A] = new Zipper[A, Unit] {
    type Out = A

    def apply(a: A, b: Unit): A = a
  }

}

trait LowPriorityZipper extends LowPriorityZipper2 {
  // (A, B) + C = (A, B, C)

  implicit def tupleLeft[A, B, C] = new Zipper[(A, B), C] {
    type Out = (A, B, C)

    def apply(a: (A, B), b: C): (A, B, C) = (a._1, a._2, b)
  }
}

trait LowPriorityZipper2 {
  // A + B = (A, B)
  implicit def tuple[A, B] = new Zipper[A, B] {
    type Out = (A, B)

    def apply(a: A, b: B): (A, B) = (a, b)
  }

}

object Path {

  case object Literal                                                       extends Path[Unit]
  final case class Parse[A](parse: String => Option[A], index: Int)         extends Path[A]
  final case class Zip[A, B, C](lhs: Path[A], rhs: Path[B], f: (A, B) => C) extends Path[C]

  def literal: Path[Unit] = Literal
  def int: Path[Int]      = Parse(_.toIntOption, 0)
  def bool: Path[Boolean] = Parse(_.toBooleanOption, 0)
}

final case class ParseState(var remaining: List[String], var result: Array[Any])

sealed trait Path[A] extends Product with Serializable { self =>
  def parseImpl(parseState: ParseState): Unit =
    self.asInstanceOf[Path[_]] match {
      case Path.Literal             =>
        parseState.remaining match {
          case _ :: tail => parseState.remaining = tail
          case Nil       => throw new Exception(s"Not enough arguments! State: ${parseState}")
        }
      case Path.Parse(parse, index) =>
        parseState.remaining match {
          case head :: tail =>
            parse(head) match {
              case Some(value) =>
                parseState.result(index) = value
                parseState.remaining = tail
              case None        =>
                throw new Exception(s"Could not parse $head! State: ${parseState}")
            }
          case Nil          =>
            throw new Exception(s"Not enough arguments! State: ${parseState}")
        }
      case Path.Zip(lhs, rhs, _)    =>
        lhs.parseImpl(parseState)
        rhs.parseImpl(parseState)
    }

  def highestIndex: Option[Int] =
    self.asInstanceOf[Path[_]] match {
      case Path.Literal          => None
      case Path.Parse(_, index)  => Some(index)
      case Path.Zip(lhs, rhs, _) => rhs.highestIndex orElse lhs.highestIndex
    }

  def zip[B](that: Path[B])(implicit zipper: Zipper[A, B]): Path[zipper.Out] =
    Path.Zip[A, B, zipper.Out](
      self,
      that.withIndex(self.highestIndex.getOrElse(-1) + 1),
      zipper.apply(_, _),
    )

  def withIndex(index: Int): Path[A] =
    self.asInstanceOf[Path[_]] match {
      case Path.Literal          => Path.Literal.asInstanceOf[Path[A]]
      case Path.Parse(parse, _)  => Path.Parse(parse, index).asInstanceOf[Path[A]]
      case Path.Zip(lhs, rhs, f) =>
        val newLeft = lhs.withIndex(index)
        Path.Zip(newLeft, rhs.withIndex(newLeft.highestIndex.getOrElse(-1) + 1), f).asInstanceOf[Path[A]]
    }
}

object PathDemo extends App {
  val literal = Path.literal
  val int     = Path.int
  val bool    = Path.bool

  val zipped = literal.zip(int).zip(bool).zip(literal).zip(bool)

  println(zipped.highestIndex)

  val parseState =
    ParseState(List("hello", "1", "true", "nice", "false"), Array.ofDim[Any](zipped.highestIndex.getOrElse(0) + 1))
  zipped.parseImpl(parseState)
  println(parseState.result.toList)
}

final case class PathParserOpt[A](f: String => Option[A], ignore: Boolean)

final case class PathParserState()

//final case class PathParsersOpt(parsers: Array[PathParserOpt[_]]) {
//  def parse
//}
