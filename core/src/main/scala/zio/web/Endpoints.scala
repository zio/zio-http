package zio.web

import zio.URIO
//import zio.{Has, Tag, Task, URIO, ZIO}
//import com.github.ghik.silencer.silent

//import scala.annotation.implicitNotFound
import scala.language.implicitConversions

sealed trait Endpoints

object Endpoints {

  sealed case class ::[H <: Endpoint[_, _, _], T <: Endpoints] private[web] (head: H, tail: T) extends Endpoints
  sealed trait Empty extends Endpoints
  private[web] case object Empty extends Empty

  val empty: Empty = Empty

  implicit class EndpointsOps[A <: Endpoints](self: A) {
    def ::[H <: Endpoint[_, _, _]](head: H): H :: A = new ::(head, self)

    // def invoke[M, I, O](endpoint: Endpoint[M, I, O])(request: I)(implicit get: Lens.Get[A, Endpoint[M, I, O]], tagA: Tag[A])
    //   : ZIO[Has[ClientService[A]], Throwable, O]
    //   = {
    //     val _ = tagA
    //     ZIO.accessM[Has[ClientService[A]]](_.get[ClientService[A]].invoke(endpoint, request))
    //   }
  }

  // navigate endpoints
  implicit def toLens[A <: Endpoints](endpoints: A): Lens[Endpoints.Empty, A] =
    Lens(endpoints)

  // recreate endpoints list
  implicit def toEndpoints[P <: Endpoints, S <: Endpoints](lens: Lens[P, S])(implicit zip: Lens.Zip[P, S]): zip.Out =
    zip(lens.prefix, lens.suffix)

  trait ClientService[A <: Endpoints] {
    // def invoke[M, I, O](endpoint: Endpoint[M, I, O], request: I)(implicit get: Lens.Get[A, Endpoint[M, I, O]]): Task[O]
  }

  

  /**
   * Allows to zoom to and update endpoints.
   * 
   * @type P Represents the prefix endpoints list, its in reversed order, its head endpoint is the "previous" element.
   * @type S Represents the suffix endpoints list, its head endpoint is the "current" element, and its tail are the "following" elements.
   */
  case class Lens[P <: Endpoints, S <: Endpoints](prefix: P, suffix: S) { self =>

    type Self = Lens[P, S]

    /**
     * Attach handler to currently zoomed endpoint.
     */
    def attach[R, I, O](handler: I => URIO[R, O])(implicit attach: Lens.Attach[Self, I => URIO[R, O]]): attach.Out =
      attach(self, handler)

    /**
     * Zoom in on the next endpoint.
     */
    def next(implicit next: Lens.Next[Self]): next.Out = next(self)

    /**
     * Zoom in on the previous endpoint.
     */
    def prev(implicit prev: Lens.Prev[Self]): prev.Out = prev(self)
  }

  object Lens {

    trait DepOp[A] {
      type Out
      def apply(a: A): Out
    }

    trait DepOp2[A, B] {
      type Out
      def apply(a: A, b: B): Out
    }

    def apply[A <: Endpoints](endpoints: A): Lens[Endpoints.Empty, A] =
      Lens[Endpoints.Empty, A](Endpoints.empty, endpoints)

    /**
     * Type class to attach handler to the currently zoomed endpoint.
     */
    trait Attach[A, B] extends DepOp2[A, B]

    object Attach {
      type Aux[A, B, Out0] = Attach[A, B] { type Out = Out0 }

      def apply[A, B](implicit tc: Attach[A, B]): Aux[A, B, tc.Out] = tc

      implicit def instance[R, M, I, O, P <: Endpoints, S0 <: Endpoint[M, I, O], S <: Endpoints]
        (implicit ev: S0 =:= Endpoint.Def[M, I, O])
        = new Attach[Lens[P, S0 :: S], I => URIO[R, O]] {
          type Out = Lens[P, Endpoint.Api[R, M, I, O] :: S]

          def apply(a: Lens[P, S0 :: S], b: I => URIO[R, O]): Lens[P, Endpoint.Api[R,M,I,O] :: S] =
            Lens(a.prefix, a.suffix.head.handler(b) :: a.suffix.tail)
        }
    }

    /**
     * Type class to zoom in on the next endpoint.
     */
    trait Next[A] extends DepOp[A]

    object Next {
      type Aux[A, Out0] = Next[A] { type Out = Out0 }

