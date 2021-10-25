package zhttp.http.middleware

import zhttp.http.{Header, Method, URL}
import zio.ZIO

sealed trait RequestMiddleware[-R, +E, +S]

object RequestMiddleware {
  case class ConsM[R, E, S](f: (Method, URL, List[Header]) => ZIO[R, E, S]) extends RequestMiddleware[R, E, S]
  case class Cons[S](f: (Method, URL, List[Header]) => S)                   extends RequestMiddleware[Any, Nothing, S]
  case object Identity extends RequestMiddleware[Any, Nothing, Unit]

  def identity: RequestMiddleware[Any, Nothing, Unit] = Identity

  def makeM[R, E, S](f: (Method, URL, List[Header]) => ZIO[R, E, S]): RequestMiddleware[R, E, S] =
    ConsM(f)

  def make[R, E, S](f: (Method, URL, List[Header]) => S): RequestMiddleware[R, E, S] =
    Cons(f)

  def apply[R, E, S](f: (Method, URL, List[Header]) => S): RequestMiddleware[R, E, S] =
    make(f)
}
