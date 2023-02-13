package zio.http.api.internal

import zio.Chunk
import zio.http.api.HttpCodec._
import zio.http.api._
import zio.http.model.Headers
import zio.stacktracer.TracingImplicits.disableAutoTrace // scalafix:ok;

private[api] object Mechanic {
  type Constructor[+A]   = InputsBuilder => A
  type Deconstructor[-A] = A => InputsBuilder

  def flatten[R, A](in: HttpCodec[R, A]): FlattenedAtoms = {
    var result = FlattenedAtoms.empty
    flattenedAtoms(in).foreach { atom =>
      result = result.append(atom)
    }
    result
  }

  private def flattenedAtoms[R, A](in: HttpCodec[R, A]): Chunk[Atom[_, _]] =
    in match {
      case Combine(left, right, _)       => flattenedAtoms(left) ++ flattenedAtoms(right)
      case atom: Atom[_, _]              => Chunk(atom)
      case map: TransformOrFail[_, _, _] => flattenedAtoms(map.api)
      case WithDoc(api, _)               => flattenedAtoms(api)
      case Empty                         => Chunk.empty
      case Fallback(_, _) => throw new UnsupportedOperationException("Cannot handle fallback at this level")
    }

  private def indexed[R, A](api: HttpCodec[R, A]): HttpCodec[R, A] =
    indexedImpl(api, AtomIndices())._1

  private def indexedImpl[R, A](api: HttpCodec[R, A], indices: AtomIndices): (HttpCodec[R, A], AtomIndices) =
    api.asInstanceOf[HttpCodec[_, _]] match {
      case Combine(left, right, inputCombiner) =>
        val (left2, leftIndices)   = indexedImpl(left, indices)
        val (right2, rightIndices) = indexedImpl(right, leftIndices)
        (Combine(left2, right2, inputCombiner).asInstanceOf[HttpCodec[R, A]], rightIndices)
      case atom: Atom[_, _]                    =>
        (IndexedAtom(atom, indices.get(atom)).asInstanceOf[HttpCodec[R, A]], indices.increment(atom))
      case TransformOrFail(api, f, g)          =>
        val (api2, resultIndices) = indexedImpl(api, indices)
        (TransformOrFail(api2, f, g).asInstanceOf[HttpCodec[R, A]], resultIndices)

      case WithDoc(api, _) => indexedImpl(api.asInstanceOf[HttpCodec[R, A]], indices)
      case Empty           => (Empty.asInstanceOf[HttpCodec[R, A]], indices)
      case Fallback(_, _)  => throw new UnsupportedOperationException("Cannot handle fallback at this level")
    }

  def makeConstructor[R, A](
    api: HttpCodec[R, A],
  ): Constructor[A] =
    makeConstructorLoop(indexed(api))

  def makeDeconstructor[R, A](
    api: HttpCodec[R, A],
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
    api: HttpCodec[Nothing, A],
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
        results => {
          val headerValue = results.headers(index)

          headerValue match {
            case header1: EndpointError.MissingHeader =>
              throw header1
            case any                                  =>
              coerce(any)
          }
        }
      case IndexedAtom(_: Query[_], index)     =>
        results => coerce(results.queries(index))
      case IndexedAtom(_: Body[_], index)      =>
        results => coerce(results.bodies(index))
      case IndexedAtom(_: Method[_], index)    =>
        results => coerce(results.methods(index))
      case transform: TransformOrFail[_, _, A] =>
        val threaded = makeConstructorLoop(transform.api)
        results =>
          transform.f(threaded(results)) match {
            case Left(value)  => throw new RuntimeException(value)
            case Right(value) => value
          }

      case WithDoc(api, _) => makeConstructorLoop(api)

      case Empty =>
        _ => coerce(())

      case atom: Atom[_, _] =>
        throw new RuntimeException(s"Atom $atom should have been wrapped in IndexedAtom")

      case Fallback(_, _) => throw new UnsupportedOperationException("Cannot handle fallback at this level")
    }
  }

  private def makeDeconstructorLoop[R, A](
    api: HttpCodec[R, A],
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
        (input, inputsBuilder) => inputsBuilder.routes(index) = input

      case IndexedAtom(_: Header[_], index) =>
        (input, inputsBuilder) => inputsBuilder.headers(index) = input

      case IndexedAtom(_: Query[_], index) =>
        (input, inputsBuilder) => inputsBuilder.queries(index) = input

      case IndexedAtom(_: Body[_], index) =>
        (input, inputsBuilder) => inputsBuilder.bodies(index) = input

      case IndexedAtom(_: Method[_], index) =>
        (input, inputsBuilder) => inputsBuilder.methods(index) = input

      case IndexedAtom(_: Status[_], index) =>
        (input, inputsBuilder) => inputsBuilder.statuses(index) = input

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

      case Empty => (_, _) => ()

      case atom: Atom[_, _] =>
        throw new RuntimeException(s"Atom $atom should have been wrapped in IndexedAtom")

      case Fallback(_, _) => throw new UnsupportedOperationException("Cannot handle fallback at this level")
    }
  }

  // Private Helper Classes

  private[api] final case class FlattenedAtoms(
    methods: Chunk[TextCodec[_]],
    routes: Chunk[TextCodec[_]],
    queries: Chunk[Query[_]],
    headers: Chunk[Header[_]],
    bodies: Chunk[internal.BodyCodec[_]],
    statuses: Chunk[TextCodec[_]],
  ) { self =>
    def append(atom: Atom[_, _]) = atom match {
      case route: Route[_]       => self.copy(routes = routes :+ route.textCodec)
      case method: Method[_]     => self.copy(methods = methods :+ method.methodCodec)
      case query: Query[_]       => self.copy(queries = queries :+ query)
      case header: Header[_]     => self.copy(headers = headers :+ header)
      case body: Body[_]         => self.copy(bodies = bodies :+ BodyCodec.Single(body.schema))
      case status: Status[_]     => self.copy(statuses = statuses :+ status.textCodec)
      case stream: BodyStream[_] => self.copy(bodies = bodies :+ BodyCodec.Multiple(stream.schema))
      case _: IndexedAtom[_, _]  => throw new RuntimeException("IndexedAtom should not be appended to FlattenedAtoms")
    }

    def makeInputsBuilder(): InputsBuilder = {
      Mechanic.InputsBuilder.make(
        routes.length,
        queries.length,
        headers.length,
        bodies.length,
        methods.length,
        statuses.length,
      )
    }
  }

  private[api] object FlattenedAtoms {
    val empty = FlattenedAtoms(Chunk.empty, Chunk.empty, Chunk.empty, Chunk.empty, Chunk.empty, Chunk.empty)
  }

  private[api] final case class InputsBuilder(
    routes: Array[Any],
    queries: Array[Any],
    headers: Array[Any],
    bodies: Array[Any],
    methods: Array[Any],
    statuses: Array[Any],
  ) { self => }
  private[api] object InputsBuilder  {
    def make(routes: Int, queries: Int, headers: Int, bodies: Int, methods: Int, statuses: Int): InputsBuilder =
      InputsBuilder(
        routes = new Array(routes),
        queries = new Array(queries),
        headers = new Array(headers),
        bodies = new Array(bodies),
        methods = new Array(methods),
        statuses = new Array(statuses),
      )
  }

  private final case class AtomIndices(
    method: Int = 0,
    route: Int = 0,
    query: Int = 0,
    header: Int = 0,
    inputBody: Int = 0,
    status: Int = 0,
  ) { self =>
    def increment(atom: Atom[_, _]): AtomIndices = {
      atom match {
        case _: Route[_]          => self.copy(route = route + 1)
        case _: Method[_]         => self.copy(method = method + 1)
        case _: Query[_]          => self.copy(query = query + 1)
        case _: Header[_]         => self.copy(header = header + 1)
        case _: Body[_]           => self.copy(inputBody = inputBody + 1)
        case _: Status[_]         => self.copy(status = status + 1)
        case _: BodyStream[_]     => self // TODO: Support body streams
        case _: IndexedAtom[_, _] => throw new RuntimeException("IndexedAtom should not be passed to increment")
      }
    }

    def get(atom: Atom[_, _]): Int =
      atom match {
        case _: Route[_]          => route
        case _: Query[_]          => query
        case _: Header[_]         => header
        case _: Body[_]           => inputBody
        case _: Method[_]         => method
        case _: Status[_]         => status
        case _: BodyStream[_]     => throw new RuntimeException("FIXME: Support BodyStream")
        case _: IndexedAtom[_, _] => throw new RuntimeException("IndexedAtom should not be passed to get")
      }
  }
}
