/*
 * Copyright 2021 - 2023 Sporta Technologies PVT LTD & the ZIO HTTP contributors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package zio.http

import java.io.{File, FileNotFoundException}
import java.nio.file.Paths
import java.util.zip.ZipFile

import zio._

import zio.stream.ZStream

import zio.http.Header.HeaderType
import zio.http.Http.{Empty, FailedErrorHandler, Route}
import zio.http.socket.{SocketApp, WebSocketChannel}
import zio.http.{Header, Headers, MediaType, Status}

sealed trait Http[-R, +Err, -In, +Out] { self =>

  /**
   * Pipes the output of one app into the other
   */
  final def >>>[R1 <: R, Err1 >: Err, In1 >: Out, Out1](
    handler: Handler[R1, Err1, In1, Out1],
    errorOutputMapper: Out => Out1,
  )(implicit trace: Trace): Http[R1, Err1, In, Out1] =
    self match {
      case Http.Empty(errorHandler)                =>
        Http.Empty(errorHandler.map(_.andThen(_.map(errorOutputMapper))))
      case Http.Static(firstHandler, errorHandler) =>
        Http.Static(firstHandler.andThen(handler), errorHandler.map(_.andThen(_.map(errorOutputMapper))))
      case route: Http.Route[R, Err, In, Out]      =>
        new Route[R1, Err1, In, Out1] {
          override def run(in: In): ZIO[R1, Err1, Http[R1, Err1, In, Out1]] =
            route.run(in).map(_ >>> (handler, errorOutputMapper))

          override val errorHandler: Option[Cause[Nothing] => ZIO[R1, Nothing, Out1]] =
            route.errorHandler.map(_.andThen(_.map(errorOutputMapper)))
        }
    }

  final def >>>[R1 <: R, Err1 >: Err, In1 >: Out, Out1](
    handler: Handler[R1, Err1, In1, Out1],
  )(implicit ev: Unit =:= Out1, trace: Trace): Http[R1, Err1, In, Out1] = {
    val errorOutputMapper: Out => Out1 = (_: Out) => ev(())
    self match {
      case Http.Empty(errorHandler)                =>
        Http.Empty(errorHandler.map(_.andThen(_.map(errorOutputMapper))))
      case Http.Static(firstHandler, errorHandler) =>
        Http.Static(firstHandler.andThen(handler), errorHandler.map(_.andThen(_.map(errorOutputMapper))))
      case route: Http.Route[R, Err, In, Out]      =>
        new Route[R1, Err1, In, Out1] {
          override def run(in: In): ZIO[R1, Err1, Http[R1, Err1, In, Out1]] =
            route.run(in).map(_ >>> (handler, errorOutputMapper))

          override val errorHandler: Option[Cause[Nothing] => ZIO[R1, Nothing, Out1]] =
            route.errorHandler.map(_.andThen(_.map(errorOutputMapper)))
        }
    }
  }

  final def @@[
    LowerEnv <: UpperEnv,
    UpperEnv <: R,
    LowerErr >: Err,
    UpperErr >: LowerErr,
    In1 <: In,
  ](
    aspect: HttpAppMiddleware.Contextual[LowerEnv, UpperEnv, LowerErr, UpperErr],
  )(implicit
    trace: Trace,
    in: In1 <:< Request,
    out: Out <:< Response,
  ): Http[aspect.OutEnv[UpperEnv], aspect.OutErr[LowerErr], Request, Response] =
    aspect(self.asInstanceOf[Http[R, Err, Request, Response]])

  /**
   * Combines two Http into one.
   */
  final def ++[R1 <: R, Err1 >: Err, In1 <: In, Out1 >: Out](
    that: Http[R1, Err1, In1, Out1],
  )(implicit trace: Trace): Http[R1, Err1, In1, Out1] =
    self.defaultWith(that)

  final def catchAllZIO[R1 <: R, Err1, Out1 >: Out](
    f: Err => ZIO[R1, Err1, Out1],
  )(implicit trace: Trace): Http[R1, Err1, In, Out1] =
    self match {
      case Http.Empty(errorHandler)           => Http.Empty(errorHandler)
      case Http.Static(handler, errorHandler) =>
        Http.Static(handler.catchAll(err => Handler.fromZIO(f(err))), errorHandler)
      case route: Route[R, Err, In, Out]      =>
        new Route[R1, Err1, In, Out1] {
          override def run(in: In): ZIO[R1, Err1, Http[R1, Err1, In, Out1]] =
            route
              .run(in)
              .map(_.catchAllZIO(f))
              .catchAll(err => f(err).map(out => Handler.succeed(out).toHttp))

          override val errorHandler: Option[Cause[Nothing] => ZIO[R1, Nothing, Out1]] =
            route.errorHandler
        }
    }

  final def catchAllCauseZIO[R1 <: R, Out1 >: Out](f: Cause[Err] => ZIO[R1, Nothing, Out1])(implicit
    trace: Trace,
  ): Http[R1, Nothing, In, Out1] =
    self
      .catchAllZIO(err => f(Cause.fail(err)))
      .withErrorHandler(Some(f))

  /**
   * Named alias for `++`
   */
  final def defaultWith[R1 <: R, Err1 >: Err, In1 <: In, Out1 >: Out](
    that: Http[R1, Err1, In1, Out1],
  )(implicit trace: Trace): Http[R1, Err1, In1, Out1] =
    self match {
      case Http.Empty(_)                 => that
      case static @ Http.Static(_, _)    => static
      case route: Route[R, Err, In, Out] =>
        new Route[R1, Err1, In1, Out1] {
          override def run(in: In1): ZIO[R1, Err1, Http[R1, Err1, In1, Out1]] =
            route.run(in).map(_.defaultWith(that))

          override val errorHandler: Option[Cause[Nothing] => ZIO[R1, Nothing, Out1]] =
            route.errorHandler
        }
    }

  private[http] def errorHandler: Option[Cause[Nothing] => ZIO[R, Nothing, Out]]

  /**
   * Transforms the output of the http app
   */
  final def map[Out1](f: Out => Out1)(implicit trace: Trace): Http[R, Err, In, Out1] =
    self match {
      case Http.Empty(errorHandler)           => Http.Empty(errorHandler.map(_.andThen(_.map(f))))
      case Http.Static(handler, errorHandler) => Http.Static(handler.map(f), errorHandler.map(_.andThen(_.map(f))))
      case route: Route[R, Err, In, Out]      =>
        new Route[R, Err, In, Out1] {
          override def run(in: In): ZIO[R, Err, Http[R, Err, In, Out1]] =
            route.run(in).map(_.map(f))

          override val errorHandler: Option[Cause[Nothing] => ZIO[R, Nothing, Out1]] =
            route.errorHandler.map(_.andThen(_.map(f)))
        }
    }

  /**
   * Transforms the failure of the http app
   */
  final def mapError[Err1](f: Err => Err1)(implicit trace: Trace): Http[R, Err1, In, Out] =
    self match {
      case empty @ Http.Empty(_)              => empty
      case Http.Static(handler, errorHandler) => Http.Static(handler.mapError(f), errorHandler)
      case route: Route[R, Err, In, Out]      =>
        new Route[R, Err1, In, Out] {
          override def run(in: In): ZIO[R, Err1, Http[R, Err1, In, Out]] =
            route.run(in).mapBoth(f, _.mapError(f))

          override val errorHandler: Option[Cause[Nothing] => ZIO[R, Nothing, Out]] =
            route.errorHandler
        }
    }

  /**
   * Transforms the output of the http effectfully
   */
  final def mapZIO[R1 <: R, Err1 >: Err, Out1](f: Out => ZIO[R1, Err1, Out1])(implicit
    trace: Trace,
  ): Http[R1, Err1, In, Out1] =
    self match {
      case Http.Empty(errorHandler)           =>
        Http.Empty(errorHandler.map(_.andThen(_.flatMap(f).orDieWith(FailedErrorHandler.apply))))
      case Http.Static(handler, errorHandler) =>
        Http.Static(
          handler.mapZIO(f),
          errorHandler.map(_.andThen(_.flatMap(f).orDieWith(FailedErrorHandler.apply))),
        )
      case route: Route[R, Err, In, Out]      =>
        new Route[R1, Err1, In, Out1] {
          override def run(in: In): ZIO[R, Err1, Http[R1, Err1, In, Out1]] =
            route.run(in).map(_.mapZIO(f))

          override val errorHandler: Option[Cause[Nothing] => ZIO[R1, Nothing, Out1]] =
            route.errorHandler.map(_.andThen(_.flatMap(f).orDieWith(FailedErrorHandler.apply)))
        }
    }

  /**
   * Transforms the failure of the http app effectfully
   */
  final def mapErrorZIO[R1 <: R, Err1, Out1 >: Out](
    f: Err => ZIO[R1, Err1, Out1],
  )(implicit trace: Trace): Http[R1, Err1, In, Out1] =
    self match {
      case empty @ Http.Empty(_)              => empty
      case Http.Static(handler, errorHandler) => Http.Static(handler.mapErrorZIO(f), errorHandler)
      case route: Route[R, Err, In, Out]      =>
        new Route[R1, Err1, In, Out1] {
          override def run(in: In): ZIO[R, Err1, Http[R1, Err1, In, Out1]] =
            route
              .run(in)
              .map(_.mapErrorZIO(f))
              .catchAll(err => ZIO.succeed(Handler.fromZIO(f(err)).toHttp))

          override val errorHandler: Option[Cause[Nothing] => ZIO[R1, Nothing, Out1]] =
            route.errorHandler
        }
    }

  /**
   * Provides the environment to Http.
   */
  final def provideEnvironment(r: ZEnvironment[R])(implicit trace: Trace): Http[Any, Err, In, Out] =
    self match {
      case Http.Empty(errorHandler)           =>
        Http.Empty(errorHandler.map(_.andThen(_.provideEnvironment(r))))
      case Http.Static(handler, errorHandler) =>
        Http.Static(handler.provideEnvironment(r), errorHandler.map(_.andThen(_.provideEnvironment(r))))
      case route: Route[R, Err, In, Out]      =>
        new Route[Any, Err, In, Out] {
          override def run(in: In): ZIO[Any, Err, Http[Any, Err, In, Out]] =
            route.run(in).map(_.provideEnvironment(r)).provideEnvironment(r)

          override val errorHandler: Option[Cause[Nothing] => ZIO[Any, Nothing, Out]] =
            route.errorHandler.map(_.andThen(_.provideEnvironment(r)))
        }
    }

  /**
   * Provides layer to Http.
   */
  final def provideLayer[Err1 >: Err, R0](layer: ZLayer[R0, Err1, R])(implicit
    trace: Trace,
  ): Http[R0, Err1, In, Out] =
    self match {
      case Http.Empty(errorHandler)           =>
        Http.Empty(errorHandler.map(_.andThen(_.provideLayer(layer).orDieWith(FailedErrorHandler.apply))))
      case Http.Static(handler, errorHandler) =>
        Http.Static(
          handler.provideLayer(layer),
          errorHandler.map(_.andThen(_.provideLayer(layer).orDieWith(FailedErrorHandler.apply))),
        )
      case route: Route[R, Err, In, Out]      =>
        new Route[R0, Err1, In, Out] {
          override def run(in: In): ZIO[R0, Err1, Http[R0, Err1, In, Out]] =
            ZIO
              .environment[R]
              .flatMap { env =>
                route.run(in).map(_.provideEnvironment(env))
              }
              .provideLayer(layer)

          override val errorHandler: Option[Cause[Nothing] => ZIO[R0, Nothing, Out]] =
            route.errorHandler.map(_.andThen(_.provideLayer(layer).orDieWith(FailedErrorHandler.apply)))
        }
    }

  /**
   * Provides some of the environment to Http.
   */
  final def provideSomeEnvironment[R1](f: ZEnvironment[R1] => ZEnvironment[R])(implicit
    trace: Trace,
  ): Http[R1, Err, In, Out] =
    self match {
      case Http.Empty(errorHandler)           =>
        Http.Empty(errorHandler.map(_.andThen(_.provideSomeEnvironment(f))))
      case Http.Static(handler, errorHandler) =>
        Http.Static(handler.provideSomeEnvironment(f), errorHandler.map(_.andThen(_.provideSomeEnvironment(f))))
      case route: Route[R, Err, In, Out]      =>
        new Route[R1, Err, In, Out] {
          override def run(in: In): ZIO[R1, Err, Http[R1, Err, In, Out]] =
            route.run(in).map(_.provideSomeEnvironment(f)).provideSomeEnvironment(f)

          override val errorHandler: Option[Cause[Nothing] => ZIO[R1, Nothing, Out]] =
            route.errorHandler.map(_.andThen(_.provideSomeEnvironment(f)))
        }
    }

  /**
   * Provides some of the environment to Http leaving the remainder `R0`.
   */
  final def provideSomeLayer[R0, R1: Tag, Err1 >: Err](
    layer: ZLayer[R0, Err1, R1],
  )(implicit ev: R0 with R1 <:< R, trace: Trace): Http[R0, Err1, In, Out] =
    self match {
      case Http.Empty(errorHandler)           =>
        Http.Empty(errorHandler.map(_.andThen(_.provideSomeLayer(layer).orDieWith(FailedErrorHandler.apply))))
      case Http.Static(handler, errorHandler) =>
        Http.Static(
          handler.provideSomeLayer(layer),
          errorHandler.map(_.andThen(_.provideSomeLayer(layer).orDieWith(FailedErrorHandler.apply))),
        )
      case route: Route[R, Err, In, Out]      =>
        new Route[R0, Err1, In, Out] {
          override def run(in: In): ZIO[R0, Err1, Http[R0, Err1, In, Out]] =
            ZIO
              .environment[R]
              .flatMap { env =>
                route.run(in).map(_.provideEnvironment(env))
              }
              .provideSomeLayer(layer)

          override val errorHandler: Option[Cause[Nothing] => ZIO[R0, Nothing, Out]] =
            route.errorHandler.map(_.andThen(_.provideSomeLayer(layer).orDieWith(FailedErrorHandler.apply)))
        }
    }

  final def runHandler(in: In)(implicit trace: Trace): ZIO[R, Err, Option[Handler[R, Err, In, Out]]] =
    self match {
      case Http.Empty(_)                 => Exit.succeed(None)
      case Http.Static(handler, _)       => Exit.succeed(Some(handler))
      case route: Route[R, Err, In, Out] => route.run(in).flatMap(_.runHandler(in))
    }

  private[http] final def runServerErrorOrNull(
    cause: Cause[Nothing],
  )(implicit unsafe: Unsafe, trace: Trace): ZIO[R, Nothing, Out] = // NOTE: Out can be null
    self.errorHandler.fold(Exit.succeed(null).asInstanceOf[ZIO[R, Nothing, Out]])(_(cause))

  final def runZIOOrNull(in: In)(implicit unsafe: Unsafe, trace: Trace): ZIO[R, Err, Out] = // NOTE: Out can be null
    self match {
      case Http.Empty(_)                      =>
        Exit.succeed(null).asInstanceOf[ZIO[R, Err, Out]]
      case Http.Static(handler, errorHandler) =>
        errorHandler match {
          case Some(errorHandler) =>
            handler(in).catchAllDefect { defect =>
              errorHandler(Cause.die(defect))
            }
          case None               =>
            handler(in)
        }
      case route: Route[R, Err, In, Out]      =>
        route.errorHandler match {
          case Some(errorHandler) =>
            route.run(in).flatMap(_.runZIOOrNull(in)).catchAllDefect { defect =>
              errorHandler(Cause.die(defect))
            }
          case None               =>
            route.run(in).flatMap(_.runZIOOrNull(in))
        }
    }

  final def runZIO(in: In)(implicit trace: Trace): ZIO[R, Option[Err], Out] =
    runZIOOrNull(in)(Unsafe.unsafe, trace)
      .mapError(Some(_))
      .flatMap { out =>
        if (out != null) Exit.succeed(out)
        else Exit.fail(None)
      }

  /**
   * Returns an Http that effectfully peeks at the success, failed, defective or
   * empty value of this Http.
   */
  final def tapAllZIO[R1 <: R, Err1 >: Err](
    onFailure: Cause[Err] => ZIO[R1, Err1, Any],
    onSuccess: Out => ZIO[R1, Err1, Any],
    onUnhandled: ZIO[R1, Err1, Any],
  )(implicit trace: Trace): Http[R1, Err1, In, Out] =
    self match {
      case Http.Empty(errorHandler)           => Http.fromHttpZIO[In] { _ => onUnhandled.as(Empty(errorHandler)) }
      case Http.Static(handler, errorHandler) => Http.Static(handler.tapAllZIO(onFailure, onSuccess), errorHandler)
      case route: Route[R, Err, In, Out]      =>
        new Route[R1, Err1, In, Out] {
          override def run(in: In): ZIO[R, Err1, Http[R1, Err1, In, Out]] =
            route.run(in).map(_.tapAllZIO(onFailure, onSuccess, onUnhandled))

          override val errorHandler: Option[Cause[Nothing] => ZIO[R1, Nothing, Out]] =
            route.errorHandler
        }
    }

  final def tapErrorCauseZIO[R1 <: R, Err1 >: Err](
    f: Cause[Err] => ZIO[R1, Err1, Any],
  )(implicit trace: Trace): Http[R1, Err1, In, Out] =
    self.tapAllZIO(f, _ => ZIO.unit, ZIO.unit)

  /**
   * Returns an Http that effectfully peeks at the failure of this Http.
   */
  final def tapErrorZIO[R1 <: R, Err1 >: Err](
    f: Err => ZIO[R1, Err1, Any],
  )(implicit trace: Trace): Http[R1, Err1, In, Out] =
    self.tapAllZIO(cause => cause.failureOption.fold[ZIO[R1, Err1, Any]](ZIO.unit)(f), _ => ZIO.unit, ZIO.unit)

  final def tapUnhandledZIO[R1 <: R, Err1 >: Err](
    f: ZIO[R1, Err1, Any],
  )(implicit trace: Trace): Http[R1, Err1, In, Out] =
    self.tapAllZIO(_ => ZIO.unit, _ => ZIO.unit, f)

  /**
   * Returns an Http that effectfully peeks at the success of this Http.
   */
  final def tapZIO[R1 <: R, Err1 >: Err](f: Out => ZIO[R1, Err1, Any])(implicit
    trace: Trace,
  ): Http[R1, Err1, In, Out] =
    self.tapAllZIO(_ => ZIO.unit, f, ZIO.unit)

  final def toHandler[R1 <: R, Err1 >: Err, In1 <: In, Out1 >: Out](default: Handler[R1, Err1, In1, Out1])(implicit
    trace: Trace,
  ): Handler[R1, Err1, In1, Out1] =
    self match {
      case Http.Empty(_)                 => default
      case Http.Static(handler, _)       => handler
      case route: Route[R, Err, In, Out] =>
        Handler
          .fromFunctionZIO[In1] { in =>
            route.run(in).map(_.toHandler(default))
          }
          .flatten
    }

  /**
   * Converts an Http into a websocket application
   */
  final def toSocketApp(implicit
    ev1: WebSocketChannel <:< In,
    ev2: Err <:< Throwable,
    trace: Trace,
  ): SocketApp[R] =
    SocketApp(event =>
      self.runZIO(event).catchAll {
        case Some(value) => ZIO.fail(value)
        case None        => ZIO.unit
      },
    )

  /**
   * Applies Http based only if the condition function evaluates to true
   */
  final def when[In1 <: In](f: In1 => Boolean)(implicit trace: Trace): Http[R, Err, In1, Out] =
    Http.fromHttp[In1] { in =>
      try {
        if (f(in)) self else Empty(self.errorHandler)
      } catch {
        case failure: Throwable => Http.fromHandler(Handler.die(failure)).withErrorHandler(self.errorHandler)
      }
    }

  final def whenZIO[R1 <: R, Err1 >: Err, In1 <: In](
    f: In1 => ZIO[R1, Err1, Boolean],
  )(implicit trace: Trace): Http[R1, Err1, In1, Out] =
    Http.fromHttpZIO { (in: In1) =>
      f(in).map {
        case true  => self
        case false => Empty(self.errorHandler)
      }
    }

  final def withDefaultErrorResponse(implicit trace: Trace, ev1: Request <:< In, ev2: Out <:< Response): App[R] =
    self.mapError { _ =>
      Response(status = Status.InternalServerError)
    }.asInstanceOf[App[R]]

  final private[http] def withErrorHandler[R1 <: R, Out1 >: Out](
    newErrorHandler: Option[Cause[Nothing] => ZIO[R1, Nothing, Out1]],
  ): Http[R1, Err, In, Out1] =
    self match {
      case Http.Empty(_)                   => Http.Empty(newErrorHandler)
      case Http.Static(handler, _)         => Http.Static(handler, newErrorHandler)
      case route: Route[R1, Err, In, Out1] =>
        new Route[R1, Err, In, Out1] {
          override def run(in: In): ZIO[R1, Err, Http[R1, Err, In, Out1]] =
            route.run(in)

          override val errorHandler: Option[Cause[Nothing] => ZIO[R1, Nothing, Out1]] = newErrorHandler
        }
    }
}

