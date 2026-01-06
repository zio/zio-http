package zio.http

import zio._

object RequestStore {

  private[http] val requestStore: FiberRef[Map[Tag[_], Any]] =
    FiberRef.unsafe.make[Map[Tag[_], Any]](Map.empty)(Unsafe.unsafe)

  def get[A: Tag]: UIO[Option[A]] =
    requestStore.get.map(_.get(implicitly[Tag[A]]).asInstanceOf[Option[A]])

  def getOrElse[A: Tag](orElse: => A): UIO[A] =
    get[A].map(_.getOrElse(orElse))

  def getOrFail[A: Tag]: ZIO[Any, NoSuchElementException, A] =
    get[A].flatMap {
      case Some(value) => Exit.succeed(value)
      case None        => Exit.fail(new NoSuchElementException(s"No value found for type ${implicitly[Tag[A]]}"))
    }

  def getMany[A: Tag, B: Tag]: UIO[(Option[A], Option[B])] =
    requestStore.get.map { store =>
      (
        store.get(implicitly[Tag[A]]).asInstanceOf[Option[A]],
        store.get(implicitly[Tag[B]]).asInstanceOf[Option[B]],
      )
    }

  def getMany[A: Tag, B: Tag, C: Tag]: UIO[(Option[A], Option[B], Option[C])] =
    requestStore.get.map { store =>
      (
        store.get(implicitly[Tag[A]]).asInstanceOf[Option[A]],
        store.get(implicitly[Tag[B]]).asInstanceOf[Option[B]],
        store.get(implicitly[Tag[C]]).asInstanceOf[Option[C]],
      )
    }

  def getMany[A: Tag, B: Tag, C: Tag, D: Tag]: UIO[(Option[A], Option[B], Option[C], Option[D])] =
    requestStore.get.map { store =>
      (
        store.get(implicitly[Tag[A]]).asInstanceOf[Option[A]],
        store.get(implicitly[Tag[B]]).asInstanceOf[Option[B]],
        store.get(implicitly[Tag[C]]).asInstanceOf[Option[C]],
        store.get(implicitly[Tag[D]]).asInstanceOf[Option[D]],
      )
    }

  def getMany[A: Tag, B: Tag, C: Tag, D: Tag, E: Tag]: UIO[(Option[A], Option[B], Option[C], Option[D], Option[E])] =
    requestStore.get.map { store =>
      (
        store.get(implicitly[Tag[A]]).asInstanceOf[Option[A]],
        store.get(implicitly[Tag[B]]).asInstanceOf[Option[B]],
        store.get(implicitly[Tag[C]]).asInstanceOf[Option[C]],
        store.get(implicitly[Tag[D]]).asInstanceOf[Option[D]],
        store.get(implicitly[Tag[E]]).asInstanceOf[Option[E]],
      )
    }

  def getMany[A: Tag, B: Tag, C: Tag, D: Tag, E: Tag, F: Tag]
    : UIO[(Option[A], Option[B], Option[C], Option[D], Option[E], Option[F])] =
    requestStore.get.map { store =>
      (
        store.get(implicitly[Tag[A]]).asInstanceOf[Option[A]],
        store.get(implicitly[Tag[B]]).asInstanceOf[Option[B]],
        store.get(implicitly[Tag[C]]).asInstanceOf[Option[C]],
        store.get(implicitly[Tag[D]]).asInstanceOf[Option[D]],
        store.get(implicitly[Tag[E]]).asInstanceOf[Option[E]],
        store.get(implicitly[Tag[F]]).asInstanceOf[Option[F]],
      )
    }

  def getMany[A: Tag, B: Tag, C: Tag, D: Tag, E: Tag, F: Tag, G: Tag]
    : UIO[(Option[A], Option[B], Option[C], Option[D], Option[E], Option[F], Option[G])] =
    requestStore.get.map { store =>
      (
        store.get(implicitly[Tag[A]]).asInstanceOf[Option[A]],
        store.get(implicitly[Tag[B]]).asInstanceOf[Option[B]],
        store.get(implicitly[Tag[C]]).asInstanceOf[Option[C]],
        store.get(implicitly[Tag[D]]).asInstanceOf[Option[D]],
        store.get(implicitly[Tag[E]]).asInstanceOf[Option[E]],
        store.get(implicitly[Tag[F]]).asInstanceOf[Option[F]],
        store.get(implicitly[Tag[G]]).asInstanceOf[Option[G]],
      )
    }

