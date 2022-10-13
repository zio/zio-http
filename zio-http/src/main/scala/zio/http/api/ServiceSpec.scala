package zio.http.api

import zio.Chunk
import zio.http.HttpApp

// Map[Id, I => O]
// (callUsers.Id, Int, User) with
sealed trait ServiceSpec[MI, MO, -AllIds] { self =>
  final def ++[AllIds2](that: ServiceSpec[MI, MO, AllIds2]): ServiceSpec[MI, MO, AllIds with AllIds2] =
    ServiceSpec.Concat[MI, MO, AllIds, AllIds2](self, that)

  final def apis: Chunk[API[_, _]] = ServiceSpec.apisOf(self)

  final def middleware[MI2, MO2](
    ms: MiddlewareSpec[MI2, MO2],
  )(implicit mi: Combiner[MI, MI2], mo: Combiner[MO, MO2]): ServiceSpec[mi.Out, mo.Out, AllIds] =
    ServiceSpec.AddMiddleware[MI, MI2, mi.Out, MO, MO2, mo.Out, AllIds](self, ms, mi, mo)

  final def toHttpApp[AllIds1 <: AllIds, R, E](
    service: Service[R, E, AllIds1],
  )(implicit ev1: MI =:= Unit, ev2: MO =:= Unit): HttpApp[R, E] =
    self.withMI[Unit].withMO[Unit].toHttpApp(service, Middleware.none)

  final def toHttpApp[AllIds1 <: AllIds, R, E](
    service: Service[R, E, AllIds1],
    midddleware: Middleware[R, E, MI, MO],
  ): HttpApp[R, E] =
    service.toHttpApp // TODO: Use middleware!!!!!

  final def withMI[MI2](implicit ev: MI =:= MI2): ServiceSpec[MI2, MO, AllIds] =
    self.asInstanceOf[ServiceSpec[MI2, MO, AllIds]]

  final def withMO[MO2](implicit ev: MO =:= MO2): ServiceSpec[MI, MO2, AllIds] =
    self.asInstanceOf[ServiceSpec[MI, MO2, AllIds]]

  // def toServer(serverMiddleware): ServiceServer = ??? // HttpApp

  // def toClient(clientMiddleware): ServiceClient[Any] = ???

  // def handle(api: API[_, _, In, Out])(f: In => ZIO[R, E, Out]): ServiceSpec = ???
  // spec.handle(callUser) { .... }
}
object ServiceSpec                        {
  private case object Empty                                            extends ServiceSpec[Unit, Unit, Any]
  private final case class Single[Id, C, D](api: API.WithId[Id, C, D]) extends ServiceSpec[Unit, Unit, Id]
  private final case class Concat[MI, MO, AllIds1, AllIds2](
    left: ServiceSpec[MI, MO, AllIds1],
    right: ServiceSpec[MI, MO, AllIds2],
  ) extends ServiceSpec[MI, MO, AllIds1 with AllIds2]
  private final case class AddMiddleware[MI1, MI2, MI3, MO1, MO2, MO3, AllIds](
    spec: ServiceSpec[MI1, MO1, AllIds],
    middlewareSpec: MiddlewareSpec[MI2, MO2],
    mi: Combiner.WithOut[MI1, MI2, MI3],
    mo: Combiner.WithOut[MO1, MO2, MO3],
  ) extends ServiceSpec[MI3, MO3, AllIds]

  def empty: ServiceSpec[Unit, Unit, Any] = Empty

  private def apisOf(self: ServiceSpec[_, _, _]): Chunk[API[_, _]] =
    self match {
      case Empty                     => Chunk.empty
      case Concat(a, b)              => apisOf(a) ++ apisOf(b)
      case Single(a)                 => Chunk.single(a)
      case AddMiddleware(a, _, _, _) => apisOf(a)
    }
}

/*

serviceSpec.handleAll(handler1 ++ handler2 ++ handler3)

 */

trait ServiceServer
trait ServiceClient[A]

// import scala.annotation.implicitNotFound
// import scala.language.implicitConversions

// object hset extends App {

//   type ??? = Nothing

//   // Set(1, 2, 3) Set of ints

//   // Set("Adam", "Kit") Set of strings
//   // "Adam" :*: 1 :*: true :*: HNil

