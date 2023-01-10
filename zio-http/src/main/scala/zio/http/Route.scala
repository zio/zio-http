package zio.http

import io.netty.handler.codec.http.HttpHeaderNames
import zio._
import zio.http.model.{Headers, MediaType, Status}
import zio.http.socket.{SocketApp, WebSocketChannelEvent}
import zio.stream.ZStream

import java.io.{File, FileNotFoundException}
import java.nio.file.Paths
import java.util.zip.ZipFile

trait Route[-R, +Err, -In, +Out] { self =>

  final def >>>[R1 <: R, Err1 >: Err, In1 >: Out, Out1](
    handler: Handler[R1, Err1, In1, Out1],
  ): Route[R1, Err1, In, Out1] =
    Route.fromHandlerHExit[In] { in =>
      self.toHandlerOrNull(in).map { firstHandler =>
        if (firstHandler eq null) null.asInstanceOf[Handler[R1, Err1, In, Out1]]
        else firstHandler.andThen(handler)
      }
    }

  final def @@[R1 <: R, Err1 >: Err, In1 <: In, Out1 >: Out, In2, Out2](
    aspect: RouteAspect[R1, Err1, In1, Out1, In2, Out2],
  ): Route[R1, Err1, In2, Out2] =
    aspect(self)

  final def @@[R1 <: R, Err1 >: Err, In1 <: In, Out1 >: Out, In2 <: In, Out2](
    aspect: HandlerAspect[R1, Err1, In1, Out1, In2, Out2],
  ): Route[R1, Err1, In2, Out2] =
    Route.fromHandlerHExit[In2] { in =>
      self.toHandlerOrNull(in).flatMap { handler =>
        if (handler eq null) HExit.succeed(null)
        else HExit.succeed(aspect(handler))
      }
    }

  final def ++[R1 <: R, Err1 >: Err, In1 <: In, Out1 >: Out](
    that: Route[R1, Err1, In1, Out1],
  ): Route[R1, Err1, In1, Out1] =
    self.defaultWith(that)

  final def defaultWith[R1 <: R, Err1 >: Err, In1 <: In, Out1 >: Out](
    that: Route[R1, Err1, In1, Out1],
  ): Route[R1, Err1, In1, Out1] =
    Route.fromHandlerHExit[In1] { in =>
      self.toHandlerOrNull(in).flatMap { handler =>
        if (handler eq null) that.toHandlerOrNull(in)
        else HExit.succeed(handler)
      }
    }

  final def map[Out1](f: Out => Out1)(implicit trace: Trace): Route[R, Err, In, Out1] =
    Route.fromHandlerHExit[In] { in =>
      self.toHandlerOrNull(in).map { handler =>
        if (handler eq null) null
        else handler.map(f)
      }
    }

  final def mapError[Err1](f: Err => Err1)(implicit trace: Trace): Route[R, Err1, In, Out] =
    Route.fromHandlerHExit[In] { in =>
      self.toHandlerOrNull(in).mapError(f).map { handler =>
        if (handler eq null) null
        else handler.mapError(f)
      }
    }

  final def mapZIO[R1 <: R, Err1 >: Err, Out1](f: Out => ZIO[R1, Err1, Out1])(implicit
    trace: Trace,
  ): Route[R1, Err1, In, Out1] =
    Route.fromHandlerHExit[In] { in =>
      self.toHandlerOrNull(in).map { handler =>
        if (handler eq null) null
        else handler.mapZIO(f)
      }
    }

  final def provideEnvironment(r: ZEnvironment[R])(implicit trace: Trace): Route[Any, Err, In, Out] =
    Route.fromHandlerHExit[In] { in =>
      self
        .toHandlerOrNull(in)
        .map { handler =>
          if (handler ne null) handler.provideEnvironment(r)
          else null
        }
        .provideEnvironment(r)
    }

  final def provideLayer[Err1 >: Err, R0](layer: ZLayer[R0, Err1, R])(implicit
    trace: Trace,
  ): Route[R0, Err1, In, Out] =
    Route.fromHandlerHExit[In] { in =>
      self
        .toHandlerOrNull(in)
        .map { handler =>
          if (handler ne null) handler.provideLayer(layer)
          else null
        }
        .provideLayer(layer)
    }

  final def provideSomeEnvironment[R1](f: ZEnvironment[R1] => ZEnvironment[R])(implicit
    trace: Trace,
  ): Route[R1, Err, In, Out] =
    Route.fromHandlerHExit[In] { in =>
      self
        .toHandlerOrNull(in)
        .map { handler =>
          if (handler ne null) handler.provideSomeEnvironment(f)
          else null
        }
        .provideSomeEnvironment(f)
    }