object Http {

  final case class Empty[-R, +Out](errorHandler: Option[Cause[Nothing] => ZIO[R, Nothing, Out]])
      extends Http[R, Nothing, Any, Out]

  final case class Static[-R, +Err, -In, +Out](
    handler: Handler[R, Err, In, Out],
    errorHandler: Option[Cause[Nothing] => ZIO[R, Nothing, Out]],
  ) extends Http[R, Err, In, Out]

  sealed trait Route[-R, +Err, -In, +Out] extends Http[R, Err, In, Out] {
    def run(in: In): ZIO[R, Err, Http[R, Err, In, Out]]
    val errorHandler: Option[Cause[Nothing] => ZIO[R, Nothing, Out]]
  }

  /**
   * Creates an HTTP app which accepts a request and produces response.
   */
  def collect[In]: Collect[In] = new Collect[In](())

  /**
   * Create an HTTP app from a partial function from A to HExit[R,E,B]
   */
  def collectExit[In]: CollectExit[In] = new CollectExit[In](())

  def collectHandler[In]: CollectHandler[In] = new CollectHandler[In](())

  /**
   * Create an HTTP app from a partial function from A to Http[R,E,A,B]
   */
  def collectHttp[In]: CollectHttp[In] = new CollectHttp[In](())

  /**
   * Creates an HTTP app which accepts a request and produces response
   * effectfully.
   */
  def collectZIO[In]: CollectZIO[In] = new CollectZIO[In](())