  def getMany[A: Tag, B: Tag, C: Tag, D: Tag, E: Tag, F: Tag, G: Tag, H: Tag]
    : UIO[(Option[A], Option[B], Option[C], Option[D], Option[E], Option[F], Option[G], Option[H])] =
    requestStore.get.map { store =>
      (
        store.get(implicitly[Tag[A]]).asInstanceOf[Option[A]],
        store.get(implicitly[Tag[B]]).asInstanceOf[Option[B]],
        store.get(implicitly[Tag[C]]).asInstanceOf[Option[C]],
        store.get(implicitly[Tag[D]]).asInstanceOf[Option[D]],
        store.get(implicitly[Tag[E]]).asInstanceOf[Option[E]],
        store.get(implicitly[Tag[F]]).asInstanceOf[Option[F]],
        store.get(implicitly[Tag[G]]).asInstanceOf[Option[G]],
        store.get(implicitly[Tag[H]]).asInstanceOf[Option[H]],
      )
    }

  def getMany[A: Tag, B: Tag, C: Tag, D: Tag, E: Tag, F: Tag, G: Tag, H: Tag, I: Tag]
    : UIO[(Option[A], Option[B], Option[C], Option[D], Option[E], Option[F], Option[G], Option[H], Option[I])] =
    requestStore.get.map { store =>
      (
        store.get(implicitly[Tag[A]]).asInstanceOf[Option[A]],
        store.get(implicitly[Tag[B]]).asInstanceOf[Option[B]],
        store.get(implicitly[Tag[C]]).asInstanceOf[Option[C]],
        store.get(implicitly[Tag[D]]).asInstanceOf[Option[D]],
        store.get(implicitly[Tag[E]]).asInstanceOf[Option[E]],
        store.get(implicitly[Tag[F]]).asInstanceOf[Option[F]],
        store.get(implicitly[Tag[G]]).asInstanceOf[Option[G]],
        store.get(implicitly[Tag[H]]).asInstanceOf[Option[H]],
        store.get(implicitly[Tag[I]]).asInstanceOf[Option[I]],
      )
    }

  def getMany[A: Tag, B: Tag, C: Tag, D: Tag, E: Tag, F: Tag, G: Tag, H: Tag, I: Tag, J: Tag]: UIO[
    (Option[A], Option[B], Option[C], Option[D], Option[E], Option[F], Option[G], Option[H], Option[I], Option[J]),
  ] =
    requestStore.get.map { store =>
      (
        store.get(implicitly[Tag[A]]).asInstanceOf[Option[A]],
        store.get(implicitly[Tag[B]]).asInstanceOf[Option[B]],
        store.get(implicitly[Tag[C]]).asInstanceOf[Option[C]],
        store.get(implicitly[Tag[D]]).asInstanceOf[Option[D]],
        store.get(implicitly[Tag[E]]).asInstanceOf[Option[E]],
        store.get(implicitly[Tag[F]]).asInstanceOf[Option[F]],
        store.get(implicitly[Tag[G]]).asInstanceOf[Option[G]],
        store.get(implicitly[Tag[H]]).asInstanceOf[Option[H]],
        store.get(implicitly[Tag[I]]).asInstanceOf[Option[I]],
        store.get(implicitly[Tag[J]]).asInstanceOf[Option[J]],
      )
    }