//   // HSet
//   // Type level set
//   // Design goals:
//   // 1. We should only ever be able to have one instance of a type in the set CHECK
//   // 2. Should be commutative and associative HALF CHECK
//   // 3. We should be able to look up a value in the set CHECK
//   // 4. We should be able to remove a value from the set CHECK
//   // 5. We should be able to take the union of two sets CHECK
//   // 6. We should be able to add values to the set CHECK

//   // 1 :*: true :*: HNil // okay
//   // 1 :*: 2 :*: true :*: HNil // not okay

//   // 1 :*: true :*: HNil === true :*: 1 :*: HNil
//   // set.get[Int]

//   // Has[Logging] with Has[Database] with Has[Monitoring]
//   // Has[Database] with Has[Monitoring] with Has[Logging] with Has[Database]

//   sealed trait HSet

//   object HSet {
//     implicit def identity[A, B, C, Set <: HSet](
//       set: Set
//     )(implicit ev1: Includes[A, Set], ev2: Includes[B, Set], ev3: Includes[C, Set]): A :&: B :&: C :&: HEmpty =
//       ev1(set) :&: ev2(set) :&: ev3(set) :&: HEmpty
//   }

//   case object HEmpty extends HSet { self =>
//     def :&:[Head](head: Head): Head :&: HEmpty =
//       hset.:&:(head, self)

//     def union[That <: HSet](that: That): That =
//       that
//   }

//   type HEmpty = HEmpty.type

//   final case class :&:[Head, Tail <: HSet](head: Head, tail: Tail) extends HSet { self =>
//     def :&:[That](that: That)(implicit eliminate: Eliminate[That, Head :&: Tail]): That :&: eliminate.Out =
//       hset.:&:(that, eliminate(self))

//     def get[Element](implicit includes: Includes[Element, Head :&: Tail]): Element =
//       includes(self)

//     def union[That <: HSet](that: That)(implicit union: Union[Head :&: Tail, That]): union.Out =
//       union(self, that)
//   }

//   // Element = type to elemiminate from set
//   // Set = original set before elimination
//   // Out = resulting set after elimination
//   // Eliminate[Int, Int :&: String :&: HEmpty] === String :&: HEmpty
//   // Eliminate[Int, String :&: Boolean :&: HEmpty] === String :&: Boolean :&: HEmpty

//   trait Eliminate[Element, Set <: HSet] {
//     type Out <: HSet
//     def apply(set: Set): Out
//   }

//   object Eliminate extends EliminateLowPriority {
//     // Sometimes, unfortunately "Aux"
//     type WithOut[Element, Set <: HSet, Out0 <: HSet] = Eliminate[Element, Set] { type Out = Out0 }

//     implicit def empty[Head]: Eliminate.WithOut[Head, HEmpty, HEmpty] =
//       new Eliminate[Head, HEmpty] {
//         type Out = HEmpty
//         def apply(set: HEmpty.type): Out =
//           set
//       }

//     implicit def eliminate[Head, Tail <: HSet]: Eliminate.WithOut[Head, Head :&: Tail, Tail] =
//       new Eliminate[Head, Head :&: Tail] {
//         type Out = Tail
//         def apply(set: Head :&: Tail): Out =
//           set.tail
//       }
//   }

//   trait EliminateLowPriority {
//     implicit def recurse[Element, Head, Tail <: HSet](implicit
//       eliminate: Eliminate[Element, Tail]
//     ): Eliminate.WithOut[Element, Head :&: Tail, Head :&: eliminate.Out] =
//       new Eliminate[Element, Head :&: Tail] {
//         type Out = Head :&: eliminate.Out

//         def apply(set: Head :&: Tail): Head :&: eliminate.Out =
//           hset.:&:(set.head, eliminate(set.tail))
//       }
//   }

//   @implicitNotFound("${Element} was not in ${Set}")
//   trait Includes[Element, Set <: HSet] {
//     def apply(set: Set): Element
//   }

//   object Includes extends IncludesLowPriority {
//     implicit def includes[Head, Tail <: HSet]: Includes[Head, Head :&: Tail] =
//       new Includes[Head, Head :&: Tail] {
//         def apply(set: Head :&: Tail): Head =
//           set.head
//       }

//     def includesValue[A](element: A, list: List[A]): A =
//       list match {
//         case Nil                             => throw new Error(s"${element} was not in ${list}")
//         case head :: next if head == element => head
//         case head :: tail                    => includesValue(element, tail)
//       }
//   }

