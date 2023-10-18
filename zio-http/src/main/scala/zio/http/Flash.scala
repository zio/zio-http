package zio.http

import java.net.{URLDecoder, URLEncoder}
import java.util.UUID

import zio._

import zio.schema.Schema
import zio.schema.codec.JsonCodec

import zio.http.template._

/**
 * `Flash` represents a flash value that one can retrieve from the flash scope.
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
   * A fash message can represent a notice, an alert or both - it's some kind of
   * a specialized `zio.prelude.These`.
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
  private[http] object Message {
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

  /**
   * `Flash.Backend` represents a flash-scope that is not cookie-based but
   * instead uses an internal structure.
   *
   * Semantically it is identical to the cookie-based flash-scope (valid for a
   * single request) but by using `Flash.Backend` we're not limited in size of
   * the payload as in the cookie-based flash-scope. Still, the `Flash.Backend`
   * uses a cookie but does not transport the payload with it but only an
   * internal identifier.
   */
  trait Backend { self =>

    /**
     * Gets an `A` from the backend-based flash-scope or fails with a
     * `Throwable`.
     */
    def flash[A](request: Request, flash: Flash[A]): IO[Throwable, A]

    /**
     * Gets an `A` from the backend-based flash-scope and provides a fallback.
     */
    def flashOrElse[A](request: Request, flash: Flash[A])(orElse: => A): UIO[A] =
      self.flash(request, flash) <> ZIO.succeed(orElse)

    /**
     * Adds flash values to the backend-based flash-scope and returns a workflow
     * with an updated `Response`.
     */
    def addFlash[A](response: Response, setter: Flash.Setter[A]): UIO[Response]

    /**
     * Optionally adds flash values to the backend-based flash-scope and returns
     * a workflow with an updated `Response`.
     */
    def addFlash[A](response: Response, setterOpt: Option[Flash.Setter[A]]): UIO[Response] =
      setterOpt.fold(ZIO.succeed(response))(self.addFlash(response, _))
  }

  object Backend {

    private case class Impl(ref: Ref[Map[UUID, Map[String, String]]]) extends Backend {
      override def flash[A](request: Request, flash: Flash[A]): IO[Throwable, A] =
        for {
          flashId <- ZIO.from(Flash.run(Flash.getUUID(flashIdName), request))
          a       <- ref.modify { map =>
            Flash.run(flash, map.get(flashId).getOrElse(Map.empty)) match {
              case value @ Right(_) => value -> (map - flashId)
              case value @ Left(_)  => value -> map
            }
          }.flatMap(ZIO.from(_))
        } yield a

      override def addFlash[A](response: Response, setter: Setter[A]): UIO[Response] = {
        val map = Flash.Setter.run(setter, Map.empty)
        for {
          flashId       <- zio.Random.nextUUID
          setterFlashId <- ref.update(in => in + (flashId -> map)).as(Flash.setValue(flashIdName, flashId))
        } yield response.addFlash(setterFlashId)
      }
    }

    val layer: ULayer[Backend] = ZLayer(Ref.make(Map.empty[UUID, Map[String, String]]).map(Impl.apply))

    private val flashIdName = "flashId"

  }

  sealed trait Setter[A] { self =>

    /**
     * Combines setting this flash value with another setter `that`.
     */
    def ++[B](that: => Setter[B]): Setter[(A, B)] = Setter.Concat(self, that)
  }

  private[http] object Setter {

    case object Empty extends Flash.Setter[Unit]

    case class SetValue[A](schema: Schema[A], key: String, a: A) extends Flash.Setter[A]

    case class Concat[A, B](left: Setter[A], right: Setter[B]) extends Flash.Setter[(A, B)]

    def run[A](setter: Setter[A]): Cookie.Response =
      Cookie.Response(
        Flash.COOKIE_NAME,
        URLEncoder.encode(
          JsonCodec.jsonEncoder(Schema[Map[String, String]]).encodeJson(run(setter, Map.empty)).toString,
          java.nio.charset.Charset.defaultCharset.toString.toLowerCase,
        ),
      )

    def run[A](setter: Setter[A], map: Map[String, String]): Map[String, String] = {
      def loop[B](setter: Setter[B], map: Map[String, String]): Map[String, String] =
        setter match {
          case SetValue(schema, key, a) =>
            map.updated(key, JsonCodec.jsonEncoder(schema).encodeJson(a).toString)
          case Concat(left, right)      =>
            loop(right, loop(left, map))
          case Empty                    => map
        }
      loop(setter, map)
    }
  }

  /**
   * Sets a flash value of type `A` with the given key `key`.
   */
  def setValue[A: Schema](key: String, a: A): Setter[A] = Setter.SetValue(Schema[A], key, a)

  /**
   * Sets a flash value of type `A` with the key for a notice.
   */
  def setNotice[A: Schema](a: A): Setter[A] = Setter.SetValue(Schema[A], Message.Notice.name, a)

  /**
   * Sets a flash value of type `A` with the key for an alert.
   */
  def setAlert[A: Schema](a: A): Setter[A] = Setter.SetValue(Schema[A], Message.Alert.name, a)

  def setEmpty: Setter[Unit] = Setter.Empty

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
   * Creates a `Flash.Message` from two other values `flashNotice` and
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
      case _ => Flash.fail(s"neither '${Message.Notice.name}' nor '${Message.Alert.name}' do exist in the flash-scope")
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

  /**
   * Gets a flash value of type `A` associated with the notice key.
   */
  def getNotice[A: Schema]: Flash[A] = get[A](Message.Notice.name)

  /**
   * Gets a flash value of type `A` associated with the alert key.
   */
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
   * Gets a flash value of type `UUID` with the given key `key`.
   */
  def getUUID(key: String): Flash[UUID] = get[UUID](key)

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
        map.get(key).toRight(new Throwable(s"no flash key: $key")).flatMap { value =>
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
          case l @ Left(_)  => l.asInstanceOf[Either[Throwable, A]]
        }
      case Succeed(a)         => Right(a)
      case Fail(message)      => Left(new Throwable(message))
    }

  private[http] def run[A](flash: Flash[A], sourceRequest: Request): Either[Throwable, A] = {
    sourceRequest
      .cookie(COOKIE_NAME)
      .toRight(new RuntimeException("flash cookie doesn't exist"))
      .flatMap { cookie =>
        try Right(URLDecoder.decode(cookie.content, java.nio.charset.Charset.defaultCharset.toString.toLowerCase))
        catch {
          case e: Exception => Left(e)
        }
      }
      .flatMap { cookieContent =>
        JsonCodec.jsonDecoder(Schema.map[String, String]).decodeJson(cookieContent).left.map(e => new Throwable(e))
      }
      .flatMap(in => run(flash, in))
  }

  private[http] def run[A](flash: Flash[A], sourceMap: Map[String, String]): Either[Throwable, A] =
    loop(flash, sourceMap)
}
