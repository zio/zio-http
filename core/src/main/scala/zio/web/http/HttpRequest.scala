package zio.web.http

import java.net.URI

sealed trait HttpRequest[+A] { self =>
  def <>[A1 >: A](that: HttpRequest[A1]): HttpRequest[A1] = self.orElse(that)

  def map[B](f: A => B): HttpRequest[B] = HttpRequest.Map(self, f)

  def orElse[A1 >: A](that: HttpRequest[A1]): HttpRequest[A1] =
    self.orElseEither(that).map(_.merge)

  def orElseEither[B](that: HttpRequest[B]): HttpRequest[Either[A, B]] = HttpRequest.OrElseEither(self, that)

  def run(method: String, uri: java.net.URI, headers: HttpHeaders): Option[A]

  def zip[B](that: HttpRequest[B]): HttpRequest[(A, B)] = HttpRequest.Zip(self, that)

  def zipWith[B, C](that: HttpRequest[B])(f: (A, B) => C): HttpRequest[C] = self.zip(that).map(f.tupled)
}

object HttpRequest {
  case object Succeed extends HttpRequest[Unit] {
    def run(method: String, uri: java.net.URI, headers: HttpHeaders): Option[Unit] = Some(())
  }

  case object Fail extends HttpRequest[Nothing] {
    def run(method: String, uri: java.net.URI, headers: HttpHeaders): Option[Nothing] = None
  }

  case object Method extends HttpRequest[String] {
    def run(method: String, uri: java.net.URI, headers: HttpHeaders): Option[String] = Some(method)
  }

  final case class Header(name: String) extends HttpRequest[String] {

    def run(method: String, uri: java.net.URI, headers: HttpHeaders): Option[String] =
      headers.value.get(name)
  }

  final case object URI extends HttpRequest[java.net.URI] {
    def run(method: String, uri: java.net.URI, headers: HttpHeaders): Option[URI] = Some(uri)
  }

  final case class Map[A, B](request: HttpRequest[A], f: A => B) extends HttpRequest[B] {

    def run(method: String, uri: java.net.URI, headers: HttpHeaders): Option[B] =
      request.run(method, uri, headers).map(f)
  }

  final case class OrElseEither[A, B](left: HttpRequest[A], right: HttpRequest[B]) extends HttpRequest[Either[A, B]] {

    def run(method: String, uri: java.net.URI, headers: HttpHeaders): Option[Either[A, B]] = {
      val l = left.run(method, uri, headers)
      val r = right.run(method, uri, headers)
      l.map(Left(_)).orElse(r.map(Right(_)))
    }
  }

  final case class Zip[A, B](left: HttpRequest[A], right: HttpRequest[B]) extends HttpRequest[(A, B)] {

    def run(method: String, uri: java.net.URI, headers: HttpHeaders): Option[(A, B)] =
      for {
        l <- left.run(method, uri, headers)
        r <- right.run(method, uri, headers)
      } yield (l, r)
  }
}
