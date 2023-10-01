package zio.http

import zio.schema.Schema
import zio.schema.codec.JsonCodec

import java.net.{URLDecoder, URLEncoder}

sealed trait Flash[+A] { self =>

  def flatMap[B](f: A => Flash[B]): Flash[B] = Flash.FlatMap(self, f)

  def map[B](f: A => B): Flash[B] = self.flatMap(a => Flash.succeed(f(a)))

  def orElse[B >: A](that: => Flash[B]): Flash[B] = Flash.OrElse(self, that)

  def <>[B >: A](that: => Flash[B]): Flash[B] = self.orElse(that)

  def zip[B](that: => Flash[B]): Flash[(A, B)] = self.zipWith(that)((a, b) => a -> b)

  def <*>[B](that: => Flash[B]): Flash[(A, B)] = self.zip(that)

  def zipWith[B, C](that: => Flash[B])(f: (A, B) => C): Flash[C] =
    self.flatMap(a => that.map(b => f(a, b)))

  def optional: Flash[Option[A]] = self.map(Option(_)) <> Flash.succeed(None)
}

object Flash {

  sealed trait Setter[A] { self =>
    def ++[B](that: => Setter[B]): Setter[(A, B)] = Setter.Concat(self, that)
  }

  private[http] object Setter {
    case class Set[A](schema: Schema[A], key: String, a: A) extends Flash.Setter[A]

    case class Concat[A, B](left: Setter[A], right: Setter[B]) extends Flash.Setter[(A, B)]

    def run[A](self: Setter[A]): Cookie.Response = {
      def loop[B](self: Setter[B], map: Map[String, String]): Map[String, String] =
        self match {
          case Set(schema, key, a) =>
            map.updated(key, JsonCodec.jsonEncoder(schema).encodeJson(a).toString)
          case Concat(left, right) =>
            loop(right, loop(left, map))
        }
      val map                                                                     = loop(self, Map.empty)
      Cookie.Response(
        Flash.COOKIE_NAME,
        URLEncoder.encode(
          JsonCodec.jsonEncoder(Schema[Map[String, String]]).encodeJson(map).toString,
          java.nio.charset.Charset.defaultCharset,
        ),
      )

    }
  }

  def set[A: Schema](key: String, a: A): Setter[A] = Setter.Set(Schema[A], key, a)

  private[http] val COOKIE_NAME = "zio-http-flash"

  private case class Get[A](schema: Schema[A], key: String) extends Flash[A]

  private case class FlatMap[A, B](self: Flash[A], f: A => Flash[B]) extends Flash[B]

  private case class OrElse[A, B >: A](self: Flash[A], that: Flash[B]) extends Flash[B]

  private case class WithInput[A](f: Map[String, String] => Flash[A]) extends Flash[A]

  private case class Succeed[A](a: A) extends Flash[A]

  private def succeed[A](a: A): Flash[A] = Succeed(a)

  private def withInput[A](f: Map[String, String] => Flash[A]): Flash[A] = WithInput(f)

  def get[A: Schema](key: String): Flash[A] = Flash.Get(Schema[A], key)

  def getString(key: String): Flash[String] = get[String](key)

  def getFloat(key: String): Flash[Float] = get[Float](key)

  def getDouble(key: String): Flash[Double] = get[Double](key)

  def getInt(key: String): Flash[Int] = get[Int](key)

  def getLong(key: String): Flash[Long] = get[Long](key)

  def getBoolean(key: String): Flash[Boolean] = get[Boolean](key)

  def get[A: Schema]: Flash[A] = withInput { map =>
    map.keys.map(a => Flash.get(a)(Schema[A])).reduce(_ <> _)
  }

  private def loop[A](flash: Flash[A], map: Map[String, String]): Either[Throwable, A] =
    flash match {
      case Get(schema, key)   =>
        map.get(key).toRight(new Throwable(s"no key: $key")).flatMap { value =>
          JsonCodec.jsonDecoder(schema).decodeJson(value).left.map(e => new Throwable(e))
        }
      case WithInput(f)       =>
        loop(f(map), map)
      case OrElse(self, that) =>
        loop(self, map).orElse(loop(that, map)).asInstanceOf[Either[Throwable, A]]
      case FlatMap(self, f)   =>
        loop(self, map) match {
          case Right(value) => loop(f(value), map)
          case Left(e)      => Left(e)
        }
      case Succeed(a)         => Right(a)
    }

  private[http] def run[A](flash: Flash[A], request: Request): Either[Throwable, A] = {
    request
      .cookie(COOKIE_NAME)
      .toRight(new Throwable("flash cookie doesn't exist"))
      .flatMap { cookie =>
        try Right(URLDecoder.decode(cookie.content, java.nio.charset.Charset.defaultCharset))
        catch {
          case e: Exception => Left(e)
        }
      }
      .flatMap { cookieContent =>
        JsonCodec.jsonDecoder(Schema.map[String, String]).decodeJson(cookieContent).left.map(e => new Throwable(e))
      }
      .flatMap(loop(flash, _))
  }

}
