package zio.web.http

sealed trait HttpResponse[+A] { self =>
  def <>[A1 >: A](that: HttpResponse[A1]): HttpResponse[A1] = self.orElse(that)

  def map[B](f: A => B): HttpResponse[B] = HttpResponse.Map(self, f)

  def orElse[A1 >: A](that: HttpResponse[A1]): HttpResponse[A1] =
    self.orElseEither(that).map(_.merge)

  def orElseEither[B](that: HttpResponse[B]): HttpResponse[Either[A, B]] = HttpResponse.OrElseEither(self, that)

  def run(statusCode: Int, headers: HttpHeaders): Option[A]

  def zip[B](that: HttpResponse[B]): HttpResponse[(A, B)] = HttpResponse.Zip(self, that)

  def zipWith[B, C](that: HttpResponse[B])(f: (A, B) => C): HttpResponse[C] = self.zip(that).map(f.tupled)
}

object HttpResponse {
  case object Succeed extends HttpResponse[Unit] {
    def run(statusCode: Int, headers: HttpHeaders): Option[Unit] = Some(())
  }

  case object Fail extends HttpResponse[Nothing] {
    def run(statusCode: Int, headers: HttpHeaders): Option[Nothing] = None
  }

  final case object StatusCode extends HttpResponse[Int] {
    def run(statusCode: Int, headers: HttpHeaders): Option[Int] = Some(statusCode)
  }

  final case class Header(name: String) extends HttpResponse[String] {
    def run(statusCode: Int, headers: HttpHeaders): Option[String] = headers.value.get(name)
  }

  final case class Map[A, B](response: HttpResponse[A], f: A => B) extends HttpResponse[B] {
    def run(statusCode: Int, headers: HttpHeaders): Option[B] = response.run(statusCode, headers).map(f)
  }

  final case class OrElseEither[A, B](left: HttpResponse[A], right: HttpResponse[B])
      extends HttpResponse[Either[A, B]] {

    def run(statusCode: Int, headers: HttpHeaders): Option[Either[A, B]] = {
      val l = left.run(statusCode, headers)
      val r = right.run(statusCode, headers)
      l.map(Left(_)).orElse(r.map(Right(_)))
    }
  }

  final case class Zip[A, B](left: HttpResponse[A], right: HttpResponse[B]) extends HttpResponse[(A, B)] {

    def run(statusCode: Int, headers: HttpHeaders): Option[(A, B)] =
      for {
        l <- left.run(statusCode, headers)
        r <- right.run(statusCode, headers)
      } yield (l, r)
  }
}
