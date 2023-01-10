package zio.http

import io.netty.handler.codec.http.HttpHeaderNames
import zio._
import zio.http.html.{Html, Template}
import zio.http.model._
import zio.http.model.headers.HeaderModifierZIO
import zio.http.socket.{SocketApp, WebSocketChannelEvent}
import zio.stream.ZStream

import java.io.{File, FileNotFoundException}
import java.nio.charset.Charset
import java.nio.file.Paths
import java.util.zip.ZipFile
import scala.reflect.ClassTag
import scala.util.control.NonFatal

trait Handler[-R, +Err, -In, +Out] { self =>

  final def @@[R1 <: R, Err1 >: Err, In1 <: In, Out1 >: Out, In2, Out2](
    that: HandlerAspect[R1, Err1, In1, Out1, In2, Out2],
  ): Handler[R1, Err1, In2, Out2] =
    that(self)

  final def >>=[R1 <: R, Err1 >: Err, In1 <: In, Out1](
    f: Out => Handler[R1, Err1, In1, Out1],
  )(implicit trace: Trace): Handler[R1, Err1, In1, Out1] =
    self.flatMap(f)

  final def >>>[R1 <: R, Err1 >: Err, In1 >: Out, Out1](
    that: Handler[R1, Err1, In1, Out1],
  )(implicit trace: Trace): Handler[R1, Err1, In, Out1] =
    self andThen that

  final def <<<[R1 <: R, Err1 >: Err, In1, Out1 <: In](
    that: Handler[R1, Err1, In1, Out1],
  )(implicit trace: Trace): Handler[R1, Err1, In1, Out] =
    self compose that

  final def <>[R1 <: R, Err1 >: Err, In1 <: In, Out1 >: Out](
    that: Handler[R1, Err1, In1, Out1],
  )(implicit trace: Trace): Handler[R1, Err1, In1, Out1] =
    self.orElse(that)

  final def <*>[R1 <: R, Err1 >: Err, In1 <: In, Out1](
    that: Handler[R1, Err1, In1, Out1],
  )(implicit trace: Trace): Handler[R1, Err1, In1, (Out, Out1)] =
    self.zip(that)

  final def <*[R1 <: R, Err1 >: Err, In1 <: In, Out1](
    that: Handler[R1, Err1, In1, Out1],
  )(implicit trace: Trace): Handler[R1, Err1, In1, Out] =
    self.zipLeft(that)

  final def *>[R1 <: R, Err1 >: Err, In1 <: In, Out1](
    that: Handler[R1, Err1, In1, Out1],
  )(implicit trace: Trace): Handler[R1, Err1, In1, Out1] =
    self.zipRight(that)

  final def \/[R1 <: R, Err1 >: Err, In1, Out1](
    that: Handler[R1, Err1, In1, Out1],
  ): HandlerAspect[R1, Err1, Out, In1, In, Out1] =
    self.codecMiddleware(that)

  final def absolve[Err1 >: Err, Out1](implicit ev: Out <:< Either[Err1, Out1]): Handler[R, Err1, In, Out1] =
    self.flatMap { out =>
      ev(out) match {
        case Right(out1) => Handler.succeed(out1)
        case Left(err)   => Handler.fail(err)
      }
    }

  final def andThen[R1 <: R, Err1 >: Err, In1 >: Out, Out1](
    that: Handler[R1, Err1, In1, Out1],
  )(implicit trace: Trace): Handler[R1, Err1, In, Out1] =
    (in: In) => self(in).flatMap(that(_))

  def apply(in: In): HExit[R, Err, Out]

  final def as[Out1](out: Out1)(implicit trace: Trace): Handler[R, Err, In, Out1] =
    self.map(_ => out)

  final def codecMiddleware[R1 <: R, Err1 >: Err, In1, Out1](
    that: Handler[R1, Err1, In1, Out1],
  ): HandlerAspect[R1, Err1, Out, In1, In, Out1] =
    HandlerAspect.codecHttp(self, that)

