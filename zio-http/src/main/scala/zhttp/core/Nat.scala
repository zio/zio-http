package zhttp.core

import scala.annotation.unused

sealed trait Nat
object Nat {
  trait Zero                extends Nat
  trait Successor[N <: Nat] extends Nat

  type One = Successor[Zero]
  type Two = Successor[One]

  trait >[A <: Nat, B <: Nat]

  object > {
    def apply[A <: Nat, B <: Nat](): >[A, B]                                                          = null.asInstanceOf[>[A, B]]
    implicit def base[A <: Nat]: >[Successor[A], Zero]                                                = >[Successor[A], Zero]
    implicit def other[A <: Nat, B <: Nat](implicit @unused ev: A > B): >[Successor[A], Successor[B]] =
      >[Successor[A], Successor[B]]
  }
}
