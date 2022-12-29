package zio.http.middleware

sealed trait IT[+A]

object IT {
  final case class Id[+A]() extends IT[A]

  final case class Contramap[+AIn, -AOut](f: AOut => AIn) extends IT[AIn]

  final case class Impossible[+A]() extends IT[A]

  trait CanApplyToPartial {
    type AIn
    type AOut
    type T <: IT[AIn]
    def transform(t: T, a: AOut): AIn
  }

  object CanApplyToPartial {
    type Aux[AInP, AOutP, TP <: IT[AInP]] = CanApplyToPartial {
      type AIn  = AInP
      type AOut = AOutP
      type T    = TP
    }
    implicit def idCanBeAppliedToPartial[A]: CanApplyToPartial.Aux[A, A, Id[A]] =
      new CanApplyToPartial {
        type AIn  = A
        type AOut = A
        type T    = Id[A]
        override def transform(t: Id[A], a: A): A = a
      }

    implicit def contramapCanBeAppliedToPartial[AInP, AOutP]
      : CanApplyToPartial.Aux[AInP, AOutP, Contramap[AInP, AOutP]] =
      new CanApplyToPartial {
        type AIn  = AInP
        type AOut = AOutP
        type T    = Contramap[AIn, AOut]
        override def transform(t: Contramap[AIn, AOut], a: AOut): AIn = t.f(a)
      }
  }
}

trait ITAndThen {
  type IT1 <: IT[_]
  type IT2 <: IT[_]
  type ITR <: IT[_]

  def andThen(it1: IT1, it2: IT2): ITR
}

object ITAndThen {

  import IT.{Id, Contramap, Impossible}

  type Aux[IT1P <: IT[_], IT2P <: IT[_], ITRP <: IT[_]] =
    ITAndThen { type IT1 = IT1P; type IT2 = IT2P; type ITR = ITRP }

  implicit def idAndThenId[AIn1, AIn2]: ITAndThen.Aux[Id[AIn1], Id[AIn2], Id[AIn2]] = new ITAndThen {
    override type IT1 = Id[AIn1]
    override type IT2 = Id[AIn2]
    override type ITR = Id[AIn2]

    override def andThen(it1: IT1, it2: IT2): ITR = IT.Id()
  }

  implicit def idAndThenContraMap[AIn1, AIn, AOut]
    : ITAndThen.Aux[Id[AIn1], Contramap[AIn, AOut], Contramap[AIn, AOut]] =
    new ITAndThen {
      override type IT1 = Id[AIn1]
      override type IT2 = Contramap[AIn, AOut]
      override type ITR = Contramap[AIn, AOut]

      override def andThen(it1: IT1, it2: IT2): ITR = it2
    }

  implicit def idAndThenImpossible[AIn1, AIn2]: ITAndThen.Aux[Id[AIn1], Impossible[AIn2], Impossible[AIn2]] =
    new ITAndThen {
      override type IT1 = Id[AIn1]
      override type IT2 = Impossible[AIn2]
      override type ITR = Impossible[AIn2]

      override def andThen(it1: IT1, it2: IT2): ITR = IT.Impossible()
    }

  implicit def contraMapAndThenId[AIn, AOut, AIn2]
    : ITAndThen.Aux[Contramap[AIn, AOut], Id[AIn2], Contramap[AIn, AOut]] =
    new ITAndThen {
      override type IT1 = Contramap[AIn, AOut]
      override type IT2 = Id[AIn2]
      override type ITR = Contramap[AIn, AOut]

      override def andThen(it1: IT1, it2: IT2): ITR = it1
    }

  implicit def contraMapAndThenContraMap[AIn1, AOut1, AIn2]
    : ITAndThen.Aux[Contramap[AIn1, AOut1], Contramap[AIn2, AIn1], Contramap[AIn2, AOut1]] =
    new ITAndThen {
      override type IT1 = Contramap[AIn1, AOut1]
      override type IT2 = Contramap[AIn2, AIn1]
      override type ITR = Contramap[AIn2, AOut1]

      override def andThen(it1: IT1, it2: IT2): ITR = Contramap(it1.f andThen it2.f)
    }

  implicit def contraMapAndThenImpossible[AIn, AOut, AIn2]
    : ITAndThen.Aux[Contramap[AIn, AOut], Impossible[AIn2], Impossible[AIn2]] =
    new ITAndThen {
      override type IT1 = Contramap[AIn, AOut]
      override type IT2 = Impossible[AIn2]
      override type ITR = Impossible[AIn2]

      override def andThen(it1: IT1, it2: IT2): ITR = IT.Impossible()
    }