  final def compose[R1 <: R, Err1 >: Err, In1, Out1 <: In](
    that: Handler[R1, Err1, In1, Out1],
  )(implicit trace: Trace): Handler[R1, Err1, In1, Out] =
    that.andThen(self)

  final def toSocketApp(implicit
    ev1: WebSocketChannelEvent <:< In,
    ev2: Err <:< Throwable,
    trace: Trace,
  ): SocketApp[R] =
    SocketApp(event => self.toZIO(event).mapError(ev2))

  final def toZIO(in: In)(implicit trace: Trace): ZIO[R, Err, Out] =
    self(in).toZIO

  final def contramap[In1](f: In1 => In)(implicit trace: Trace): Handler[R, Err, In1, Out] =
    (in: In1) => self(f(in))

  final def contramapZIO[R1 <: R, Err1 >: Err, In1](f: In1 => ZIO[R1, Err1, In])(implicit
    trace: Trace,
  ): Handler[R1, Err1, In1, Out] =
    (in: In1) => HExit.fromZIO(f(in)).flatMap(self(_))

  final def contraFlatMap[In1]: Handler.ContraFlatMap[R, Err, In, Out, In1] =
    new Handler.ContraFlatMap(self)

  final def catchAll[R1 <: R, Err1, In1 <: In, Out1 >: Out](f: Err => Handler[R1, Err1, In1, Out1])(implicit
    trace: Trace,
  ): Handler[R1, Err1, In1, Out1] =
    self.foldHandler(f, Handler.succeed)

  final def catchAllCause[R1 <: R, Err1, In1 <: In, Out1 >: Out](f: Cause[Err] => Handler[R1, Err1, In1, Out1])(implicit
    trace: Trace,
  ): Handler[R1, Err1, In1, Out1] =
    self.foldCauseHandler(f, Handler.succeed)

  final def catchAllDefect[R1 <: R, Err1 >: Err, In1 <: In, Out1 >: Out](f: Throwable => Handler[R1, Err1, In1, Out1])(
    implicit trace: Trace,
  ): Handler[R1, Err1, In1, Out1] =
    self.foldCauseHandler(
      cause => cause.dieOption.fold[Handler[R1, Err1, In1, Out1]](Handler.failCause(cause))(f),
      Handler.succeed,
    )

  final def catchSome[R1 <: R, Err1 >: Err, In1 <: In, Out1 >: Out](
    pf: PartialFunction[Err, Handler[R1, Err1, In1, Out1]],
  )(implicit
    trace: Trace,
  ): Handler[R1, Err1, In1, Out1] =
    self.catchAll(err => pf.applyOrElse(err, (err: Err1) => Handler.fail(err)))

  final def catchSomeDefect[R1 <: R, Err1 >: Err, In1 <: In, Out1 >: Out](
    pf: PartialFunction[Throwable, Handler[R1, Err1, In1, Out1]],
  )(implicit
    trace: Trace,
  ): Handler[R1, Err1, In1, Out1] =
    self.catchAllDefect(err => pf.applyOrElse(err, (cause: Throwable) => Handler.die(cause)))

  final def delay(duration: Duration)(implicit trace: Trace): Handler[R, Err, In, Out] =
    self.delayAfter(duration)

  final def delayAfter(duration: Duration)(implicit trace: Trace): Handler[R, Err, In, Out] =
    self.mapZIO(out => ZIO.succeed(out).delay(duration))

  final def delayBefore(duration: Duration)(implicit trace: Trace): Handler[R, Err, In, Out] =
    self.contramapZIO(in => ZIO.succeed(in).delay(duration))

  final def either(implicit ev: CanFail[Err], trace: Trace): Handler[R, Nothing, In, Either[Err, Out]] =
    self.foldHandler(err => Handler.succeed(Left(err)), out => Handler.succeed(Right(out)))

  final def flatten[R1 <: R, Err1 >: Err, In1 <: In, Out1](implicit
    ev: Out <:< Handler[R1, Err1, In1, Out1],
    trace: Trace,
  ): Handler[R1, Err1, In1, Out1] =
    self.flatMap(identity(_))

