package zhttp.http.query
import scala.annotation.tailrec

object EitherUtil {

  def forEach[A, B](list: Iterable[A])(f: A => Either[String, B]): Either[String, Iterable[B]] = {
    @tailrec
    def loop[A2, B2](xs: Iterable[A2], acc: List[B2])(f: A2 => Either[String, B2]): Either[String, Iterable[B2]] =
      xs match {
        case head :: tail =>
          f(head) match {
            case Left(e)  => Left(e)
            case Right(a) => loop(tail, a :: acc)(f)
          }
        case Nil          => Right(acc.reverse)
        case _            => Right(acc.reverse)
      }

    loop(list.toList, List.empty)(f)
  }
}