  implicit def impossibleAndThenId[AIn1, AIn2]: ITAndThen.Aux[Impossible[AIn1], Id[AIn2], Impossible[AIn2]] =
    new ITAndThen {
      override type IT1 = Impossible[AIn1]
      override type IT2 = Id[AIn2]
      override type ITR = Impossible[AIn2]

      override def andThen(it1: IT1, it2: IT2): ITR = IT.Impossible()
    }

  implicit def impossibleAndThenContraMap[AIn1, AIn, AOut]
    : ITAndThen.Aux[Impossible[AIn1], Contramap[AIn, AOut], Impossible[AIn]] =
    new ITAndThen {
      override type IT1 = Impossible[AIn1]
      override type IT2 = Contramap[AIn, AOut]
      override type ITR = Impossible[AIn]

      override def andThen(it1: IT1, it2: IT2): ITR = IT.Impossible()
    }

  implicit def impossibleAndThenImpossible[AIn1, AIn2]
    : ITAndThen.Aux[Impossible[AIn1], Impossible[AIn2], Impossible[AIn2]] =
    new ITAndThen {
      override type IT1 = Impossible[AIn1]
      override type IT2 = Impossible[AIn2]
      override type ITR = Impossible[AIn2]

      override def andThen(it1: IT1, it2: IT2): ITR = IT.Impossible()
    }
}

trait ITOrElse {
  type IT1 <: IT[_]
  type IT2 <: IT[_]
  type ITR <: IT[_]

  def orElse(it1: IT1, it2: IT2): ITR
}

object ITOrElse {

  import IT._

  type Aux[IT1P <: IT[_], IT2P <: IT[_], ITRP <: IT[_]] =
    ITOrElse { type IT1 = IT1P; type IT2 = IT2P; type ITR = ITRP }

  implicit def idOrElseId[AIn1, AIn2 <: AIn1]: ITOrElse.Aux[Id[AIn1], Id[AIn2], Id[AIn2]] = new ITOrElse {
    override type IT1 = Id[AIn1]
    override type IT2 = Id[AIn2]
    override type ITR = Id[AIn2]

    override def orElse(it1: IT1, it2: IT2): ITR = Id()
  }

  implicit def idOrElseContramap[AIn1, AIn, AOut]: ITOrElse.Aux[Id[AIn1], Contramap[AIn, AOut], Impossible[AIn]] =
    new ITOrElse {
      override type IT1 = Id[AIn1]
      override type IT2 = Contramap[AIn, AOut]
      override type ITR = Impossible[AIn]

      override def orElse(it1: IT1, it2: IT2): ITR = Impossible()
    }

  implicit def idOrElseImpossible[AIn1, AIn2]: ITOrElse.Aux[Id[AIn1], Impossible[AIn2], Impossible[AIn2]] =
    new ITOrElse {
      override type IT1 = Id[AIn1]
      override type IT2 = Impossible[AIn2]
      override type ITR = Impossible[AIn2]

      override def orElse(it1: IT1, it2: IT2): ITR = Impossible()
    }

  implicit def contramapOrElseId[AIn, AOut, AIn2]: ITOrElse.Aux[Contramap[AIn, AOut], Id[AIn2], Impossible[AIn2]] =
    new ITOrElse {
      override type IT1 = Contramap[AIn, AOut]
      override type IT2 = Id[AIn2]
      override type ITR = Impossible[AIn2]

      override def orElse(it1: IT1, it2: IT2): ITR = Impossible()
    }

  implicit def contramapOrElseContramap[AIn1, AOut1, AIn2, AOut2]
    : ITOrElse.Aux[Contramap[AIn1, AOut1], Contramap[AIn2, AOut2], Impossible[AIn2]] =
    new ITOrElse {
      override type IT1 = Contramap[AIn1, AOut1]
      override type IT2 = Contramap[AIn2, AOut2]
      override type ITR = Impossible[AIn2]

      override def orElse(it1: IT1, it2: IT2): ITR = Impossible()
    }

  implicit def contramapOrElseImpossible[AIn, AOut, AIn2]
    : ITOrElse.Aux[Contramap[AIn, AOut], Impossible[AIn2], Impossible[AIn2]] =
    new ITOrElse {
      override type IT1 = Contramap[AIn, AOut]
      override type IT2 = Impossible[AIn2]
      override type ITR = Impossible[AIn2]

      override def orElse(it1: IT1, it2: IT2): ITR = Impossible()
    }