  def getMany[A: Tag, B: Tag, C: Tag, D: Tag, E: Tag, F: Tag, G: Tag, H: Tag, I: Tag, J: Tag, K: Tag]: UIO[
    (
      Option[A],
      Option[B],
      Option[C],
      Option[D],
      Option[E],
      Option[F],
      Option[G],
      Option[H],
      Option[I],
      Option[J],
      Option[K],
    ),
  ] =
    requestStore.get.map { store =>
      (
        store.get(implicitly[Tag[A]]).asInstanceOf[Option[A]],
        store.get(implicitly[Tag[B]]).asInstanceOf[Option[B]],
        store.get(implicitly[Tag[C]]).asInstanceOf[Option[C]],
        store.get(implicitly[Tag[D]]).asInstanceOf[Option[D]],
        store.get(implicitly[Tag[E]]).asInstanceOf[Option[E]],
        store.get(implicitly[Tag[F]]).asInstanceOf[Option[F]],
        store.get(implicitly[Tag[G]]).asInstanceOf[Option[G]],
        store.get(implicitly[Tag[H]]).asInstanceOf[Option[H]],
        store.get(implicitly[Tag[I]]).asInstanceOf[Option[I]],
        store.get(implicitly[Tag[J]]).asInstanceOf[Option[J]],
        store.get(implicitly[Tag[K]]).asInstanceOf[Option[K]],
      )
    }

  def getMany[A: Tag, B: Tag, C: Tag, D: Tag, E: Tag, F: Tag, G: Tag, H: Tag, I: Tag, J: Tag, K: Tag, L: Tag]: UIO[
    (
      Option[A],
      Option[B],
      Option[C],
      Option[D],
      Option[E],
      Option[F],
      Option[G],
      Option[H],
      Option[I],
      Option[J],
      Option[K],
      Option[L],
    ),
  ] =
    requestStore.get.map { store =>
      (
        store.get(implicitly[Tag[A]]).asInstanceOf[Option[A]],
        store.get(implicitly[Tag[B]]).asInstanceOf[Option[B]],
        store.get(implicitly[Tag[C]]).asInstanceOf[Option[C]],
        store.get(implicitly[Tag[D]]).asInstanceOf[Option[D]],
        store.get(implicitly[Tag[E]]).asInstanceOf[Option[E]],
        store.get(implicitly[Tag[F]]).asInstanceOf[Option[F]],
        store.get(implicitly[Tag[G]]).asInstanceOf[Option[G]],
        store.get(implicitly[Tag[H]]).asInstanceOf[Option[H]],
        store.get(implicitly[Tag[I]]).asInstanceOf[Option[I]],
        store.get(implicitly[Tag[J]]).asInstanceOf[Option[J]],
        store.get(implicitly[Tag[K]]).asInstanceOf[Option[K]],
        store.get(implicitly[Tag[L]]).asInstanceOf[Option[L]],
      )
    }

  def getMany[A: Tag, B: Tag, C: Tag, D: Tag, E: Tag, F: Tag, G: Tag, H: Tag, I: Tag, J: Tag, K: Tag, L: Tag, M: Tag]
    : UIO[
      (
        Option[A],
        Option[B],
        Option[C],
        Option[D],
        Option[E],
        Option[F],
        Option[G],
        Option[H],
        Option[I],
        Option[J],
        Option[K],
        Option[L],
        Option[M],
      ),
    ] =
    requestStore.get.map { store =>
      (
        store.get(implicitly[Tag[A]]).asInstanceOf[Option[A]],
        store.get(implicitly[Tag[B]]).asInstanceOf[Option[B]],
        store.get(implicitly[Tag[C]]).asInstanceOf[Option[C]],
        store.get(implicitly[Tag[D]]).asInstanceOf[Option[D]],
        store.get(implicitly[Tag[E]]).asInstanceOf[Option[E]],
        store.get(implicitly[Tag[F]]).asInstanceOf[Option[F]],
        store.get(implicitly[Tag[G]]).asInstanceOf[Option[G]],
        store.get(implicitly[Tag[H]]).asInstanceOf[Option[H]],
        store.get(implicitly[Tag[I]]).asInstanceOf[Option[I]],
        store.get(implicitly[Tag[J]]).asInstanceOf[Option[J]],
        store.get(implicitly[Tag[K]]).asInstanceOf[Option[K]],
        store.get(implicitly[Tag[L]]).asInstanceOf[Option[L]],
        store.get(implicitly[Tag[M]]).asInstanceOf[Option[M]],
      )
    }

