package zio.http

import zio.http.Flash.{Message, succeed}
import zio.http.template.{Dom, Html}

import java.net.{URLDecoder, URLEncoder}
import zio.schema.Schema
import zio.schema.codec.JsonCodec

/**
 * `Flash` represents a flash value that one can retrieve from the
 * (cookie-based) flash scope.
 *
 * The flash scope consists of a serialized and url-encoded json object built
 * with `zio-schema`.
 */
sealed trait Flash[+A] { self =>

  def flatMap[B](f: A => Flash[B]): Flash[B] = Flash.FlatMap(self, f)

  def map[B](f: A => B): Flash[B] = self.flatMap(a => Flash.succeed(f(a)))

  def orElse[B >: A](that: => Flash[B]): Flash[B] = Flash.OrElse(self, that)

  /**
   * Operator alias for `orElse`.
   */
  def <>[B >: A](that: => Flash[B]): Flash[B] = self.orElse(that)

  def zip[B](that: => Flash[B]): Flash[(A, B)] = self.zipWith(that)((a, b) => a -> b)

  /**
   * Operator alias for `zip`.
   */
  def <*>[B](that: => Flash[B]): Flash[(A, B)] = self.zip(that)

  def zipWith[B, C](that: => Flash[B])(f: (A, B) => C): Flash[C] =
    self.flatMap(a => that.map(b => f(a, b)))

  def optional: Flash[Option[A]] = self.map(Option(_)) <> Flash.succeed(None)

  def foldHtml[A1 >: A, B](f: Html => B, g: Html => B)(h: (B, B) => B)(implicit
    ev: A1 =:= Flash.Message[Html, Html],
  ): Flash[B] =
    self.map(a => a.asInstanceOf[A1].fold(f, g)(h))

  def toHtml[A1 >: A](implicit ev: A1 =:= String): Flash[Html] =
    self.map(Html.fromString(_))

}

object Flash {

  /**
   * A flash message can represent a notice, an alert or both - it's some kind
   * of a specialized `zio.prelude.These`.
   *
   * Using a flash message allows one to categorize those into notice or alert
   * and by that wrap both messages with a different ui design.
   */
  sealed trait Message[+A, +B] { self =>

    /**
     * Folds a notice with `f` into `C`, an alert with `g` into `C` and both
     * with `h` into another `C`.
     */
    def fold[C](f: A => C, g: B => C)(h: (C, C) => C): C = this match {
      case Message.Notice(a)                                 => f(a)
      case Message.Alert(b)                                  => g(b)
      case Message.Both(Message.Notice(a), Message.Alert(b)) => h(f(a), g(b))
    }

    /**
     * Returns true if this `Message` represents both, a notice and and alert.
     */
    def isBoth: Boolean = this match {
      case Message.Both(_, _) => true
      case _                  => false
    }

    /**
     * Returns true if this `Message` represents a notice only.
     */
    def isNotice = this match {
      case Message.Notice(_) => true
      case _                 => false
    }

    /**
     * Returns true if this `Message` represents an alert only.
     */
    def isAlert = this match {
      case Message.Alert(_) => true
      case _                => false
    }
  }
  object Message {
    case class Notice[+A](a: A) extends Message[A, Nothing]
    private[http] object Notice {
      val name = "notice"
    }
    case class Alert[+B](b: B) extends Message[Nothing, B]
    private[http] object Alert  {
      val name = "alert"
    }
    private[http] case class Both[+A, +B](notice: Notice[A], alert: Alert[B]) extends Message[A, B]
  }

  sealed trait Setter[A] { self =>

    /**
     * Combines setting this flash value with another setter `that`.
     */
    def ++[B](that: => Setter[B]): Setter[Unit] = Setter.Concat(self, that)
  }