  implicit def impossibleOrElseId[AIn1, AIn2]: ITOrElse.Aux[Impossible[AIn1], Id[AIn2], Impossible[AIn2]] =
    new ITOrElse {
      override type IT1 = Impossible[AIn1]
      override type IT2 = Id[AIn2]
      override type ITR = Impossible[AIn2]

      override def orElse(it1: IT1, it2: IT2): ITR = Impossible()
    }

  implicit def impossibleOrElseContraMap[AIn1, AIn, AOut]
    : ITOrElse.Aux[Impossible[AIn1], Contramap[AIn, AOut], Impossible[AIn]] =
    new ITOrElse {
      override type IT1 = Impossible[AIn1]
      override type IT2 = Contramap[AIn, AOut]
      override type ITR = Impossible[AIn]

      override def orElse(it1: IT1, it2: IT2): ITR = Impossible()
    }

  implicit def impossibleOrElseImpossible[AIn1, AIn2]
    : ITOrElse.Aux[Impossible[AIn1], Impossible[AIn2], Impossible[AIn2]] =
    new ITOrElse {
      override type IT1 = Impossible[AIn1]
      override type IT2 = Impossible[AIn2]
      override type ITR = Impossible[AIn2]

      override def orElse(it1: IT1, it2: IT2): ITR = Impossible()
    }
}

trait ITIfThenElse[AIn, AOut, AIn1 <: AIn, AIn2 <: AIn] {
  type IT1 <: IT[AIn1]
  type IT2 <: IT[AIn2]
  type ITR <: IT[AIn]

  def ifThenElse(cond: AOut => Boolean, ifTrue: AOut => IT1, ifFalse: AOut => IT2): ITR
}

object ITIfThenElse {

  import IT.{Id, Contramap, Impossible}

  type Aux[AIn, AOut, AIn1 <: AIn, AIn2 <: AIn, IT1P <: IT[AIn1], IT2P <: IT[AIn2], ITRP <: IT[AIn]] =
    ITIfThenElse[AIn, AOut, AIn1, AIn2] { type IT1 = IT1P; type IT2 = IT2P; type ITR = ITRP }

  implicit def idIfThenElseId[AIn, AOut, AIn1 <: AIn, AIn2 <: AIn]
    : ITIfThenElse.Aux[AIn, AOut, AIn1, AIn2, Id[AIn1], Id[AIn2], Id[AIn]] =
    new ITIfThenElse[AIn, AOut, AIn1, AIn2] {
      override type IT1 = Id[AIn1]
      override type IT2 = Id[AIn2]
      override type ITR = Id[AIn]

      override def ifThenElse(cond: AOut => Boolean, ifTrue: AOut => IT1, ifFalse: AOut => IT2): ITR =
        IT.Id()
    }

  implicit def idIfThenElseContraMap[AIn, AOut, AIn1 <: AIn, AIn2 <: AIn, AOut2 <: AOut](implicit
    ev: AOut <:< AIn,
    ev2: AOut =:= AOut2,
    ev3: AIn2 =:= AIn,
  ): ITIfThenElse.Aux[AIn, AOut, AIn1, AIn2, Id[AIn1], Contramap[AIn2, AOut2], Contramap[AIn, AOut]] =
    new ITIfThenElse[AIn, AOut, AIn1, AIn2] {
      override type IT1 = Id[AIn1]
      override type IT2 = Contramap[AIn2, AOut2]
      override type ITR = Contramap[AIn, AOut]

      override def ifThenElse(cond: AOut => Boolean, ifTrue: AOut => IT1, ifFalse: AOut => IT2): ITR =
        Contramap { (a: AOut) =>
          if (cond(a)) ev(a) else ev3(ifFalse(a).f(ev2(a)))
        }
    }

  implicit def idIfThenElseImpossible[AIn, AOut, AIn1 <: AIn, AIn2 <: AIn]
    : ITIfThenElse.Aux[AIn, AOut, AIn1, AIn2, Id[AIn1], Impossible[AIn2], Impossible[AIn]] =
    new ITIfThenElse[AIn, AOut, AIn1, AIn2] {
      override type IT1 = Id[AIn1]
      override type IT2 = Impossible[AIn2]
      override type ITR = Impossible[AIn]

      override def ifThenElse(cond: AOut => Boolean, ifTrue: AOut => IT1, ifFalse: AOut => IT2): ITR =
        IT.Impossible()
    }