  def getMany[
    A: Tag,
    B: Tag,
    C: Tag,
    D: Tag,
    E: Tag,
    F: Tag,
    G: Tag,
    H: Tag,
    I: Tag,
    J: Tag,
    K: Tag,
    L: Tag,
    M: Tag,
    N: Tag,
  ]: UIO[
    (
      Option[A],
      Option[B],
      Option[C],
      Option[D],
      Option[E],
      Option[F],
      Option[G],
      Option[H],
      Option[I],
      Option[J],
      Option[K],
      Option[L],
      Option[M],
      Option[N],
    ),
  ] =
    requestStore.get.map { store =>
      (
        store.get(implicitly[Tag[A]]).asInstanceOf[Option[A]],
        store.get(implicitly[Tag[B]]).asInstanceOf[Option[B]],
        store.get(implicitly[Tag[C]]).asInstanceOf[Option[C]],
        store.get(implicitly[Tag[D]]).asInstanceOf[Option[D]],
        store.get(implicitly[Tag[E]]).asInstanceOf[Option[E]],
        store.get(implicitly[Tag[F]]).asInstanceOf[Option[F]],
        store.get(implicitly[Tag[G]]).asInstanceOf[Option[G]],
        store.get(implicitly[Tag[H]]).asInstanceOf[Option[H]],
        store.get(implicitly[Tag[I]]).asInstanceOf[Option[I]],
        store.get(implicitly[Tag[J]]).asInstanceOf[Option[J]],
        store.get(implicitly[Tag[K]]).asInstanceOf[Option[K]],
        store.get(implicitly[Tag[L]]).asInstanceOf[Option[L]],
        store.get(implicitly[Tag[M]]).asInstanceOf[Option[M]],
        store.get(implicitly[Tag[N]]).asInstanceOf[Option[N]],
      )
    }

  def getMany[
    A: Tag,
    B: Tag,
    C: Tag,
    D: Tag,
    E: Tag,
    F: Tag,
    G: Tag,
    H: Tag,
    I: Tag,
    J: Tag,
    K: Tag,
    L: Tag,
    M: Tag,
    N: Tag,
    O: Tag,
  ]: UIO[
    (
      Option[A],
      Option[B],
      Option[C],
      Option[D],
      Option[E],
      Option[F],
      Option[G],
      Option[H],
      Option[I],
      Option[J],
      Option[K],
      Option[L],
      Option[M],
      Option[N],
      Option[O],
    ),
  ] =
    requestStore.get.map { store =>
      (
        store.get(implicitly[Tag[A]]).asInstanceOf[Option[A]],
        store.get(implicitly[Tag[B]]).asInstanceOf[Option[B]],
        store.get(implicitly[Tag[C]]).asInstanceOf[Option[C]],
        store.get(implicitly[Tag[D]]).asInstanceOf[Option[D]],
        store.get(implicitly[Tag[E]]).asInstanceOf[Option[E]],
        store.get(implicitly[Tag[F]]).asInstanceOf[Option[F]],
        store.get(implicitly[Tag[G]]).asInstanceOf[Option[G]],
        store.get(implicitly[Tag[H]]).asInstanceOf[Option[H]],
        store.get(implicitly[Tag[I]]).asInstanceOf[Option[I]],
        store.get(implicitly[Tag[J]]).asInstanceOf[Option[J]],
        store.get(implicitly[Tag[K]]).asInstanceOf[Option[K]],
        store.get(implicitly[Tag[L]]).asInstanceOf[Option[L]],
        store.get(implicitly[Tag[M]]).asInstanceOf[Option[M]],
        store.get(implicitly[Tag[N]]).asInstanceOf[Option[N]],
        store.get(implicitly[Tag[O]]).asInstanceOf[Option[O]],
      )
    }