  /**
   * Creates an empty Http value
   */
  val empty: Http[Any, Nothing, Any, Nothing] = Empty(None)

  /**
   * Creates an Http app from the contents of a file.
   */
  def fromFile(file: => File)(implicit trace: Trace): Http[Any, Throwable, Any, Response] =
    fromFileZIO(ZIO.succeed(file))

  /**
   * Creates an Http app from the contents of a file which is produced from an
   * effect. The operator automatically adds the content-length and content-type
   * headers if possible.
   */
  def fromFileZIO[R](getFile: ZIO[R, Throwable, File])(implicit
    trace: Trace,
  ): Http[R, Throwable, Any, Response] =
    Http.fromOptionalHandlerZIO { (_: Any) =>
      getFile.mapError(Some(_)).flatMap { file =>
        ZIO.attempt {
          if (file.isFile) {
            val length   = Headers(Header.ContentLength(file.length()))
            val response = http.Response(headers = length, body = Body.fromFile(file))
            val pathName = file.toPath.toString

            // Set MIME type in the response headers. This is only relevant in
            // case of RandomAccessFile transfers as browsers use the MIME type,
            // not the file extension, to determine how to process a URL.
            // {{{<a href="MSDN Doc">https://developer.mozilla.org/en-US/docs/Web/HTTP/Headers/Content-Type</a>}}}
            Some(
              Handler.succeed(
                determineMediaType(pathName).fold(response)(mediaType =>
                  response.withHeader(Header.ContentType(mediaType)),
                ),
              ),
            )
          } else None
        }.mapError(Some(_)).flatMap {
          case Some(value) => ZIO.succeed(value)
          case None        => ZIO.fail(None)
        }
      }
    }