  final def provideSomeLayer[R0, R1: Tag, Err1 >: Err](
    layer: ZLayer[R0, Err1, R1],
  )(implicit ev: R0 with R1 <:< R, trace: Trace): Route[R0, Err1, In, Out] =
    Route.fromHandlerHExit[In] { in =>
      self
        .toHandlerOrNull(in)
        .map { handler =>
          if (handler ne null) handler.provideSomeLayer(layer)
          else null
        }
        .provideSomeLayer(layer)
    }

  final def tapAllZIO[R1 <: R, Err1 >: Err](
    onFailure: Cause[Err] => ZIO[R1, Err1, Any],
    onSuccess: Out => ZIO[R1, Err1, Any],
    onUnhandled: ZIO[R1, Err1, Any],
  )(implicit trace: Trace): Route[R1, Err1, In, Out] =
    Route.fromHandlerHExit[In] { in =>
      self.toHandlerOrNull(in).flatMap { handler =>
        if (handler eq null) HExit.fromZIO(onUnhandled) *> HExit.succeed(null)
        else HExit.succeed(handler.tapAllZIO(onFailure, onSuccess))
      }
    }

  final def tapErrorCauseZIO[R1 <: R, Err1 >: Err](
    f: Cause[Err] => ZIO[R1, Err1, Any],
  )(implicit trace: Trace): Route[R1, Err1, In, Out] =
    self.tapAllZIO(f, _ => ZIO.unit, ZIO.unit)

  final def tapErrorZIO[R1 <: R, Err1 >: Err](
    f: Err => ZIO[R1, Err1, Any],
  )(implicit trace: Trace): Route[R1, Err1, In, Out] =
    self.tapAllZIO(cause => cause.failureOption.fold[ZIO[R1, Err1, Any]](ZIO.unit)(f), _ => ZIO.unit, ZIO.unit)

  final def tapUnhandledZIO[R1 <: R, Err1 >: Err](
    f: ZIO[R1, Err1, Any],
  )(implicit trace: Trace): Route[R1, Err1, In, Out] =
    self.tapAllZIO(_ => ZIO.unit, _ => ZIO.unit, f)

  final def tapZIO[R1 <: R, Err1 >: Err](f: Out => ZIO[R1, Err1, Any])(implicit
    trace: Trace,
  ): Route[R1, Err1, In, Out] =
    self.tapAllZIO(_ => ZIO.unit, f, ZIO.unit)

  final def toHandler[R1 <: R, Err1 >: Err, In1 <: In, Out1 >: Out](default: Handler[R1, Err1, In1, Out1])(implicit
    trace: Trace,
  ): Handler[R1, Err1, In1, Out1] =
    Handler
      .fromFunctionZIO[In1] { in =>
        self.toHandlerOrNull(in).toZIO.map { handler =>
          if (handler ne null) handler
          else default
        }
      }
      .flatten

  // TODO: unsafe api
  private[zio] def toHandlerOrNull(
    in: In,
  ): HExit[R, Err, Handler[R, Err, In, Out]] // NOTE: Handler[R, Err, In, Out] can be null

  final def toHandler(in: In): HExit[R, Err, Option[Handler[R, Err, In, Out]]] =
    self.toHandlerOrNull(in).map(Option(_))

  // TODO: unsafe api
  final private[zio] def toHExitOrNull(in: In): HExit[R, Err, Out] = { // NOTE: Out can be null
    self.toHandlerOrNull(in).flatMap { handler =>
      if (handler ne null) handler.apply(in)
      else HExit.succeed(null).asInstanceOf[HExit[R, Err, Out]]
    }
  }

  final def toSocketApp(implicit
    ev1: WebSocketChannelEvent <:< In,
    ev2: Err <:< Throwable,
    trace: Trace,
  ): SocketApp[R] =
    SocketApp(event =>
      self.toZIO(event).catchAll {
        case Some(value) => ZIO.fail(value)
        case None        => ZIO.unit
      },
    )

  final def toZIO(in: In)(implicit trace: Trace): ZIO[R, Option[Err], Out] =
    toHExitOrNull(in)
      .mapError(Some(_))
      .flatMap { out =>
        if (out != null) HExit.succeed(out)
        else HExit.fail(None)
      }
      .toZIO

  final def withMiddleware[R1 <: R, In1 <: In, In2, Out2](
    middleware: api.Middleware[R1, In2, Out2],
  )(implicit ev1: In1 <:< Request, ev2: Out <:< Response): HttpRoute[R1, Err] =
    middleware(self.asInstanceOf[HttpRoute[R, Err]])