  private[http] object Setter {
    case class Set[A](schema: Schema[A], key: String, a: A) extends Flash.Setter[Unit]

    case class Concat[A, B](left: Setter[A], right: Setter[B]) extends Flash.Setter[Unit]

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
          java.nio.charset.Charset.defaultCharset.toString.toLowerCase,
        ),
      )

    }
  }

  /**
   * Sets any flash value of type `A` with the given key `key`.
   */
  def set[A: Schema](key: String, a: A): Setter[Unit] = Setter.Set(Schema[A], key, a)

  /**
   * Sets any flash value of type `A` with the key for a notice.
   */
  def setNotice[A: Schema](a: A): Setter[Unit] = Setter.Set(Schema[A], Message.Notice.name, a)

  /**
   * Sets any flash value of type `A` with the key for an alert.
   */
  def setAlert[A: Schema](a: A): Setter[Unit] = Setter.Set(Schema[A], Message.Alert.name, a)

  private[http] val COOKIE_NAME = "zio-http-flash"

  private case class Get[A](schema: Schema[A], key: String) extends Flash[A]

  private case class FlatMap[A, B](self: Flash[A], f: A => Flash[B]) extends Flash[B]

  private case class OrElse[A, B >: A](self: Flash[A], that: Flash[B]) extends Flash[B]

  private case class WithInput[A](f: Map[String, String] => Flash[A]) extends Flash[A]

  private case class Succeed[A](a: A) extends Flash[A]

  private case class Fail(message: String) extends Flash[Nothing]

  def succeed[A](a: A): Flash[A] = Succeed(a)

  def fail(message: String): Flash[Nothing] = Fail(message)

  private def withInput[A](f: Map[String, String] => Flash[A]): Flash[A] = WithInput(f)

  private def getMessage[A: Schema, B: Schema]: Flash[Message[A, B]] =
    getMessage(Flash.get[A](Message.Notice.name), Flash.get[B](Message.Alert.name))

  /**
   * Creates a `Flash.Message` from two simpler values `flashNotice` and
   * `flashAlert`.
   *
   * Uses `flashNotice` to create a `Flash.Message` representing a notice.
   *
   * Uses `flashAlert` to create a `Flash.Message` representing an alert.
   *
   * If `flashNotice` and `flashAlert` are both available in the flash scope the
   * resulting `Flash.Message` will represent both.
   */
  def getMessage[A, B](flashNotice: Flash[A], flashAlert: Flash[B]): Flash[Message[A, B]] =
    (flashNotice.optional <*> flashAlert.optional).flatMap {
      case (Some(a), Some(b)) => Flash.succeed(Flash.Message.Both(Flash.Message.Notice(a), Flash.Message.Alert(b)))
      case (Some(a), _)       => Flash.succeed(Flash.Message.Notice(a))
      case (_, Some(b))       => Flash.succeed(Flash.Message.Alert(b))
      case _ => Flash.fail(s"neither '${Message.Notice.name}' nor '${Message.Alert.name}' do exist in flash scope")
    }

  private def getMessageHtml[A: Schema, B: Schema](f: A => Html, g: B => Html): Flash[Message[Html, Html]] =
    getMessage[A, B].map {
      case Message.Notice(a)                                 => Message.Notice(f(a))
      case Message.Alert(b)                                  => Message.Alert(g(b))
      case Message.Both(Message.Notice(a), Message.Alert(b)) => Message.Both(Message.Notice(f(a)), Message.Alert(g(b)))
    }

  /**
   * Creates a `Flash.Message` using the default keys for notice and alert.
   *
   * Additionally the values must be of type `String` so they can be transformed
   * to `Html`.
   *
   * Usage e.g.: `Flash.getMessageHtml.foldHtml(showNotice, showAlert)(_ ++ _)`
   */
  def getMessageHtml: Flash[Message[Html, Html]] =
    getMessageHtml[String, String](a => Dom.text(a), b => Dom.text(b))

  /**
   * Gets any flash value of type `A` with the given key `key`.
   */
  def get[A: Schema](key: String): Flash[A] = Flash.Get(Schema[A], key)

  /**
   * Gets a flash value of type `String` with the given key `key`.
   */
  def getString(key: String): Flash[String] = get[String](key)

  def getNotice[A: Schema]: Flash[A] = get[A](Message.Notice.name)

  def getAlert[A: Schema]: Flash[A] = get[A](Message.Alert.name)

  /**
   * Gets a flash value of type `Float` with the given key `key`.
   */
  def getFloat(key: String): Flash[Float] = get[Float](key)

  /**
   * Gets a flash value of type `Double` with the given key `key`.
   */
  def getDouble(key: String): Flash[Double] = get[Double](key)

  /**
   * Gets a flash value of type `Int` with the given key `key`.
   */
  def getInt(key: String): Flash[Int] = get[Int](key)

  /**
   * Gets a flash value of type `Long` with the given key `key`.
   */
  def getLong(key: String): Flash[Long] = get[Long](key)

  /**
   * Gets a flash value of type `Boolean` with the given key `key`.
   */
  def getBoolean(key: String): Flash[Boolean] = get[Boolean](key)

  /**
   * Gets the first flash value of type `A` regardless of any key.
   */
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
        (loop(self, map) match {
          case Left(_)      => loop(that, map)
          case Right(value) => Right(value)
        }).asInstanceOf[Either[Throwable, A]]
      case FlatMap(self, f)   =>
        loop(self, map) match {
          case Right(value) => loop(f(value), map)
          case Left(e)      => Left(e)
        }
      case Succeed(a)         => Right(a)
      case Fail(message)      => Left(new Throwable(message))
    }

  private[http] def run[A](flash: Flash[A], request: Request): Either[Throwable, A] = {
    request
      .cookie(COOKIE_NAME)
      .toRight(new Throwable("flash cookie doesn't exist"))
      .flatMap { cookie =>
        try Right(URLDecoder.decode(cookie.content, java.nio.charset.Charset.defaultCharset.toString.toLowerCase))
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