  def getMany[
    A: Tag,
    B: Tag,
    C: Tag,
    D: Tag,
    E: Tag,
    F: Tag,
    G: Tag,
    H: Tag,
    I: Tag,
    J: Tag,
    K: Tag,
    L: Tag,
    M: Tag,
    N: Tag,
    O: Tag,
    P: Tag,
  ]: UIO[
    (
      Option[A],
      Option[B],
      Option[C],
      Option[D],
      Option[E],
      Option[F],
      Option[G],
      Option[H],
      Option[I],
      Option[J],
      Option[K],
      Option[L],
      Option[M],
      Option[N],
      Option[O],
      Option[P],
    ),
  ] =
    requestStore.get.map { store =>
      (
        store.get(implicitly[Tag[A]]).asInstanceOf[Option[A]],
        store.get(implicitly[Tag[B]]).asInstanceOf[Option[B]],
        store.get(implicitly[Tag[C]]).asInstanceOf[Option[C]],
        store.get(implicitly[Tag[D]]).asInstanceOf[Option[D]],
        store.get(implicitly[Tag[E]]).asInstanceOf[Option[E]],
        store.get(implicitly[Tag[F]]).asInstanceOf[Option[F]],
        store.get(implicitly[Tag[G]]).asInstanceOf[Option[G]],
        store.get(implicitly[Tag[H]]).asInstanceOf[Option[H]],
        store.get(implicitly[Tag[I]]).asInstanceOf[Option[I]],
        store.get(implicitly[Tag[J]]).asInstanceOf[Option[J]],
        store.get(implicitly[Tag[K]]).asInstanceOf[Option[K]],
        store.get(implicitly[Tag[L]]).asInstanceOf[Option[L]],
        store.get(implicitly[Tag[M]]).asInstanceOf[Option[M]],
        store.get(implicitly[Tag[N]]).asInstanceOf[Option[N]],
        store.get(implicitly[Tag[O]]).asInstanceOf[Option[O]],
        store.get(implicitly[Tag[P]]).asInstanceOf[Option[P]],
      )
    }

  def getMany[
    A: Tag,
    B: Tag,
    C: Tag,
    D: Tag,
    E: Tag,
    F: Tag,
    G: Tag,
    H: Tag,
    I: Tag,
    J: Tag,
    K: Tag,
    L: Tag,
    M: Tag,
    N: Tag,
    O: Tag,
    P: Tag,
    Q: Tag,
  ]: UIO[
    (
      Option[A],
      Option[B],
      Option[C],
      Option[D],
      Option[E],
      Option[F],
      Option[G],
      Option[H],
      Option[I],
      Option[J],
      Option[K],
      Option[L],
      Option[M],
      Option[N],
      Option[O],
      Option[P],
      Option[Q],
    ),
  ] =
    requestStore.get.map { store =>
      (
        store.get(implicitly[Tag[A]]).asInstanceOf[Option[A]],
        store.get(implicitly[Tag[B]]).asInstanceOf[Option[B]],
        store.get(implicitly[Tag[C]]).asInstanceOf[Option[C]],
        store.get(implicitly[Tag[D]]).asInstanceOf[Option[D]],
        store.get(implicitly[Tag[E]]).asInstanceOf[Option[E]],
        store.get(implicitly[Tag[F]]).asInstanceOf[Option[F]],
        store.get(implicitly[Tag[G]]).asInstanceOf[Option[G]],
        store.get(implicitly[Tag[H]]).asInstanceOf[Option[H]],
        store.get(implicitly[Tag[I]]).asInstanceOf[Option[I]],
        store.get(implicitly[Tag[J]]).asInstanceOf[Option[J]],
        store.get(implicitly[Tag[K]]).asInstanceOf[Option[K]],
        store.get(implicitly[Tag[L]]).asInstanceOf[Option[L]],
        store.get(implicitly[Tag[M]]).asInstanceOf[Option[M]],
        store.get(implicitly[Tag[N]]).asInstanceOf[Option[N]],
        store.get(implicitly[Tag[O]]).asInstanceOf[Option[O]],
        store.get(implicitly[Tag[P]]).asInstanceOf[Option[P]],
        store.get(implicitly[Tag[Q]]).asInstanceOf[Option[Q]],
      )
    }