  final def flatMap[R1 <: R, Err1 >: Err, In1 <: In, Out1](
    f: Out => Handler[R1, Err1, In1, Out1],
  )(implicit trace: Trace): Handler[R1, Err1, In1, Out1] =
    self.foldHandler(
      Handler.fail(_),
      f(_),
    )

  final def foldCauseHandler[R1 <: R, Err1, In1 <: In, Out1](
    onFailure: Cause[Err] => Handler[R1, Err1, In1, Out1],
    onSuccess: Out => Handler[R1, Err1, In1, Out1],
  )(implicit trace: Trace): Handler[R1, Err1, In1, Out1] =
    (in: In1) =>
      self(in).foldExit(
        cause => onFailure(cause)(in),
        out => onSuccess(out)(in),
      )

  final def foldHandler[R1 <: R, Err1, In1 <: In, Out1](
    onFailure: Err => Handler[R1, Err1, In1, Out1],
    onSuccess: Out => Handler[R1, Err1, In1, Out1],
  )(implicit trace: Trace): Handler[R1, Err1, In1, Out1] =
    self.foldCauseHandler(
      cause => cause.failureOrCause.fold(onFailure, Handler.failCause(_)),
      onSuccess,
    )

  final def map[Out1](f: Out => Out1)(implicit trace: Trace): Handler[R, Err, In, Out1] =
    self.flatMap(out => Handler.succeed(f(out)))

  final def mapError[Err1](f: Err => Err1)(implicit trace: Trace): Handler[R, Err1, In, Out] =
    self.foldHandler(err => Handler.fail(f(err)), Handler.succeed)

  final def mapZIO[R1 <: R, Err1 >: Err, Out1](f: Out => ZIO[R1, Err1, Out1])(implicit
    trace: Trace,
  ): Handler[R1, Err1, In, Out1] =
    self >>> Handler.fromFunctionZIO(f)

  final def merge[Err1 >: Err, Out1 >: Out](implicit ev: Err1 =:= Out1, trace: Trace): Handler[R, Nothing, In, Out1] =
    self.catchAll(Handler.succeed(_))

  final def narrow[In1](implicit ev: In1 <:< In): Handler[R, Err, In1, Out] =
    self.asInstanceOf[Handler[R, Err, In1, Out]]

  final def onExit[R1 <: R, Err1 >: Err](f: Exit[Err, Out] => ZIO[R1, Err1, Any])(implicit
    trace: Trace,
  ): Handler[R1, Err1, In, Out] =
    self.tapAllZIO(
      cause => f(Exit.failCause(cause)),
      out => f(Exit.succeed(out)),
    )

  final def option(implicit ev: CanFail[Err], trace: Trace): Handler[R, Nothing, In, Option[Out]] =
    self.foldHandler(_ => Handler.succeed(None), out => Handler.succeed(Some(out)))

  final def optional[Err1](implicit ev: Err <:< Option[Err1], trace: Trace): Handler[R, Err1, In, Option[Out]] =
    self.foldHandler(
      err => ev(err).fold[Handler[R, Err1, In, Option[Out]]](Handler.succeed(None))(Handler.fail(_)),
      out => Handler.succeed(Some(out)),
    )

  final def orDie(implicit ev1: Err <:< Throwable, ev2: CanFail[Err], trace: Trace): Handler[R, Nothing, In, Out] =
    orDieWith(ev1)

  final def orDieWith(f: Err => Throwable)(implicit ev: CanFail[Err], trace: Trace): Handler[R, Nothing, In, Out] =
    self.foldHandler(err => Handler.die(f(err)), Handler.succeed)

  final def orElse[R1 <: R, Err1 >: Err, In1 <: In, Out1 >: Out](
    that: Handler[R1, Err1, In1, Out1],
  )(implicit trace: Trace): Handler[R1, Err1, In1, Out1] =
    (in: In1) =>
      (self(in), that(in)) match {
        case (s @ HExit.Success(_), _)                        =>
          s
        case (s @ HExit.Failure(cause), _) if cause.isDie     =>
          s
        case (HExit.Failure(cause), other) if cause.isFailure =>
          other
        case (self, other)                                    =>
          HExit.fromZIO(self.toZIO.orElse(other.toZIO))
      }

