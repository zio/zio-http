package zio.http.endpoint.cli

import zio.test.Gen

/**
 * Class Tree helps construct a generator with desired depth of a tree structure
 */

final case class Compare[A, Repr](a: A, repr: Repr)

final case class Mapper[A, B](f: ((A, B) => A), anyB: Gen[Any, B])

final case class Tree[A](
  atoms: List[Gen[Any, A]],
  mappers: List[Mapper[A, Any]],
  operators: List[((A, A) => A)],
  depth: Int,
) {

  def gen: Gen[Any, A] = for {
    treeDepth <- Gen.bounded(0, 2)(Gen.const(_))
    tree      <- anyTree(treeDepth)
  } yield tree

  lazy val anyAtom: Gen[Any, A] = Gen.oneOf(atoms: _*)

  def anyTree(depth: Int): Gen[Any, A] =
    if (depth <= 0)
      anyAtom
    else {
      val genMappers   = mappers.map { case Mapper(f, anyB) =>
        anyTree(depth - 1).zip(anyB).map { case (a, b) =>
          f(a, b)
        }
      }
      val genOperators = operators.map { case f =>
        anyTree(depth - 1).zip(anyTree(depth - 1)).map { case (a, b) =>
          f(a, b)
        }
      }
      val gens         = genMappers ++ genOperators
      Gen.oneOf(gens: _*)
    }
}