  def fromHandler[R, Err, In, Out](handler: Handler[R, Err, In, Out]): Http[R, Err, In, Out] =
    Static(handler, None)

  def fromHandlerZIO[In] = new FromHandlerZIO[In](())

  def fromHttp[In]: FromHttp[In] = new FromHttp[In](())

  def fromHttpZIO[In]: FromHttpZIO[In] = new FromHttpZIO[In](())

  def fromOptionalHandler[In]: FromOptionalHandler[In] = new FromOptionalHandler[In](())

  def fromOptionalHandlerZIO[In]: FromOptionalHandlerZIO[In] = new FromOptionalHandlerZIO[In](())

  /**
   * Creates an HTTP that can serve files on the give path.
   */
  def fromPath(head: String, tail: String*)(implicit trace: Trace): Http[Any, Throwable, Any, Response] =
    fromFile(Paths.get(head, tail: _*).toFile)

  /**
   * Creates an Http app from a resource path
   */
  def fromResource(path: String)(implicit trace: Trace): Http[Any, Throwable, Any, Response] =
    Http.fromHttpZIO { (_: Any) =>
      ZIO
        .attemptBlocking(getClass.getClassLoader.getResource(path))
        .map { resource =>
          if (resource == null) Http.empty
          else fromResourceWithURL(resource)
        }
    }