  def getMany[
    A: Tag,
    B: Tag,
    C: Tag,
    D: Tag,
    E: Tag,
    F: Tag,
    G: Tag,
    H: Tag,
    I: Tag,
    J: Tag,
    K: Tag,
    L: Tag,
    M: Tag,
    N: Tag,
    O: Tag,
    P: Tag,
    Q: Tag,
    R: Tag,
  ]: UIO[
    (
      Option[A],
      Option[B],
      Option[C],
      Option[D],
      Option[E],
      Option[F],
      Option[G],
      Option[H],
      Option[I],
      Option[J],
      Option[K],
      Option[L],
      Option[M],
      Option[N],
      Option[O],
      Option[P],
      Option[Q],
      Option[R],
    ),
  ] =
    requestStore.get.map { store =>
      (
        store.get(implicitly[Tag[A]]).asInstanceOf[Option[A]],
        store.get(implicitly[Tag[B]]).asInstanceOf[Option[B]],
        store.get(implicitly[Tag[C]]).asInstanceOf[Option[C]],
        store.get(implicitly[Tag[D]]).asInstanceOf[Option[D]],
        store.get(implicitly[Tag[E]]).asInstanceOf[Option[E]],
        store.get(implicitly[Tag[F]]).asInstanceOf[Option[F]],
        store.get(implicitly[Tag[G]]).asInstanceOf[Option[G]],
        store.get(implicitly[Tag[H]]).asInstanceOf[Option[H]],
        store.get(implicitly[Tag[I]]).asInstanceOf[Option[I]],
        store.get(implicitly[Tag[J]]).asInstanceOf[Option[J]],
        store.get(implicitly[Tag[K]]).asInstanceOf[Option[K]],
        store.get(implicitly[Tag[L]]).asInstanceOf[Option[L]],
        store.get(implicitly[Tag[M]]).asInstanceOf[Option[M]],
        store.get(implicitly[Tag[N]]).asInstanceOf[Option[N]],
        store.get(implicitly[Tag[O]]).asInstanceOf[Option[O]],
        store.get(implicitly[Tag[P]]).asInstanceOf[Option[P]],
        store.get(implicitly[Tag[Q]]).asInstanceOf[Option[Q]],
        store.get(implicitly[Tag[R]]).asInstanceOf[Option[R]],
      )
    }

  def getMany[
    A: Tag,
    B: Tag,
    C: Tag,
    D: Tag,
    E: Tag,
    F: Tag,
    G: Tag,
    H: Tag,
    I: Tag,
    J: Tag,
    K: Tag,
    L: Tag,
    M: Tag,
    N: Tag,
    O: Tag,
    P: Tag,
    Q: Tag,
    R: Tag,
    S: Tag,
  ]: UIO[
    (
      Option[A],
      Option[B],
      Option[C],
      Option[D],
      Option[E],
      Option[F],
      Option[G],
      Option[H],
      Option[I],
      Option[J],
      Option[K],
      Option[L],
      Option[M],
      Option[N],
      Option[O],
      Option[P],
      Option[Q],
      Option[R],
      Option[S],
    ),
  ] =
    requestStore.get.map { store =>
      (
        store.get(implicitly[Tag[A]]).asInstanceOf[Option[A]],
        store.get(implicitly[Tag[B]]).asInstanceOf[Option[B]],
        store.get(implicitly[Tag[C]]).asInstanceOf[Option[C]],
        store.get(implicitly[Tag[D]]).asInstanceOf[Option[D]],
        store.get(implicitly[Tag[E]]).asInstanceOf[Option[E]],
        store.get(implicitly[Tag[F]]).asInstanceOf[Option[F]],
        store.get(implicitly[Tag[G]]).asInstanceOf[Option[G]],
        store.get(implicitly[Tag[H]]).asInstanceOf[Option[H]],
        store.get(implicitly[Tag[I]]).asInstanceOf[Option[I]],
        store.get(implicitly[Tag[J]]).asInstanceOf[Option[J]],
        store.get(implicitly[Tag[K]]).asInstanceOf[Option[K]],
        store.get(implicitly[Tag[L]]).asInstanceOf[Option[L]],
        store.get(implicitly[Tag[M]]).asInstanceOf[Option[M]],
        store.get(implicitly[Tag[N]]).asInstanceOf[Option[N]],
        store.get(implicitly[Tag[O]]).asInstanceOf[Option[O]],
        store.get(implicitly[Tag[P]]).asInstanceOf[Option[P]],
        store.get(implicitly[Tag[Q]]).asInstanceOf[Option[Q]],
        store.get(implicitly[Tag[R]]).asInstanceOf[Option[R]],
        store.get(implicitly[Tag[S]]).asInstanceOf[Option[S]],
      )
    }