  final def provideEnvironment(r: ZEnvironment[R])(implicit trace: Trace): Handler[Any, Err, In, Out] =
    (in: In) => self(in).provideEnvironment(r)

  final def provideLayer[Err1 >: Err, R0](layer: ZLayer[R0, Err1, R])(implicit
    trace: Trace,
  ): Handler[R0, Err1, In, Out] =
    (in: In) => self(in).provideLayer(layer)

  final def provideSomeEnvironment[R1](f: ZEnvironment[R1] => ZEnvironment[R])(implicit
    trace: Trace,
  ): Handler[R1, Err, In, Out] =
    (in: In) => self(in).provideSomeEnvironment(f)

  final def provideSomeLayer[R0, R1: Tag, Err1 >: Err](
    layer: ZLayer[R0, Err1, R1],
  )(implicit ev: R0 with R1 <:< R, trace: Trace): Handler[R0, Err1, In, Out] =
    (in: In) => self(in).provideSomeLayer(layer)

  final def race[R1 <: R, Err1 >: Err, In1 <: In, Out1 >: Out](
    that: Handler[R1, Err1, In1, Out1],
  )(implicit trace: Trace): Handler[R1, Err1, In1, Out1] =
    (in: In1) =>
      (self(in), that(in)) match {
        case (HExit.Effect(self), HExit.Effect(other)) => HExit.fromZIO(self.raceFirst(other))
        case (HExit.Effect(_), other)                  => other
        case (self, _)                                 => self
      }

  final def refineOrDie[Err1](
    pf: PartialFunction[Err, Err1],
  )(implicit ev1: Err <:< Throwable, ev2: CanFail[Err], trace: Trace): Handler[R, Err1, In, Out] =
    refineOrDieWith(pf)(ev1)

  final def refineOrDieWith[Err1](
    pf: PartialFunction[Err, Err1],
  )(f: Err => Throwable)(implicit ev: CanFail[Err], trace: Trace): Handler[R, Err1, In, Out] =
    self.foldHandler(
      err => pf.andThen(Handler.fail(_)).applyOrElse(err, (e: Err) => Handler.die(f(e))),
      Handler.succeed,
    )

  final def tapAllZIO[R1 <: R, Err1 >: Err](
    onFailure: Cause[Err] => ZIO[R1, Err1, Any],
    onSuccess: Out => ZIO[R1, Err1, Any],
  )(implicit trace: Trace): Handler[R1, Err1, In, Out] =
    (in: In) =>
      self(in) match {
        case HExit.Success(a)     => HExit.fromZIO(onSuccess(a).as(a))
        case HExit.Failure(cause) => HExit.fromZIO(onFailure(cause) *> ZIO.failCause(cause))
        case HExit.Effect(z)      => HExit.Effect(z.tapErrorCause(onFailure).tap(onSuccess))
      }

  final def tapErrorCauseZIO[R1 <: R, Err1 >: Err](
    f: Cause[Err] => ZIO[R1, Err1, Any],
  )(implicit trace: Trace): Handler[R1, Err1, In, Out] =
    self.tapAllZIO(f, _ => ZIO.unit)

  final def tapErrorZIO[R1 <: R, Err1 >: Err](
    f: Err => ZIO[R1, Err1, Any],
  )(implicit trace: Trace): Handler[R1, Err1, In, Out] =
    self.tapAllZIO(cause => cause.failureOption.fold[ZIO[R1, Err1, Any]](ZIO.unit)(f), _ => ZIO.unit)

  final def tapZIO[R1 <: R, Err1 >: Err](f: Out => ZIO[R1, Err1, Any])(implicit
    trace: Trace,
  ): Handler[R1, Err1, In, Out] =
    self.tapAllZIO(_ => ZIO.unit, f)