  /**
   * Attempts to retrieve files from the classpath.
   */
  def getResource(path: String)(implicit trace: Trace): Http[Any, Throwable, Any, java.net.URL] =
    Http.fromOptionalHandlerZIO { _ =>
      ZIO
        .attemptBlocking(getClass.getClassLoader.getResource(path))
        .mapError(Some(_))
        .flatMap { resource =>
          if (resource == null) ZIO.fail(None)
          else ZIO.succeed(Handler.succeed(resource))
        }
    }

  /**
   * Attempts to retrieve files from the classpath.
   */
  def getResourceAsFile(path: String)(implicit trace: Trace): Http[Any, Throwable, Any, File] =
    getResource(path).map(url => new File(url.getPath))

  final class Collect[In](val self: Unit) extends AnyVal {
    def apply[Out](pf: PartialFunction[In, Out]): Http[Any, Nothing, In, Out] =
      Http.collectHandler[In].apply(pf.andThen(Handler.succeed(_)))
  }

  final class CollectHandler[In](val self: Unit) extends AnyVal {
    def apply[R, Err, Out](pf: PartialFunction[In, Handler[R, Err, In, Out]]): Http[R, Err, In, Out] =
      new Route[R, Err, In, Out] {
        override def run(in: In): ZIO[R, Err, Http[R, Err, In, Out]] =
          try {
            Exit.succeed {
              val handler = pf.applyOrElse(in, (_: In) => null)
              if (handler eq null) Empty(None)
              else Static(handler, None)
            }
          } catch {
            case failure: Throwable =>
              Exit.die(failure)
          }

        override val errorHandler: Option[Cause[Nothing] => ZIO[R, Nothing, Out]] = None
      }
  }