  def getMany[
    A: Tag,
    B: Tag,
    C: Tag,
    D: Tag,
    E: Tag,
    F: Tag,
    G: Tag,
    H: Tag,
    I: Tag,
    J: Tag,
    K: Tag,
    L: Tag,
    M: Tag,
    N: Tag,
    O: Tag,
    P: Tag,
    Q: Tag,
    R: Tag,
    S: Tag,
    T: Tag,
  ]: UIO[
    (
      Option[A],
      Option[B],
      Option[C],
      Option[D],
      Option[E],
      Option[F],
      Option[G],
      Option[H],
      Option[I],
      Option[J],
      Option[K],
      Option[L],
      Option[M],
      Option[N],
      Option[O],
      Option[P],
      Option[Q],
      Option[R],
      Option[S],
      Option[T],
    ),
  ] =
    requestStore.get.map { store =>
      (
        store.get(implicitly[Tag[A]]).asInstanceOf[Option[A]],
        store.get(implicitly[Tag[B]]).asInstanceOf[Option[B]],
        store.get(implicitly[Tag[C]]).asInstanceOf[Option[C]],
        store.get(implicitly[Tag[D]]).asInstanceOf[Option[D]],
        store.get(implicitly[Tag[E]]).asInstanceOf[Option[E]],
        store.get(implicitly[Tag[F]]).asInstanceOf[Option[F]],
        store.get(implicitly[Tag[G]]).asInstanceOf[Option[G]],
        store.get(implicitly[Tag[H]]).asInstanceOf[Option[H]],
        store.get(implicitly[Tag[I]]).asInstanceOf[Option[I]],
        store.get(implicitly[Tag[J]]).asInstanceOf[Option[J]],
        store.get(implicitly[Tag[K]]).asInstanceOf[Option[K]],
        store.get(implicitly[Tag[L]]).asInstanceOf[Option[L]],
        store.get(implicitly[Tag[M]]).asInstanceOf[Option[M]],
        store.get(implicitly[Tag[N]]).asInstanceOf[Option[N]],
        store.get(implicitly[Tag[O]]).asInstanceOf[Option[O]],
        store.get(implicitly[Tag[P]]).asInstanceOf[Option[P]],
        store.get(implicitly[Tag[Q]]).asInstanceOf[Option[Q]],
        store.get(implicitly[Tag[R]]).asInstanceOf[Option[R]],
        store.get(implicitly[Tag[S]]).asInstanceOf[Option[S]],
        store.get(implicitly[Tag[T]]).asInstanceOf[Option[T]],
      )
    }

  def getMany[
    A: Tag,
    B: Tag,
    C: Tag,
    D: Tag,
    E: Tag,
    F: Tag,
    G: Tag,
    H: Tag,
    I: Tag,
    J: Tag,
    K: Tag,
    L: Tag,
    M: Tag,
    N: Tag,
    O: Tag,
    P: Tag,
    Q: Tag,
    R: Tag,
    S: Tag,
    T: Tag,
    U: Tag,
  ]: UIO[
    (
      Option[A],
      Option[B],
      Option[C],
      Option[D],
      Option[E],
      Option[F],
      Option[G],
      Option[H],
      Option[I],
      Option[J],
      Option[K],
      Option[L],
      Option[M],
      Option[N],
      Option[O],
      Option[P],
      Option[Q],
      Option[R],
      Option[S],
      Option[T],
      Option[U],
    ),
  ] =
    requestStore.get.map { store =>
      (
        store.get(implicitly[Tag[A]]).asInstanceOf[Option[A]],
        store.get(implicitly[Tag[B]]).asInstanceOf[Option[B]],
        store.get(implicitly[Tag[C]]).asInstanceOf[Option[C]],
        store.get(implicitly[Tag[D]]).asInstanceOf[Option[D]],
        store.get(implicitly[Tag[E]]).asInstanceOf[Option[E]],
        store.get(implicitly[Tag[F]]).asInstanceOf[Option[F]],
        store.get(implicitly[Tag[G]]).asInstanceOf[Option[G]],
        store.get(implicitly[Tag[H]]).asInstanceOf[Option[H]],
        store.get(implicitly[Tag[I]]).asInstanceOf[Option[I]],
        store.get(implicitly[Tag[J]]).asInstanceOf[Option[J]],
        store.get(implicitly[Tag[K]]).asInstanceOf[Option[K]],
        store.get(implicitly[Tag[L]]).asInstanceOf[Option[L]],
        store.get(implicitly[Tag[M]]).asInstanceOf[Option[M]],
        store.get(implicitly[Tag[N]]).asInstanceOf[Option[N]],
        store.get(implicitly[Tag[O]]).asInstanceOf[Option[O]],
        store.get(implicitly[Tag[P]]).asInstanceOf[Option[P]],
        store.get(implicitly[Tag[Q]]).asInstanceOf[Option[Q]],
        store.get(implicitly[Tag[R]]).asInstanceOf[Option[R]],
        store.get(implicitly[Tag[S]]).asInstanceOf[Option[S]],
        store.get(implicitly[Tag[T]]).asInstanceOf[Option[T]],
        store.get(implicitly[Tag[U]]).asInstanceOf[Option[U]],
      )
    }

