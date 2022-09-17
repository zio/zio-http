package zio.http.api.internal

import zio.Chunk
import zio.http.api._
import zio.http.api.In._

object Mechanic {
  type Constructor[+A]   = InputResults => A
  type Deconstructor[-A] = A => InputResults

  def flatten(in: In[_]): FlattenedAtoms = {
    var result = FlattenedAtoms.empty
    flattenedAtoms(in).foreach { atom =>
      result = result.append(atom)
    }
    result
  }

  private def flattenedAtoms(in: In[_]): Chunk[Atom[_]] =
    in match {
      case Combine(left, right, _) => flattenedAtoms(left) ++ flattenedAtoms(right)
      case atom: Atom[_]           => Chunk(atom)
      case map: Transform[_, _]    => flattenedAtoms(map.api)
      case WithDoc(api, _)         => flattenedAtoms(api)
    }

  private def indexed[A](api: In[A]): In[A] =
    indexedImpl(api, AtomIndices())._1

  private def indexedImpl[A](api: In[A], indices: AtomIndices): (In[A], AtomIndices) =
    api.asInstanceOf[In[_]] match {
      case Combine(left, right, inputCombiner) =>
        val (left2, leftIndices)   = indexedImpl(left, indices)
        val (right2, rightIndices) = indexedImpl(right, leftIndices)
        (Combine(left2, right2, inputCombiner).asInstanceOf[In[A]], rightIndices)
      case atom: Atom[_]                       =>
        (IndexedAtom(atom, indices.get(atom)).asInstanceOf[In[A]], indices.increment(atom))
      case Transform(api, f, g)                =>
        val (api2, resultIndices) = indexedImpl(api, indices)
        (Transform(api2, f, g).asInstanceOf[In[A]], resultIndices)

      case WithDoc(api, _) => indexedImpl(api.asInstanceOf[In[A]], indices)
    }

  def makeConstructor[A](api: In[A]): Constructor[A] =
    makeConstructorLoop(indexed(api))

  def makeDeconstructor[A](api: In[A]): Deconstructor[A] =
    makeDeconstructorLoop(indexed(api))

  private def makeConstructorLoop[A](api: In[A]): Constructor[A] = {
    def coerce(any: Any): A = any.asInstanceOf[A]

    api match {
      case Combine(left, right, inputCombiner) =>
        val leftThread  = makeConstructorLoop(left)
        val rightThread = makeConstructorLoop(right)

        results => {
          val leftValue  = leftThread(results)
          val rightValue = rightThread(results)

          inputCombiner.combine(leftValue, rightValue)
        }

      case IndexedAtom(_: Route[_], index)     =>
        results => coerce(results.routes(index))
      case IndexedAtom(_: Header[_], index)    =>
        results => coerce(results.headers(index))
      case IndexedAtom(_: Query[_], index)     =>
        results => coerce(results.queries(index))
      case IndexedAtom(_: InputBody[_], index) =>
        results => coerce(results.inputBody(index))

      case transform: Transform[_, A] =>
        val threaded = makeConstructorLoop(transform.api)
        results => transform.f(threaded(results))

      case WithDoc(api, _) => makeConstructorLoop(api)

      case atom: Atom[_] =>
        throw new RuntimeException(s"Atom $atom should have been wrapped in IndexedAtom")
    }
  }

  private def makeDeconstructorLoop[A](api: In[A]): Deconstructor[A] = {
    api match {
      case Combine(left, right, inputCombiner) =>
        val leftDeconstructor  = makeDeconstructorLoop(left)
        val rightDeconstructor = makeDeconstructorLoop(right)

        input => {
          val (left, right) = inputCombiner.separate(input)

          leftDeconstructor(left) ++ rightDeconstructor(right)
        }

      case IndexedAtom(_: Route[_], _)     => ???
      case IndexedAtom(_: Header[_], _)    => ???
      case IndexedAtom(_: Query[_], _)     => ???
      case IndexedAtom(_: InputBody[_], _) => ???

      case _: Transform[_, A] => ???

      case WithDoc(api, _) => makeDeconstructorLoop(api)

      case atom: Atom[_] =>
        throw new RuntimeException(s"Atom $atom should have been wrapped in IndexedAtom")
    }
  }

  // Private Helper Classes

  private[api] final case class FlattenedAtoms(
    routes: Chunk[TextCodec[_]],
    queries: Chunk[Query[_]],
    headers: Chunk[Header[_]],
    inputBodies: Chunk[InputBody[_]],
  ) {
    def append(atom: Atom[_]) = atom match {
      case route: Route[_]         => copy(routes = routes.appended(route.textCodec))
      case query: Query[_]         => copy(queries = queries.appended(query))
      case header: Header[_]       => copy(headers = headers.appended(header))
      case inputBody: InputBody[_] => copy(inputBodies = inputBodies.appended(inputBody))
      case _: IndexedAtom[_]       => throw new RuntimeException("IndexedAtom should not be appended to FlattenedAtoms")
    }
  }

  private[api] object FlattenedAtoms {
    val empty = FlattenedAtoms(Chunk.empty, Chunk.empty, Chunk.empty, Chunk.empty)
  }

  private[api] final case class InputResults(
    routes: Chunk[Any] = Chunk.empty,
    queries: Chunk[Any] = Chunk.empty,
    headers: Chunk[Any] = Chunk.empty,
    inputBody: Chunk[Any] = Chunk.empty,
  ) { self =>
    def ++(that: InputResults): InputResults =
      InputResults(
        routes = self.routes ++ that.routes,
        queries = self.queries ++ that.queries,
        headers = self.headers ++ that.headers,
        inputBody = self.inputBody ++ that.inputBody,
      )
  }
  private[api] object InputResults   {
    val empty: InputResults = InputResults(Chunk.empty, Chunk.empty, Chunk.empty, Chunk.empty)
  }

  private final case class AtomIndices(
    route: Int = 0,
    query: Int = 0,
    header: Int = 0,
    inputBody: Int = 0,
  ) {
    def increment(atom: Atom[_]): AtomIndices = {
      atom match {
        case _: Route[_]       => copy(route = route + 1)
        case _: Query[_]       => copy(query = query + 1)
        case _: Header[_]      => copy(header = header + 1)
        case _: InputBody[_]   => copy(inputBody = inputBody + 1)
        case _: IndexedAtom[_] => throw new RuntimeException("IndexedAtom should not be passed to increment")
      }
    }

    def get(atom: Atom[_]): Int =
      atom match {
        case _: Route[_]       => route
        case _: Query[_]       => query
        case _: Header[_]      => header
        case _: InputBody[_]   => inputBody
        case _: IndexedAtom[_] => throw new RuntimeException("IndexedAtom should not be passed to get")
      }
  }
}