  final class CollectExit[In](val self: Unit) extends AnyVal {
    def apply[Err, Out](pf: PartialFunction[In, Exit[Err, Out]]): Http[Any, Err, In, Out] =
      Http.collectHandler[In].apply(pf.andThen(Handler.fromExit(_)))
  }

  final class CollectHttp[In](val self: Unit) extends AnyVal {
    def apply[R, Err, Out](pf: PartialFunction[In, Http[R, Err, In, Out]]): Http[R, Err, In, Out] =
      new Route[R, Err, In, Out] {
        override def run(in: In): ZIO[R, Err, Http[R, Err, In, Out]] =
          Exit.succeed(pf.applyOrElse(in, (_: In) => empty))

        override val errorHandler: Option[Cause[Nothing] => ZIO[R, Nothing, Out]] = None
      }
  }

  final class CollectZIO[In](val self: Unit) extends AnyVal {
    def apply[R, Err, Out](pf: PartialFunction[In, ZIO[R, Err, Out]]): Http[R, Err, In, Out] =
      Http.collectHandler[In].apply(pf.andThen(Handler.fromZIO(_)))
  }

  final class FromHandlerZIO[In](val self: Unit) extends AnyVal {
    def apply[R, Err, Out](f: In => ZIO[R, Err, Option[Handler[R, Err, In, Out]]])(implicit
      trace: Trace,
    ): Http[R, Err, In, Out] =
      new Route[R, Err, In, Out] {
        override def run(in: In): ZIO[R, Err, Http[R, Err, In, Out]] =
          try {
            f(in).map(_.fold[Http[R, Err, In, Out]](Empty(None))(handler => Static(handler, None)))
          } catch {
            case error: Throwable => Exit.die(error)
          }

        override val errorHandler: Option[Cause[Nothing] => ZIO[R, Nothing, Out]] = None
      }
  }