  def getMany[
    A: Tag,
    B: Tag,
    C: Tag,
    D: Tag,
    E: Tag,
    F: Tag,
    G: Tag,
    H: Tag,
    I: Tag,
    J: Tag,
    K: Tag,
    L: Tag,
    M: Tag,
    N: Tag,
    O: Tag,
    P: Tag,
    Q: Tag,
    R: Tag,
    S: Tag,
    T: Tag,
    U: Tag,
    V: Tag,
  ]: UIO[
    (
      Option[A],
      Option[B],
      Option[C],
      Option[D],
      Option[E],
      Option[F],
      Option[G],
      Option[H],
      Option[I],
      Option[J],
      Option[K],
      Option[L],
      Option[M],
      Option[N],
      Option[O],
      Option[P],
      Option[Q],
      Option[R],
      Option[S],
      Option[T],
      Option[U],
      Option[V],
    ),
  ] =
    requestStore.get.map { store =>
      (
        store.get(implicitly[Tag[A]]).asInstanceOf[Option[A]],
        store.get(implicitly[Tag[B]]).asInstanceOf[Option[B]],
        store.get(implicitly[Tag[C]]).asInstanceOf[Option[C]],
        store.get(implicitly[Tag[D]]).asInstanceOf[Option[D]],
        store.get(implicitly[Tag[E]]).asInstanceOf[Option[E]],
        store.get(implicitly[Tag[F]]).asInstanceOf[Option[F]],
        store.get(implicitly[Tag[G]]).asInstanceOf[Option[G]],
        store.get(implicitly[Tag[H]]).asInstanceOf[Option[H]],
        store.get(implicitly[Tag[I]]).asInstanceOf[Option[I]],
        store.get(implicitly[Tag[J]]).asInstanceOf[Option[J]],
        store.get(implicitly[Tag[K]]).asInstanceOf[Option[K]],
        store.get(implicitly[Tag[L]]).asInstanceOf[Option[L]],
        store.get(implicitly[Tag[M]]).asInstanceOf[Option[M]],
        store.get(implicitly[Tag[N]]).asInstanceOf[Option[N]],
        store.get(implicitly[Tag[O]]).asInstanceOf[Option[O]],
        store.get(implicitly[Tag[P]]).asInstanceOf[Option[P]],
        store.get(implicitly[Tag[Q]]).asInstanceOf[Option[Q]],
        store.get(implicitly[Tag[R]]).asInstanceOf[Option[R]],
        store.get(implicitly[Tag[S]]).asInstanceOf[Option[S]],
        store.get(implicitly[Tag[T]]).asInstanceOf[Option[T]],
        store.get(implicitly[Tag[U]]).asInstanceOf[Option[U]],
        store.get(implicitly[Tag[V]]).asInstanceOf[Option[V]],
      )
    }

  def set[A: Tag](a: A): UIO[Unit] =
    requestStore.update(_.updated(implicitly[Tag[A]], a))

  def update[A: Tag](a: Option[A] => A): UIO[Unit] =
    for {
      current <- get[A]
      _       <- set(a(current))
    } yield ()

  def storeRequest: HandlerAspect[Any, Unit] =
    Middleware.interceptIncomingHandler(handler((request: Request) => set(request).as((request, ()))))

  def getRequest: UIO[Option[Request]] =
    get[Request]

  def getRequestOrFail: ZIO[Any, NoSuchElementException, Request] =
    getOrFail[Request]
}