//   trait IncludesLowPriority {
//     implicit def recursive[Element, Head, Tail <: HSet](implicit
//       includes: Includes[Element, Tail]
//     ): Includes[Element, Head :&: Tail] =
//       new Includes[Element, Head :&: Tail] {

//         def apply(set: Head :&: Tail): Element =
//           includes(set.tail)
//       }
//   }

//   trait Union[Left <: HSet, Right <: HSet] {
//     type Out <: HSet
//     def apply(left: Left, right: Right): Out
//   }

//   object Union extends UnionLowPriority {
//     type WithOut[Left <: HSet, Right <: HSet, Out0] = Union[Left, Right] { type Out = Out0 }

//     implicit def leftEmpty[Right <: HSet]: Union.WithOut[HEmpty, Right, Right] =
//       new Union[HEmpty, Right] {
//         type Out = Right
//         def apply(left: HEmpty.type, right: Right): Out =
//           right
//       }

//     implicit def removeAndRecurse[LeftHead, LeftTail <: HSet, Right <: HSet](implicit
//       includes: Includes[LeftHead, Right],
//       union: Union[LeftTail, Right]
//     ): Union.WithOut[LeftHead :&: LeftTail, Right, union.Out] =
//       new Union[LeftHead :&: LeftTail, Right] {
//         type Out = union.Out
//         def apply(left: LeftHead :&: LeftTail, right: Right): union.Out =
//           union(left.tail, right)
//       }

//     def unionValue[A](left: List[A], right: List[A]): List[A] =
//       (left, right) match {
//         case (Nil, right) => right
//         case (left, Nil)  => left
//         case (leftHead :: leftTail, right) =>
//           if (right.contains(leftHead))
//             unionValue(leftTail, right)
//           else
//             leftHead :: unionValue(leftTail, right)
//       }
//   }

//   trait UnionLowPriority {

//     implicit def recurse[LeftHead, LeftTail <: HSet, RightHead, RightTail <: HSet](implicit
//       union: Union[LeftTail, RightHead :&: RightTail]
//     ): Union.WithOut[LeftHead :&: LeftTail, RightHead :&: RightTail, LeftHead :&: union.Out] =
//       new Union[LeftHead :&: LeftTail, RightHead :&: RightTail] {
//         type Out = LeftHead :&: union.Out
//         def apply(left: LeftHead :&: LeftTail, right: RightHead :&: RightTail): LeftHead :&: union.Out =
//           hset.:&:(left.head, union(left.tail, right))
//       }

//     implicit def rightEmpty[Left <: HSet]: Union.WithOut[Left, HEmpty, Left] =
//       new Union[Left, HEmpty] {
//         type Out = Left
//         def apply(left: Left, right: HEmpty.type): Left =
//           left
//       }
//   }

//   object Example {

//     val set: String :&: Boolean :&: Int :&: HEmpty.type =
//       "Kit" :&: true :&: "Adam" :&: 1 :&: HEmpty

//     val set2 = List(1) :&: List("Adam") :&: List(10) :&: true :&: HEmpty

//     val myString = set.get[String]
//     // val myDouble = set.get[Double]

//     val sameSet: Boolean :&: String :&: Int :&: HEmpty.type =
//       "Kit" :&: true :&: "Adam" :&: 1 :&: HEmpty

//     val union1 = set union set
//     val union2 = set union set2
//   }

//   trait ZIO[-R, +E, +A]

//   implicit final class HSetOps[R <: HSet, E, A](private val self: ZIO[R, E, A]) extends AnyVal {
//     def flatMap[R1 <: HSet, E1, B](f: A => ZIO[R1, E1, B])(implicit union: Union[R, R1]): ZIO[union.Out, E1, B] =
//       ???
//     def provideSome[R1](r1: R1)(implicit eliminate: Eliminate[R1, R]): ZIO[eliminate.Out, E, A] =
//       ???
//   }

//   trait Database
//   trait Logging
//   trait Monitoring

//   val sampleDatabase: Database = ???

//   val zio1: ZIO[Database :&: Logging :&: HEmpty, Nothing, Int] = ???

//   val zio2: ZIO[Logging :&: Monitoring :&: HEmpty, Nothing, String] = ???

//   val zio3: ZIO[Database :&: Logging :&: Monitoring :&: HEmpty, Nothing, String] =
//     zio1.flatMap(_ => zio2)

//   val zio4: ZIO[Logging :&: Monitoring :&: HEmpty.type, Nothing, String] =
//     zio3.provideSome(sampleDatabase)
// }