  final class FromOptionalHandlerZIO[In](val self: Unit) extends AnyVal {
    def apply[R, Err, Out](f: In => ZIO[R, Option[Err], Handler[R, Err, In, Out]])(implicit
      trace: Trace,
    ): Http[R, Err, In, Out] =
      new Route[R, Err, In, Out] {
        override def run(in: In): ZIO[R, Err, Http[R, Err, In, Out]] =
          f(in).map(Static(_, None)).catchAll {
            case None    => ZIO.succeed(Empty(None))
            case Some(e) => ZIO.fail(e)
          }

        override val errorHandler: Option[Cause[Nothing] => ZIO[R, Nothing, Out]] = None
      }
  }

  final class FromOptionalHandler[In](val self: Unit) extends AnyVal {
    def apply[R, Err, Out](f: In => Option[Handler[R, Err, In, Out]])(implicit
      trace: Trace,
    ): Http[R, Err, In, Out] =
      new Route[R, Err, In, Out] {
        override def run(in: In): ZIO[R, Err, Http[R, Err, In, Out]] =
          f(in) match {
            case None    => ZIO.succeed(Empty(None))
            case Some(h) => ZIO.succeed(Static(h, None))
          }

        override val errorHandler: Option[Cause[Nothing] => ZIO[R, Nothing, Out]] = None
      }
  }

