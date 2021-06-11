package zio.web.internal

/**
 * Type class for appending/prepending values into tuples.
 */
sealed trait Combine[A, B] {
  type Left  = A
  type Right = B
  type Out

  val pack: (Left, Right) => Out
  val unpack: Out => (Left, Right)
}

object Combine extends Combine4 {
  type Aux[A, B, Out0] = Combine[A, B] { type Out = Out0 }
}

sealed trait Combine0 {
  implicit def base[A, B]: Combine.Aux[A, B, (A, B)] =
    new Combine[A, B] {
      type Out = (A, B)
      val pack   = (left: A, right: B) => (left, right)
      val unpack = (out: (A, B)) => (out._1, out._2)
    }
}

sealed trait Combine1 extends Combine0 {
  implicit def right2tuple[A, B, C]: Combine.Aux[A, (B, C), (A, B, C)] =
    new Combine[A, (B, C)] {
      type Out = (A, B, C)
      val pack   = (left, right) => (left, right._1, right._2)
      val unpack = out => (out._1, (out._2, out._3))
    }

  implicit def left2tuple[A, B, C]: Combine.Aux[(A, B), C, (A, B, C)] =
    new Combine[(A, B), C] {
      type Out = (A, B, C)
      val pack   = (left, right) => (left._1, left._2, right)
      val unpack = out => ((out._1, out._2), out._3)
    }
}

sealed trait Combine2 extends Combine1 {
  implicit def right3tuple[A, B, C, D]: Combine.Aux[A, (B, C, D), (A, B, C, D)] =
    new Combine[A, (B, C, D)] {
      type Out = (A, B, C, D)
      val pack   = (left, right) => (left, right._1, right._2, right._3)
      val unpack = out => (out._1, (out._2, out._3, out._4))
    }

  implicit def left3tuple[A, B, C, D]: Combine.Aux[(A, B, C), D, (A, B, C, D)] =
    new Combine[(A, B, C), D] {
      type Out = (A, B, C, D)
      val pack   = (left, right) => (left._1, left._2, left._3, right)
      val unpack = out => ((out._1, out._2, out._3), out._4)
    }
}

sealed trait Combine3 extends Combine2 {
  implicit def leftUnit[A]: Combine.Aux[Unit, A, A] =
    new Combine[Unit, A] {
      type Out = A
      val pack   = (_, right) => right
      val unpack = out => ((), out)
    }
}

sealed trait Combine4 extends Combine3 {
  implicit def rightUnit[A]: Combine.Aux[A, Unit, A] =
    new Combine[A, Unit] {
      type Out = A
      val pack   = (left, _) => left
      val unpack = out => (out, ())
    }
}