      def apply[A](implicit tc: Next[A]): Aux[A, tc.Out] = tc

      implicit def instance[P <: Endpoints, S0 <: Endpoint[_, _, _], S <: Endpoints]
        : Aux[Lens[P, S0 :: S], Lens[S0 :: P, S]]
        = new Next[Lens[P, S0 :: S]] {
          type Out = Lens[S0 :: P, S]

          def apply(a: Lens[P, S0 :: S]) =
            Lens(a.suffix.head :: a.prefix, a.suffix.tail)
        }
    }

    /**
     * Type class to zoom in on the prev endpoint.
     */
    trait Prev[A] extends DepOp[A]

    object Prev {
      type Aux[A, Out0] = Prev[A] { type Out = Out0 }

      def apply[A](implicit tc: Prev[A]): Aux[A, tc.Out] = tc

      implicit def instance[P0 <: Endpoint[_, _, _], P <: Endpoints, S <: Endpoints]
        : Aux[Lens[P0 :: P, S], Lens[P, P0 :: S]]
        = new Prev[Lens[P0 :: P, S]] {
          type Out = Lens[P, P0 :: S]

          def apply(a: Lens[P0 :: P, S]) = 
            Lens(a.prefix.tail, a.prefix.head :: a.suffix)
        }
    }

    /**
     * Type class to reverse the order of endpoints.
     */
    trait Reverse[A <: Endpoints] extends DepOp[A] { type Out <: Endpoints }

    object Reverse {
      def apply[A <: Endpoints](implicit reverse: Reverse[A]): Aux[A, reverse.Out] = reverse

      type Aux[A <: Endpoints, B <: Endpoints] = Reverse[A] { type Out = B }

      implicit def instance[A <: Endpoints, B <: Endpoints](implicit loop: Loop[Empty, A, B])
        : Aux[A, B]
        = new Reverse[A] {
          type Out = B

          def apply(a: A): Out = loop(empty, a)
        }

      /**
       * Type class to loop through endpoints list building up a list in reverse order.
       */
      trait Loop[S <: Endpoints, A <: Endpoints, B <: Endpoints] {
        def apply(acc: S, a: A): B
      }

      object Loop {
        implicit def base[A <: Endpoints]
          : Loop[A, Empty, A]
          = new Loop[A, Empty, A] {
            def apply(acc: A, a: Empty): A = acc
          }

        implicit def inductive[S <: Endpoints, A0 <: Endpoint[_, _, _], A <: Endpoints, B <: Endpoints]
          (implicit loop : Loop[A0 :: S, A, B])
            : Loop[S, A0 :: A, B]
            = new Loop[S, A0 :: A, B] {
              def apply(acc: S, a: A0 :: A) : B =
                loop(a.head :: acc, a.tail)
            }
      }
    }

    /**
     * Type class to zip the lens back to an endpoints list.
     */
    trait Zip[P <: Endpoints, S <: Endpoints] extends DepOp2[P, S] { type Out <: Endpoints }

    trait Zip0 {
      type Aux[P <: Endpoints, S <: Endpoints, PS <: Endpoints] = Zip[P, S] { type Out = PS }

      // trivial case, we're zipping with an empty suffix, which is equivalent to returning the reversed prefix
      implicit def empty[P <: Endpoints, S <: Empty](implicit reverse: Reverse[P])
        : Aux[P, S, reverse.Out]
        = new Zip[P, S] {
          type Out = reverse.Out

          def apply(prefix: P, suffix: S): reverse.Out = reverse(prefix)
        }
    }

    object Zip extends Zip0 {
      // base step, we're zipping with an empty prefix, which is equivalent to returning the suffix
      implicit def base[P <: Empty, S <: Endpoints]
        : Aux[P, S, S]
        = new Zip[P, S] {
          type Out = S

          def apply(prefix: P, suffix: S): S = suffix
        }

      // inductive step, we're prepending the suffix with the head element of prefix list and recursively dealing with prefix tail
      implicit def inductive[P0 <: Endpoint[_, _, _], P <: Endpoints, S <: Endpoints]
        (implicit zip: Zip[P, P0 :: S])
          : Aux[P0 :: P, S, zip.Out]
          = new Zip[P0 :: P, S] {
            type Out = zip.Out

            def apply(prefix: P0 :: P, suffix: S): Out =
              zip(prefix.tail, prefix.head :: suffix)
          }  
    }
  }
}