  final def toRoute(implicit trace: Trace): Route[R, Err, In, Out] =
    Route.fromHandler(self)

  final def unrefine[Err1 >: Err](pf: PartialFunction[Throwable, Err1]): Handler[R, Err1, In, Out] =
    unrefineWith(pf)(err => err)

  final def unrefineTo[Err1 >: Err: ClassTag]: Handler[R, Err1, In, Out] =
    unrefine { case err: Err1 =>
      err
    }

  final def unrefineWith[Err1](pf: PartialFunction[Throwable, Err1])(f: Err => Err1): Handler[R, Err1, In, Out] =
    self.catchAllCause(cause =>
      cause.find {
        case Cause.Die(t, _) if pf.isDefinedAt(t) => pf(t)
      }.fold(Handler.failCause(cause.map(f)))(Handler.fail(_)),
    )

  final def unwrapZIO[R1 <: R, Err1 >: Err, Out1](implicit
    ev: Out <:< ZIO[R1, Err1, Out1],
    trace: Trace,
  ): Handler[R1, Err1, In, Out1] =
    self.flatMap(out => Handler.fromZIO(ev(out)))

  def widen[Err1, Out1](implicit ev1: Err <:< Err1, ev2: Out <:< Out1): Handler[R, Err1, In, Out1] =
    self.asInstanceOf[Handler[R, Err1, In, Out1]]

  final def zip[R1 <: R, Err1 >: Err, In1 <: In, Out1](
    that: Handler[R1, Err1, In1, Out1],
  )(implicit trace: Trace): Handler[R1, Err1, In1, (Out, Out1)] =
    self.flatMap(out => that.map(out1 => (out, out1)))

  final def zipLeft[R1 <: R, Err1 >: Err, In1 <: In, Out1](
    that: Handler[R1, Err1, In1, Out1],
  )(implicit trace: Trace): Handler[R1, Err1, In1, Out] =
    self.flatMap(out => that.as(out))

  final def zipRight[R1 <: R, Err1 >: Err, In1 <: In, Out1](
    that: Handler[R1, Err1, In1, Out1],
  )(implicit trace: Trace): Handler[R1, Err1, In1, Out1] =
    self.flatMap(_ => that)
}

object Handler {

  def attempt[Out](out: => Out)(implicit trace: Trace): Handler[Any, Throwable, Any, Out] =
    fromHExit {
      try HExit.succeed(out)
      catch {
        case NonFatal(cause) => HExit.fail(cause)
      }
    }

  def badRequest(message: String)(implicit trace: Trace): Handler[Any, Nothing, Any, Response] =
    error(HttpError.BadRequest(message))

  private def determineMediaType(filePath: String): Option[MediaType] = {
    filePath.lastIndexOf(".") match {
      case -1 => None
      case i  =>
        // Extract file extension
        val ext = filePath.substring(i + 1)
        MediaType.forFileExtension(ext)
    }
  }

  def die(failure: => Throwable)(implicit trace: Trace): Handler[Any, Nothing, Any, Nothing] =
    fromHExit(HExit.die(failure))

  def dieMessage(message: => String)(implicit trace: Trace): Handler[Any, Nothing, Any, Nothing] =
    die(new RuntimeException(message))

  def error(error: HttpError)(implicit trace: Trace): Handler[Any, Nothing, Any, Response] =
    response(Response.fromHttpError(error))

  def error(message: String)(implicit trace: Trace): Handler[Any, Nothing, Any, Response] =
    error(HttpError.InternalServerError(message))

  def fail[Err](err: => Err)(implicit trace: Trace): Handler[Any, Err, Any, Nothing] =
    fromHExit(HExit.fail(err))

  def failCause[Err](cause: => Cause[Err])(implicit trace: Trace): Handler[Any, Err, Any, Nothing] =
    fromHExit(HExit.failCause(cause))

  def forbidden(message: String)(implicit trace: Trace): Handler[Any, Nothing, Any, Response] =
    error(HttpError.Forbidden(message))