  final def when[In1 <: In](f: In1 => Boolean)(implicit trace: Trace): Route[R, Err, In1, Out] =
    Route.fromHandlerHExit[In1] { in =>
      if (f(in)) self.toHandlerOrNull(in)
      else HExit.succeed(null)
    }

  final def whenZIO[R1 <: R, Err1 >: Err, In1 <: In](
    f: In1 => ZIO[R1, Err1, Boolean],
  )(implicit trace: Trace): Route[R1, Err1, In1, Out] =
    Route.fromHandlerZIO { (in: In1) =>
      f(in).mapError(Some(_)).flatMap {
        case true  =>
          self.toHandlerOrNull(in).toZIO.mapError(Some(_)).flatMap { handler =>
            if (handler eq null) ZIO.fail(None)
            else ZIO.succeed(handler)
          }
        case false =>
          ZIO.fail(None)
      }
    }

  final def withDefaultErrorResponse(implicit trace: Trace, ev1: Request <:< In, ev2: Out <:< Response): App[R] =
    self.mapError { _ =>
      Response(status = Status.InternalServerError)
    }.asInstanceOf[App[R]]
}

object Route {

  def collect[In]: Collect[In] = new Collect[In](())

  def collectHandler[In]: CollectHandler[In] = new CollectHandler[In](())

  def collectHExit[In]: CollectHExit[In] = new CollectHExit[In](())

  def collectRoute[In]: CollectRoute[In] = new CollectRoute[In](())

  def collectZIO[In]: CollectZIO[In] = new CollectZIO[In](())

  def empty: Route[Any, Nothing, Any, Nothing] =
    (_: Any) => HExit.succeed(null)

  def fromFile(file: => File)(implicit trace: Trace): Route[Any, Throwable, Any, Response] =
    fromFileZIO(ZIO.succeed(file))

  def fromFileZIO[R](getFile: ZIO[R, Throwable, File])(implicit
    trace: Trace,
  ): Route[R, Throwable, Any, Response] =
    Route.fromHandlerZIO { (_: Any) =>
      getFile.mapError(Some(_)).flatMap { file =>
        ZIO.attempt {
          if (file.isFile) {
            val length   = Headers.contentLength(file.length())
            val response = http.Response(headers = length, body = Body.fromFile(file))
            val pathName = file.toPath.toString

            // Set MIME type in the response headers. This is only relevant in
            // case of RandomAccessFile transfers as browsers use the MIME type,
            // not the file extension, to determine how to process a URL.
            // {{{<a href="MSDN Doc">https://developer.mozilla.org/en-US/docs/Web/HTTP/Headers/Content-Type</a>}}}
            Some(Handler.succeed(determineMediaType(pathName).fold(response)(response.withMediaType)))
          } else None
        }.mapError(Some(_)).flatMap {
          case Some(value) => ZIO.succeed(value)
          case None        => ZIO.fail(None)
        }
      }
    }

  def fromHandler[R, Err, In, Out](handler: Handler[R, Err, In, Out])(implicit trace: Trace): Route[R, Err, In, Out] =
    (_: In) => HExit.succeed(handler)

  // TODO: unsafe api
  private[zio] def fromHandlerHExit[In] = new FromHandlerHExit[In](())

  def fromHandlerZIO[In]: FromHandlerZIO[In] = new FromHandlerZIO[In](())

  def fromPath(head: String, tail: String*)(implicit trace: Trace): Route[Any, Throwable, Any, Response] =
    fromFile(Paths.get(head, tail: _*).toFile)

  def fromResource(path: String)(implicit trace: Trace): Route[Any, Throwable, Any, Response] =
    Route.fromRouteZIO { (_: Any) =>
      ZIO
        .attemptBlocking(getClass.getClassLoader.getResource(path))
        .map { resource =>
          if (resource == null) Route.empty
          else fromResourceWithURL(resource)
        }
    }

  def fromRouteZIO[In]: FromRouteZIO[In] = new FromRouteZIO[In](())

  def getResource(path: String)(implicit trace: Trace): Route[Any, Throwable, Any, java.net.URL] =
    Route.fromHandlerZIO { _ =>
      ZIO
        .attemptBlocking(getClass.getClassLoader.getResource(path))
        .mapError(Some(_))
        .flatMap { resource =>
          if (resource == null) ZIO.fail(None)
          else ZIO.succeed(Handler.succeed(resource))
        }
    }

  def getResourceAsFile(path: String)(implicit trace: Trace): Route[Any, Throwable, Any, File] =
    getResource(path).map(url => new File(url.getPath))

