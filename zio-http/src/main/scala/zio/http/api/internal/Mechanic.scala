package zio.http.api.internal

import zio.Chunk
import zio.http.api.In._
import zio.http.api._
import zio.http.model.Headers
import zio.stacktracer.TracingImplicits.disableAutoTrace // scalafix:ok;

private[api] object Mechanic {
  type Constructor[+A]   = InputsBuilder => A
  type Deconstructor[-A] = A => InputsBuilder

  def flatten(in: In[_, _]): FlattenedAtoms = {
    var result = FlattenedAtoms.empty
    flattenedAtoms(in).foreach { atom =>
      result = result.append(atom)
    }
    result
  }

  private def flattenedAtoms(in: In[_, _]): Chunk[Atom[_, _]] =
    in match {
      case Combine(left, right, _)       => flattenedAtoms(left) ++ flattenedAtoms(right)
      case atom: Atom[_, _]              => Chunk(atom)
      case map: TransformOrFail[_, _, _] => flattenedAtoms(map.api)
      case WithDoc(api, _)               => flattenedAtoms(api)
    }

  private def indexed[R, A](api: In[R, A]): In[R, A] =
    indexedImpl(api, AtomIndices())._1

  private def indexedImpl[R, A](api: In[R, A], indices: AtomIndices): (In[R, A], AtomIndices) =
    api.asInstanceOf[In[_, _]] match {
      case Combine(left, right, inputCombiner) =>
        val (left2, leftIndices)   = indexedImpl(left, indices)
        val (right2, rightIndices) = indexedImpl(right, leftIndices)
        (Combine(left2, right2, inputCombiner).asInstanceOf[In[R, A]], rightIndices)
      case atom: Atom[_, _]                    =>
        (IndexedAtom(atom, indices.get(atom)).asInstanceOf[In[R, A]], indices.increment(atom))
      case TransformOrFail(api, f, g)          =>
        val (api2, resultIndices) = indexedImpl(api, indices)
        (TransformOrFail(api2, f, g).asInstanceOf[In[R, A]], resultIndices)

      case WithDoc(api, _) => indexedImpl(api.asInstanceOf[In[R, A]], indices)
    }

  def makeConstructor[A](
    api: In[In.RouteType with In.HeaderType with In.BodyType with In.QueryType, A],
  ): Constructor[A] =
    makeConstructorLoop(indexed(api))

  def makeDeconstructor[A](
    api: In[In.RouteType with In.HeaderType with In.BodyType with In.QueryType, A],
  ): Deconstructor[A] = {
    val flattened = flatten(api)

    val deconstructor = makeDeconstructorLoop(indexed(api))

    (a: A) => {
      val inputsBuilder = flattened.makeInputsBuilder()
      deconstructor(a, inputsBuilder)
      inputsBuilder
    }
  }

  private def makeConstructorLoop[A](
    api: In[In.RouteType with In.HeaderType with In.BodyType with In.QueryType, A],
  ): Constructor[A] = {
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
        results => coerce(results.inputBodies(index))

      case transform: TransformOrFail[_, _, A] =>
        val threaded = makeConstructorLoop(transform.api)
        results =>
          transform.f(threaded(results)) match {
            case Left(value)  => throw new RuntimeException(value)
            case Right(value) => value
          }

      case WithDoc(api, _) => makeConstructorLoop(api)

      case atom: Atom[_, _] =>
        throw new RuntimeException(s"Atom $atom should have been wrapped in IndexedAtom")
    }
  }

  private def makeDeconstructorLoop[A](
    api: In[In.RouteType with In.HeaderType with In.BodyType with In.QueryType, A],
  ): (A, InputsBuilder) => Unit = {
    api match {
      case Combine(left, right, inputCombiner) =>
        val leftDeconstructor  = makeDeconstructorLoop(left)
        val rightDeconstructor = makeDeconstructorLoop(right)

        (input, inputsBuilder) => {
          val (left, right) = inputCombiner.separate(input)

          leftDeconstructor(left, inputsBuilder)
          rightDeconstructor(right, inputsBuilder)
        }

      case IndexedAtom(_: Route[_], index) =>
        (input, inputsBuilder) => inputsBuilder.setRoute(index, input)

      case IndexedAtom(_: Header[_], index) =>
        (input, inputsBuilder) => inputsBuilder.setHeader(index, input)

      case IndexedAtom(_: Query[_], index) =>
        (input, inputsBuilder) => inputsBuilder.setQuery(index, input)

      case IndexedAtom(_: InputBody[_], index) =>
        (input, inputsBuilder) => inputsBuilder.setInputBody(index, input)

      case transform: TransformOrFail[_, _, A] =>
        val deconstructor = makeDeconstructorLoop(transform.api)

        (input, inputsBuilder) =>
          deconstructor(
            transform.g(input) match {
              case Left(value)  => throw new RuntimeException(value)
              case Right(value) => value
            },
            inputsBuilder,
          )

      case WithDoc(api, _) => makeDeconstructorLoop(api)

      case atom: Atom[_, _] =>
        throw new RuntimeException(s"Atom $atom should have been wrapped in IndexedAtom")
    }
  }

  // Private Helper Classes

  private[api] final case class FlattenedAtoms(
    routes: Chunk[TextCodec[_]],
    queries: Chunk[Query[_]],
    headers: Chunk[Header[_]],
    inputBodies: Chunk[InputBody[_]],
  ) { self =>
    def append(atom: Atom[_, _]) = atom match {
      case Empty                   => self
      case route: Route[_]         => self.copy(routes = routes :+ route.textCodec)
      case query: Query[_]         => self.copy(queries = queries :+ query)
      case header: Header[_]       => self.copy(headers = headers :+ header)
      case inputBody: InputBody[_] => self.copy(inputBodies = inputBodies :+ inputBody)
      case _: BodyStream[_]        => self // TODO: Support body streams
      case _: IndexedAtom[_, _]    => throw new RuntimeException("IndexedAtom should not be appended to FlattenedAtoms")
    }

    def makeInputsBuilder(): InputsBuilder = {
      Mechanic.InputsBuilder.make(routes.length, queries.length, headers.length, inputBodies.length, 2)
    }
  }

  private[api] object FlattenedAtoms {
    val empty = FlattenedAtoms(Chunk.empty, Chunk.empty, Chunk.empty, Chunk.empty)
  }

  private[api] final case class InputsBuilder(
    routes: Array[Any],
    queries: Array[Any],
    headers: Array[Any],
    inputBodies: Array[Any],
    responseHeaders: Array[Any],
  ) { self =>
    def setRoute(index: Int, value: Any): Unit =
      routes(index) = value

    def setQuery(index: Int, value: Any): Unit =
      queries(index) = value

    def setHeader(index: Int, value: Any): Unit =
      headers(index) = value

    def setInputBody(index: Int, value: Any): Unit =
      inputBodies(index) = value
  }
  private[api] object InputsBuilder  {
    def make(routes: Int, queries: Int, headers: Int, bodies: Int, responseHeaders: Int): InputsBuilder =
      InputsBuilder(
        routes = new Array(routes),
        queries = new Array(queries),
        headers = new Array(headers),
        inputBodies = new Array(bodies),
        responseHeaders = new Array(responseHeaders),
      )
  }

  private final case class AtomIndices(
    route: Int = 0,
    query: Int = 0,
    header: Int = 0,
    inputBody: Int = 0,
  ) { self =>
    def increment(atom: Atom[_, _]): AtomIndices = {
      atom match {
        case _: Empty.type        => self
        case _: Route[_]          => self.copy(route = route + 1)
        case _: Query[_]          => self.copy(query = query + 1)
        case _: Header[_]         => self.copy(header = header + 1)
        case _: InputBody[_]      => self.copy(inputBody = inputBody + 1)
        case _: BodyStream[_]     => self // TODO: Support body streams
        case _: IndexedAtom[_, _] => throw new RuntimeException("IndexedAtom should not be passed to increment")
      }
    }

    def get(atom: Atom[_, _]): Int =
      atom match {
        case Empty                => throw new RuntimeException("Empty should not be passed to get")
        case _: Route[_]          => route
        case _: Query[_]          => query
        case _: Header[_]         => header
        case _: InputBody[_]      => inputBody
        case _: BodyStream[_]     => throw new RuntimeException("FIXME: Support BodyStream")
        case _: IndexedAtom[_, _] => throw new RuntimeException("IndexedAtom should not be passed to get")
      }
  }
}