  def fromBody(body: Body)(implicit trace: Trace): Handler[Any, Nothing, Any, Response] =
    response(Response(body = body))

  def fromEither[Err, Out](either: Either[Err, Out])(implicit trace: Trace): Handler[Any, Err, Any, Out] =
    either.fold(Handler.fail(_), Handler.succeed(_))

  def fromFile(file: => File)(implicit trace: Trace): Handler[Any, Throwable, Any, Response] =
    fromFileZIO(ZIO.succeed(file))

  def fromFileZIO[R](getFile: ZIO[R, Throwable, File])(implicit
    trace: Trace,
  ): Handler[R, Throwable, Any, Response] =
    fromZIO {
      getFile.flatMap { file =>
        ZIO.attempt {
          if (file.isFile) {
            val length   = Headers.contentLength(file.length())
            val response = http.Response(headers = length, body = Body.fromFile(file))
            val pathName = file.toPath.toString

            // Set MIME type in the response headers. This is only relevant in
            // case of RandomAccessFile transfers as browsers use the MIME type,
            // not the file extension, to determine how to process a URL.
            // {{{<a href="MSDN Doc">https://developer.mozilla.org/en-US/docs/Web/HTTP/Headers/Content-Type</a>}}}
            Handler.succeed(determineMediaType(pathName).fold(response)(response.withMediaType))
          } else Handler.fail(new IllegalArgumentException(s"File $file is not a file"))
        }
      }
    }.flatten

  def fromFunction[In]: FromFunction[In] = new FromFunction[In](())

  def fromFunctionHandler[In]: FromFunctionHandler[In] = new FromFunctionHandler[In](())

  def fromFunctionHExit[In]: FromFunctionHExit[In] = new FromFunctionHExit[In](())

  def fromFunctionZIO[In]: FromFunctionZIO[In] = new FromFunctionZIO[In](())

  def fromHExit[R, Err, Out](hExit: => HExit[R, Err, Out])(implicit trace: Trace): Handler[R, Err, Any, Out] =
    (_: Any) => hExit

  def fromPath(head: String, tail: String*)(implicit trace: Trace): Handler[Any, Throwable, Any, Response] =
    fromFile(Paths.get(head, tail: _*).toFile)

  def fromResource(path: String)(implicit trace: Trace): Handler[Any, Throwable, Any, Response] =
    getResource(path).flatMap(fromResourceWithURL)

  def fromRoute[R, Err, In, Out](route: Route[R, Err, In, Out], default: Handler[R, Err, In, Out])(implicit
    trace: Trace,
  ): Handler[R, Err, In, Out] =
    route.toHandler(default)

  def fromStream[R](stream: ZStream[R, Throwable, String], charset: Charset = HTTP_CHARSET)(implicit
    trace: Trace,
  ): Handler[R, Throwable, Any, Response] =
    Handler.fromZIO {
      ZIO.environment[R].map { env =>
        fromBody(Body.fromStream(stream.provideEnvironment(env), charset))
      }
    }.flatten

  def fromStream[R](stream: ZStream[R, Throwable, Byte])(implicit
    trace: Trace,
  ): Handler[R, Throwable, Any, Response] =
    Handler.fromZIO {
      ZIO.environment[R].map { env =>
        fromBody(Body.fromStream(stream.provideEnvironment(env)))
      }
    }.flatten

  def fromZIO[R, Err, Out](zio: => ZIO[R, Err, Out])(implicit trace: Trace): Handler[R, Err, Any, Out] =
    fromHExit(HExit.fromZIO(zio))