  final class Collect[In](val self: Unit) extends AnyVal {
    def apply[Out](pf: PartialFunction[In, Out])(implicit trace: Trace): Route[Any, Nothing, In, Out] =
      Route.collectHandler[In].apply(pf.andThen(Handler.succeed(_)))
  }

  final class CollectHandler[In](val self: Unit) extends AnyVal {
    def apply[R, Err, Out](pf: PartialFunction[In, Handler[R, Err, In, Out]])(implicit
      trace: Trace,
    ): Route[R, Err, In, Out] =
      (in: In) => HExit.succeed(pf.applyOrElse(in, (_: In) => null))
  }

  final class CollectHExit[In](val self: Unit) extends AnyVal {
    def apply[R, Err, Out](pf: PartialFunction[In, HExit[R, Err, Out]])(implicit
      trace: Trace,
    ): Route[R, Err, In, Out] =
      Route.collectHandler[In].apply(pf.andThen(Handler.fromHExit(_)))
  }

  final class CollectRoute[In](val self: Unit) extends AnyVal {
    def apply[R, Err, Out](pf: PartialFunction[In, Route[R, Err, In, Out]])(implicit
      trace: Trace,
    ): Route[R, Err, In, Out] =
      (in: In) =>
        pf.applyOrElse(in, (_: In) => null) match {
          case null  => HExit.succeed(null)
          case route => route.toHandlerOrNull(in)
        }
  }

  final class CollectZIO[In](val self: Unit) extends AnyVal {
    def apply[R, Err, Out](pf: PartialFunction[In, ZIO[R, Err, Out]])(implicit
      trace: Trace,
    ): Route[R, Err, In, Out] =
      Route.collectHandler[In].apply(pf.andThen(Handler.fromZIO(_)))
  }

  private[zio] final class FromHandlerHExit[In](val self: Unit) extends AnyVal {
    def apply[R, Err, Out](f: In => HExit[R, Err, Handler[R, Err, In, Out]])(implicit
      trace: Trace,
    ): Route[R, Err, In, Out] =
      (in: In) =>
        try {
          f(in)
        } catch {
          case error: Throwable => HExit.die(error)
        }
  }

  final class FromHandlerZIO[In](val self: Unit) extends AnyVal {
    def apply[R, Err, Out](f: In => ZIO[R, Option[Err], Handler[R, Err, In, Out]])(implicit
      trace: Trace,
    ): Route[R, Err, In, Out] =
      (in: In) =>
        HExit.fromZIO(f(in).catchAll {
          case None    => ZIO.succeed(null)
          case Some(e) => ZIO.fail(e)
        })
  }

  final class FromRouteZIO[In](val self: Unit) extends AnyVal {
    def apply[R, Err, Out](f: In => ZIO[R, Err, Route[R, Err, In, Out]])(implicit
      trace: Trace,
    ): Route[R, Err, In, Out] =
      (in: In) =>
        HExit.fromZIO {
          f(in).flatMap { route =>
            route.toHandlerOrNull(in).toZIO
          }
        }
  }

  implicit class HttpRouteSyntax[R, Err](val self: HttpRoute[R, Err]) extends AnyVal {
    def whenPathEq(path: Path): HttpRoute[R, Err] =
      self.when[Request](_.path == path)

    def whenPathEq(path: String): HttpRoute[R, Err] =
      self.when[Request](_.path.encode == path)
  }

  final implicit class ResponseOutputSyntax[-R, +Err, -In](val self: Route[R, Err, In, Response]) extends AnyVal {
    def body(implicit trace: Trace): Route[R, Err, In, Body] =
      self.map(_.body)

    def contentLength(implicit trace: Trace): Route[R, Err, In, Option[Long]] =
      self.map(_.contentLength)

    def contentType(implicit trace: Trace): Route[R, Err, In, Option[String]] =
      headerValue(HttpHeaderNames.CONTENT_TYPE)

    def headers(implicit trace: Trace): Route[R, Err, In, Headers] =
      self.map(_.headers)

    def headerValue(name: CharSequence)(implicit trace: Trace): Route[R, Err, In, Option[String]] =
      self.map(_.headerValue(name))

    def status(implicit trace: Trace): Route[R, Err, In, Status] =
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
  )(implicit trace: Trace): Route[Any, Throwable, Any, Response] = {
    url.getProtocol match {
      case "file" =>
        Route.fromFile(new File(url.getPath))
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

        Route.fromHandlerZIO(_ =>
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
        Handler.fail(new IllegalArgumentException(s"Unsupported protocol: $proto")).toRoute
    }
  }

}