  final class FromHttp[In](val self: Unit) extends AnyVal {
    def apply[R, Err, Out](f: In => Http[R, Err, In, Out]): Http[R, Err, In, Out] =
      new Route[R, Err, In, Out] {
        override def run(in: In): ZIO[R, Err, Http[R, Err, In, Out]] =
          Exit.succeed {
            f(in)
          }

        override val errorHandler: Option[Cause[Nothing] => ZIO[R, Nothing, Out]] = None
      }
  }

  final class FromHttpZIO[In](val self: Unit) extends AnyVal {
    def apply[R, Err, Out](f: In => ZIO[R, Err, Http[R, Err, In, Out]]): Http[R, Err, In, Out] =
      new Route[R, Err, In, Out] {
        override def run(in: In): ZIO[R, Err, Http[R, Err, In, Out]] =
          f(in)

        override val errorHandler: Option[Cause[Nothing] => ZIO[R, Nothing, Out]] = None
      }
  }

  implicit class HttpRouteSyntax[R, Err](val self: HttpApp[R, Err]) extends AnyVal {
    def whenPathEq(path: Path)(implicit trace: Trace): HttpApp[R, Err] =
      self.when[Request](_.path == path)

    def whenPathEq(path: String)(implicit trace: Trace): HttpApp[R, Err] =
      self.when[Request](_.path.encode == path)
  }

  final implicit class ResponseOutputSyntax[-R, +Err, -In](val self: Http[R, Err, In, Response]) extends AnyVal {
    def body(implicit trace: Trace): Http[R, Err, In, Body] =
      self.map(_.body)

    def contentLength(implicit trace: Trace): Http[R, Err, In, Option[Header.ContentLength]] =
      self.map(_.header(Header.ContentLength))

    def contentType(implicit trace: Trace): Http[R, Err, In, Option[Header.ContentType]] =
      self.map(_.header(Header.ContentType))

    def header(headerType: HeaderType)(implicit trace: Trace): Http[R, Err, In, Option[headerType.HeaderValue]] =
      self.map(_.header(headerType))

    def headers(implicit trace: Trace): Http[R, Err, In, Headers] =
      self.map(_.headers)

    def rawHeader(name: CharSequence)(implicit trace: Trace): Http[R, Err, In, Option[String]] =
      self.map(_.rawHeader(name))

    def status(implicit trace: Trace): Http[R, Err, In, Status] =
      self.map(_.status)
  }

  private def determineMediaType(filePath: String): Option[MediaType] = {
    filePath.lastIndexOf(".") match {
      case -1 => None
      case i  =>
        // Extract file extension
        val ext = filePath.substring(i + 1)
        MediaType.forFileExtension(ext)
    }
  }

  private[zio] def fromResourceWithURL(
    url: java.net.URL,
  )(implicit trace: Trace): Http[Any, Throwable, Any, Response] = {
    url.getProtocol match {
      case "file" =>
        Http.fromFile(new File(url.getPath))
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
              response
                .withHeader(Header.ContentType(t))
                .withHeader(Header.ContentLength(contentLength))
            }
          }

        Http.fromOptionalHandlerZIO(_ =>
          appZIO.mapBoth(
            {
              case _: FileNotFoundException => None
              case error: Throwable         => Some(error)
            },
            { response =>
              Handler.response(response)
            },
          ),
        )
      case proto  =>
        Handler.fail(new IllegalArgumentException(s"Unsupported protocol: $proto")).toHttp
    }
  }

  final case class FailedErrorHandler[E](error: E) extends Exception(s"Error raised in Http error handler: $error")
}