  private[zio] def fromResourceWithURL(
    url: java.net.URL,
  )(implicit trace: Trace): Handler[Any, Throwable, Any, Response] = {
    url.getProtocol match {
      case "file" =>
        Handler.fromFile(new File(url.getPath))
      case "jar"  =>
        val path         = new java.net.URI(url.getPath).getPath // remove "file:" prefix and normalize whitespace
        val bangIndex    = path.indexOf('!')
        val filePath     = path.substring(0, bangIndex)
        val resourcePath = path.substring(bangIndex + 2)
        val mediaType    = determineMediaType(resourcePath)
        val openZip      = ZIO.attemptBlockingIO(new ZipFile(filePath))
        val closeZip     = (jar: ZipFile) => ZIO.attemptBlocking(jar.close()).ignoreLogged

        def fileNotFound = new FileNotFoundException(s"Resource $resourcePath not found")

        def isDirectory = new IllegalArgumentException(s"Resource $resourcePath is a directory")

        val appZIO =
          ZIO.acquireReleaseWith(openZip)(closeZip) { jar =>
            for {
              entry <- ZIO
                .attemptBlocking(Option(jar.getEntry(resourcePath)))
                .collect(fileNotFound) { case Some(e) => e }
              _     <- ZIO.when(entry.isDirectory)(ZIO.fail(isDirectory))
              contentLength = entry.getSize
              inZStream     = ZStream
                .acquireReleaseWith(openZip)(closeZip)
                .mapZIO(jar => ZIO.attemptBlocking(jar.getEntry(resourcePath) -> jar))
                .flatMap { case (entry, jar) => ZStream.fromInputStream(jar.getInputStream(entry)) }
              response      = Response(body = Body.fromStream(inZStream))
            } yield mediaType.fold(response) { t =>
              response.withMediaType(t).withContentLength(contentLength)
            }
          }

        Handler.fromZIO(appZIO)
      case proto  =>
        Handler.fail(new IllegalArgumentException(s"Unsupported protocol: $proto"))
    }
  }

  def getResource(path: String)(implicit trace: Trace): Handler[Any, Throwable, Any, java.net.URL] =
    Handler
      .fromZIO(ZIO.attemptBlocking(getClass.getClassLoader.getResource(path)))
      .flatMap { resource =>
        if (resource == null) Handler.fail(new IllegalArgumentException(s"Resource $path not found"))
        else Handler.succeed(resource)
      }

  def getResourceAsFile(path: String)(implicit trace: Trace): Handler[Any, Throwable, Any, File] =
    getResource(path).map(url => new File(url.getPath))

  def html(view: Html)(implicit trace: Trace): Handler[Any, Nothing, Any, Response] =
    response(Response.html(view))

  def identity[A](implicit trace: Trace): Handler[Any, Nothing, A, A] =
    (in: A) => HExit.succeed(in)

  def methodNotAllowed(message: String)(implicit trace: Trace): Handler[Any, Nothing, Any, Response] =
    error(HttpError.MethodNotAllowed(message))

  def notFound: Handler[Any, Nothing, Request, Response] =
    Handler
      .fromFunctionHandler[Request] { request =>
        error(HttpError.NotFound(request.url.path.encode))
      }

  def ok: Handler[Any, Nothing, Any, Response] =
    status(Status.Ok)

  def response(response: Response)(implicit trace: Trace): Handler[Any, Nothing, Any, Response] =
    succeed(response)

  def responseZIO[R, Err](getResponse: ZIO[R, Err, Response])(implicit trace: Trace): Handler[R, Err, Any, Response] =
    fromZIO(getResponse)

  def stackTrace(implicit trace: Trace): Handler[Any, Nothing, Any, StackTrace] =
    fromZIO(ZIO.stackTrace)

  def status(code: Status)(implicit trace: Trace): Handler[Any, Nothing, Any, Response] =
    succeed(Response(code))

  def succeed[Out](out: Out)(implicit trace: Trace): Handler[Any, Nothing, Any, Out] =
    fromHExit(HExit.succeed(out))

  def template(heading: CharSequence)(view: Html)(implicit trace: Trace): Handler[Any, Nothing, Any, Response] =
    response(Response.html(Template.container(heading)(view)))

  def text(text: CharSequence)(implicit trace: Trace): Handler[Any, Nothing, Any, Response] =
    response(Response.text(text))

  def timeout(duration: Duration)(implicit trace: Trace): Handler[Any, Nothing, Any, Response] =
    status(Status.RequestTimeout).delay(duration)