  implicit def contraMapIfThenElseId[AIn, AOut, AIn1 <: AIn, AOut1 >: AOut, AIn2 <: AIn](implicit
    ev: AOut <:< AIn,
    ev2: AOut =:= AOut1,
    ev3: AIn1 =:= AIn,
  ): ITIfThenElse.Aux[AIn, AOut, AIn1, AIn2, Contramap[AIn1, AOut1], Id[AIn2], Contramap[AIn, AOut]] =
    new ITIfThenElse[AIn, AOut, AIn1, AIn2] {
      override type IT1 = Contramap[AIn1, AOut1]
      override type IT2 = Id[AIn2]
      override type ITR = Contramap[AIn, AOut]

      override def ifThenElse(cond: AOut => Boolean, ifTrue: AOut => IT1, ifFalse: AOut => IT2): ITR =
        Contramap { (a: AOut) =>
          if (cond(a)) ev3(ifTrue(a).f(ev2(a))) else ev(a)
        }
    }

  implicit def contraMapIfThenElseContraMap[AIn, AOut, AIn1 <: AIn, AOut1 >: AOut, AIn2 <: AIn, AOut2 >: AOut](implicit
    ev1: AOut =:= AOut1,
    ev2: AOut =:= AOut2,
    ev3: AIn1 =:= AIn,
    ev4: AIn2 =:= AIn,
  ): ITIfThenElse.Aux[AIn, AOut, AIn1, AIn2, Contramap[AIn1, AOut1], Contramap[AIn2, AOut2], Contramap[AIn, AOut]] =
    new ITIfThenElse[AIn, AOut, AIn1, AIn2] {
      override type IT1 = Contramap[AIn1, AOut1]
      override type IT2 = Contramap[AIn2, AOut2]
      override type ITR = Contramap[AIn, AOut]

      override def ifThenElse(cond: AOut => Boolean, ifTrue: AOut => IT1, ifFalse: AOut => IT2): ITR =
        Contramap { (a: AOut) =>
          if (cond(a)) ev3(ifTrue(a).f(ev1(a))) else ev4(ifFalse(a).f(ev2(a)))
        }
    }

  implicit def contraMapIfThenElseImpossible[AIn, AOut, AIn1 <: AIn, AIn2 <: AIn]
    : ITIfThenElse.Aux[AIn, AOut, AIn1, AIn2, Contramap[AIn1, AOut], Impossible[AIn2], Impossible[AIn]] =
    new ITIfThenElse[AIn, AOut, AIn1, AIn2] {
      override type IT1 = Contramap[AIn1, AOut]
      override type IT2 = Impossible[AIn2]
      override type ITR = Impossible[AIn]

      override def ifThenElse(cond: AOut => Boolean, ifTrue: AOut => IT1, ifFalse: AOut => IT2): ITR =
        IT.Impossible()
    }

  implicit def impossibleIfThenElseId[AIn, AIn1 <: AIn, AIn2 <: AIn]
    : ITIfThenElse.Aux[AIn, Nothing, AIn1, AIn2, Impossible[AIn1], Id[AIn2], Impossible[AIn]] =
    new ITIfThenElse[AIn, Nothing, AIn1, AIn2] {
      override type IT1 = Impossible[AIn1]
      override type IT2 = Id[AIn2]
      override type ITR = Impossible[AIn]

      override def ifThenElse(cond: Nothing => Boolean, ifTrue: Nothing => IT1, ifFalse: Nothing => IT2): ITR =
        Impossible()
    }

  implicit def impossibleIfThenElseContraMap[AIn, AIn1 <: AIn, AIn2 <: AIn, AOut2]
    : ITIfThenElse.Aux[AIn, Nothing, AIn1, AIn2, Impossible[AIn1], Contramap[AIn2, AOut2], Impossible[AIn]] =
    new ITIfThenElse[AIn, Nothing, AIn1, AIn2] {
      override type IT1 = Impossible[AIn1]
      override type IT2 = Contramap[AIn2, AOut2]
      override type ITR = Impossible[AIn]

      override def ifThenElse(cond: Nothing => Boolean, ifTrue: Nothing => IT1, ifFalse: Nothing => IT2): ITR =
        Impossible()
    }

  implicit def impossibleIfThenElseImpossible[AIn, AIn1 <: AIn, AIn2 <: AIn]
    : ITIfThenElse.Aux[AIn, Nothing, AIn1, AIn2, Impossible[AIn1], Impossible[AIn2], Impossible[AIn]] =
    new ITIfThenElse[AIn, Nothing, AIn1, AIn2] {
      override type IT1 = Impossible[AIn1]
      override type IT2 = Impossible[AIn2]
      override type ITR = Impossible[AIn]

      override def ifThenElse(cond: Nothing => Boolean, ifTrue: Nothing => IT1, ifFalse: Nothing => IT2): ITR =
        Impossible()
    }
}