  def tooLarge: Handler[Any, Nothing, Any, Response] =
    Handler.status(Status.RequestEntityTooLarge)

  final implicit class RequestHandlerSyntax[-R, +Err](val self: RequestHandler[R, Err])
      extends HeaderModifierZIO[RequestHandler[R, Err]] {

    /**
     * Patches the response produced by the app
     */
    def patch(patch: Patch)(implicit trace: Trace): RequestHandler[R, Err] = self.map(patch(_))

    /**
     * Overwrites the method in the incoming request
     */
    def setMethod(method: Method)(implicit trace: Trace): RequestHandler[R, Err] =
      self.contramap[Request](_.copy(method = method))

    /**
     * Overwrites the path in the incoming request
     */
    def setPath(path: Path)(implicit trace: Trace): RequestHandler[R, Err] = self.contramap[Request](_.updatePath(path))

    /**
     * Sets the status in the response produced by the app
     */
    def setStatus(status: Status)(implicit trace: Trace): RequestHandler[R, Err] = patch(Patch.setStatus(status))

    /**
     * Overwrites the url in the incoming request
     */
    def setUrl(url: URL)(implicit trace: Trace): RequestHandler[R, Err] = self.contramap[Request](_.copy(url = url))

    /**
     * Updates the current Headers with new one, using the provided update
     * function passed.
     */
    override def updateHeaders(update: Headers => Headers)(implicit trace: Trace): RequestHandler[R, Err] =
      self.map(_.updateHeaders(update))
  }

  final implicit class ResponseOutputSyntax[-R, +Err, -In](val self: Handler[R, Err, In, Response]) extends AnyVal {
    def body(implicit trace: Trace): Handler[R, Err, In, Body] =
      self.map(_.body)

    def contentLength(implicit trace: Trace): Handler[R, Err, In, Option[Long]] =
      self.map(_.contentLength)

    def contentType(implicit trace: Trace): Handler[R, Err, In, Option[String]] =
      headerValue(HttpHeaderNames.CONTENT_TYPE)

    def headers(implicit trace: Trace): Handler[R, Err, In, Headers] =
      self.map(_.headers)

    def headerValue(name: CharSequence)(implicit trace: Trace): Handler[R, Err, In, Option[String]] =
      self.map(_.headerValue(name))

    def status(implicit trace: Trace): Handler[R, Err, In, Status] =
      self.map(_.status)
  }

  final class ContraFlatMap[-R, +Err, -In, +Out, In1](val self: Handler[R, Err, In, Out]) extends AnyVal {
    def apply[R1 <: R, Err1 >: Err](f: In1 => Handler[R1, Err1, Any, In])(implicit
      trace: Trace,
    ): Handler[R1, Err1, In1, Out] =
      fromFunctionHandler(f) >>> self
  }

  final class FromFunction[In](val self: Unit) extends AnyVal {
    def apply[Out](f: In => Out)(implicit trace: Trace): Handler[Any, Nothing, In, Out] =
      (in: In) =>
        try {
          HExit.succeed(f(in))
        } catch {
          case error: Throwable => HExit.die(error)
        }
  }

  final class FromFunctionHandler[In](val self: Unit) extends AnyVal {
    def apply[R, Err, Out](f: In => Handler[R, Err, In, Out])(implicit trace: Trace): Handler[R, Err, In, Out] =
      (in: In) => f(in)(in)
  }

  final class FromFunctionHExit[In](val self: Unit) extends AnyVal {
    def apply[R, Err, Out](f: In => HExit[R, Err, Out])(implicit trace: Trace): Handler[R, Err, In, Out] =
      (in: In) =>
        try {
          f(in)
        } catch {
          case error: Throwable => HExit.die(error)
        }
  }

  final class FromFunctionZIO[In](val self: Unit) extends AnyVal {
    def apply[R, Err, Out](f: In => ZIO[R, Err, Out])(implicit trace: Trace): Handler[R, Err, In, Out] =
      (in: In) => HExit.fromZIO(f(in))
  }
}
