package zio.http

import io.netty.handler.codec.http.HttpHeaderNames
import zio._
import zio.http.Http.{Empty, Route}
import zio.http.model.{Headers, MediaType, Status}
import zio.http.socket.{SocketApp, WebSocketChannelEvent}
import zio.stream.ZStream

import java.io.{File, FileNotFoundException}
import java.nio.file.Paths
import java.util.zip.ZipFile

sealed trait Http[-R, +Err, -In, +Out] { self =>
  import Handler.FastZIOSyntax

  /**
   * Pipes the output of one app into the other
   */
  final def >>>[R1 <: R, Err1 >: Err, In1 >: Out, Out1](
    handler: Handler[R1, Err1, In1, Out1],
  ): Http[R1, Err1, In, Out1] =
    self match {
      case Http.Empty                         => Http.empty
      case Http.Static(firstHandler)          => Http.Static(firstHandler.andThen(handler))
      case route: Http.Route[R, Err, In, Out] =>
        new Route[R1, Err1, In, Out1] {
          override def run(in: In): ZIO[R1, Err1, Http[R1, Err1, In, Out1]] =
            route.run(in).fastMap(_ >>> handler)
        }
    }

  final def @@[R1 <: R, Err1 >: Err, In1 <: In, Out1 >: Out, In2, Out2](
    aspect: Middleware[R1, Err1, In1, Out1, In2, Out2],
  ): Http[R1, Err1, In2, Out2] =
    aspect(self)

  /**
   * Combines two Http into one.
   */
  final def ++[R1 <: R, Err1 >: Err, In1 <: In, Out1 >: Out](
    that: Http[R1, Err1, In1, Out1],
  ): Http[R1, Err1, In1, Out1] =
    self.defaultWith(that)

  /**
   * Named alias for `++`
   */
  final def defaultWith[R1 <: R, Err1 >: Err, In1 <: In, Out1 >: Out](
    that: Http[R1, Err1, In1, Out1],
  ): Http[R1, Err1, In1, Out1] =
    self match {
      case Http.Empty                    => that
      case Http.Static(handler)          => Http.Static(handler)
      case route: Route[R, Err, In, Out] =>
        new Route[R1, Err1, In1, Out1] {
          override def run(in: In1): ZIO[R1, Err1, Http[R1, Err1, In1, Out1]] =
            route.run(in).fastMap(_.defaultWith(that))
        }
    }

  /**
   * Transforms the output of the http app
   */
  final def map[Out1](f: Out => Out1): Http[R, Err, In, Out1] =
    self match {
      case Http.Empty                    => Http.Empty
      case Http.Static(handler)          => Http.Static(handler.map(f))
      case route: Route[R, Err, In, Out] =>
        new Route[R, Err, In, Out1] {
          override def run(in: In): ZIO[R, Err, Http[R, Err, In, Out1]] =
            route.run(in).fastMap(_.map(f))
        }
    }

  /**
   * Transforms the failure of the http app
   */
  final def mapError[Err1](f: Err => Err1): Http[R, Err1, In, Out] =
    self match {
      case Http.Empty                    => Http.Empty
      case Http.Static(handler)          => Http.Static(handler.mapError(f))
      case route: Route[R, Err, In, Out] =>
        new Route[R, Err1, In, Out] {
          override def run(in: In): ZIO[R, Err1, Http[R, Err1, In, Out]] =
            route.run(in).fastMapBoth(f, _.mapError(f))
        }
    }

  /**
   * Transforms the output of the http effectfully
   */
  final def mapZIO[R1 <: R, Err1 >: Err, Out1](f: Out => ZIO[R1, Err1, Out1]): Http[R1, Err1, In, Out1] =
    self match {
      case Http.Empty                    => Http.Empty
      case Http.Static(handler)          => Http.Static(handler.mapZIO(f))
      case route: Route[R, Err, In, Out] =>
        new Route[R1, Err1, In, Out1] {
          override def run(in: In): ZIO[R, Err1, Http[R1, Err1, In, Out1]] =
            route.run(in).fastMap(_.mapZIO(f))
        }
    }

  /**
   * Provides the environment to Http.
   */
  final def provideEnvironment(r: ZEnvironment[R]): Http[Any, Err, In, Out] =
    self match {
      case Http.Empty                    => Http.Empty
      case Http.Static(handler)          => Http.Static(handler.provideEnvironment(r))
      case route: Route[R, Err, In, Out] =>
        new Route[Any, Err, In, Out] {
          override def run(in: In): ZIO[Any, Err, Http[Any, Err, In, Out]] =
            route.run(in).fastMap(_.provideEnvironment(r)).provideEnvironment(r)
        }
    }

  /**
   * Provides layer to Http.
   */
  final def provideLayer[Err1 >: Err, R0](layer: ZLayer[R0, Err1, R]): Http[R0, Err1, In, Out] =
    self match {
      case Http.Empty                    => Http.Empty
      case Http.Static(handler)          => Http.Static(handler.provideLayer(layer))
      case route: Route[R, Err, In, Out] =>
        new Route[R0, Err1, In, Out] {
          override def run(in: In): ZIO[R0, Err1, Http[R0, Err1, In, Out]] =
            route.run(in).map(_.provideLayer(layer)).provideLayer(layer)
        }
    }

  /**
   * Provides some of the environment to Http.
   */
  final def provideSomeEnvironment[R1](f: ZEnvironment[R1] => ZEnvironment[R]): Http[R1, Err, In, Out] =
    self match {
      case Http.Empty                    => Http.Empty
      case Http.Static(handler)          => Http.Static(handler.provideSomeEnvironment(f))
      case route: Route[R, Err, In, Out] =>
        new Route[R1, Err, In, Out] {
          override def run(in: In): ZIO[R1, Err, Http[R1, Err, In, Out]] =
            route.run(in).fastMap(_.provideSomeEnvironment(f)).provideSomeEnvironment(f)
        }
    }

  /**
   * Provides some of the environment to Http leaving the remainder `R0`.
   */
  final def provideSomeLayer[R0, R1: Tag, Err1 >: Err](
    layer: ZLayer[R0, Err1, R1],
  )(implicit ev: R0 with R1 <:< R): Http[R0, Err1, In, Out] =
    self match {
      case Http.Empty                    => Http.Empty
      case Http.Static(handler)          => Http.Static(handler.provideSomeLayer(layer))
      case route: Route[R, Err, In, Out] =>
        new Route[R0, Err1, In, Out] {
          override def run(in: In): ZIO[R0, Err1, Http[R0, Err1, In, Out]] =
            route.run(in).fastMap(_.provideSomeLayer(layer)).provideSomeLayer(layer)
        }
    }

  final def runHandler(in: In): ZIO[R, Err, Option[Handler[R, Err, In, Out]]] =
    self match {
      case Http.Empty                    => Exit.succeed(None)
      case Http.Static(handler)          => Exit.succeed(Some(handler))
      case route: Route[R, Err, In, Out] => route.run(in).fastFlatMap(_.runHandler(in))
    }

  final def runZIOOrNull(in: In)(implicit unsafe: Unsafe): ZIO[R, Err, Out] = // NOTE: Out can be null
    self match {
      case Http.Empty                    => Exit.succeed(null).asInstanceOf[ZIO[R, Err, Out]]
      case Http.Static(handler)          => handler(in)
      case route: Route[R, Err, In, Out] => route.run(in).fastFlatMap(_.runZIOOrNull(in))
    }

  final def runZIO(in: In): ZIO[R, Option[Err], Out] = {
    implicit val u = Unsafe.unsafe
    runZIOOrNull(in)
      .mapError(Some(_))
      .flatMap { out =>
        if (out != null) Exit.succeed(out)
        else Exit.fail(None)
      }
  }

  /**
   * Returns an Http that effectfully peeks at the success, failed, defective or
   * empty value of this Http.
   */
  final def tapAllZIO[R1 <: R, Err1 >: Err](
    onFailure: Cause[Err] => ZIO[R1, Err1, Any],
    onSuccess: Out => ZIO[R1, Err1, Any],
    onUnhandled: ZIO[R1, Err1, Any],
  ): Http[R1, Err1, In, Out] =
    self match {
      case Http.Empty                    => Http.fromHttpZIO[In] { _ => onUnhandled.as(Empty) }
      case Http.Static(handler)          => Http.Static(handler.tapAllZIO(onFailure, onSuccess))
      case route: Route[R, Err, In, Out] =>
        new Route[R1, Err1, In, Out] {
          override def run(in: In): ZIO[R, Err1, Http[R1, Err1, In, Out]] =
            route.run(in).fastMap(_.tapAllZIO(onFailure, onSuccess, onUnhandled))
        }
    }

  final def tapErrorCauseZIO[R1 <: R, Err1 >: Err](
    f: Cause[Err] => ZIO[R1, Err1, Any],
  ): Http[R1, Err1, In, Out] =
    self.tapAllZIO(f, _ => ZIO.unit, ZIO.unit)

  /**
   * Returns an Http that effectfully peeks at the failure of this Http.
   */
  final def tapErrorZIO[R1 <: R, Err1 >: Err](
    f: Err => ZIO[R1, Err1, Any],
  ): Http[R1, Err1, In, Out] =
    self.tapAllZIO(cause => cause.failureOption.fold[ZIO[R1, Err1, Any]](ZIO.unit)(f), _ => ZIO.unit, ZIO.unit)

  final def tapUnhandledZIO[R1 <: R, Err1 >: Err](
    f: ZIO[R1, Err1, Any],
  ): Http[R1, Err1, In, Out] =
    self.tapAllZIO(_ => ZIO.unit, _ => ZIO.unit, f)

  /**
   * Returns an Http that effectfully peeks at the success of this Http.
   */
  final def tapZIO[R1 <: R, Err1 >: Err](f: Out => ZIO[R1, Err1, Any]): Http[R1, Err1, In, Out] =
    self.tapAllZIO(_ => ZIO.unit, f, ZIO.unit)

  final def toHandler[R1 <: R, Err1 >: Err, In1 <: In, Out1 >: Out](
    default: Handler[R1, Err1, In1, Out1],
  ): Handler[R1, Err1, In1, Out1] =
    self match {
      case Http.Empty                    => default
      case Http.Static(handler)          => handler
      case route: Route[R, Err, In, Out] =>
        Handler
          .fromFunctionZIO[In1] { in =>
            route.run(in).fastMap(_.toHandler(default))
          }
          .flatten
    }

  /**
   * Converts an Http into a websocket application
   */
  final def toSocketApp(implicit
    ev1: WebSocketChannelEvent <:< In,
    ev2: Err <:< Throwable,
  ): SocketApp[R] =
    SocketApp(event =>
      self.runZIO(event).catchAll {
        case Some(value) => ZIO.fail(value)
        case None        => ZIO.unit
      },
    )

  /**
   * Attaches the provided middleware to the Http app
   */
  final def withMiddleware[R1 <: R, In1 <: In, In2, Out2](
    middleware: api.Middleware[R1, In2, Out2],
  )(implicit ev1: In1 <:< Request, ev2: Out <:< Response): HttpApp[R1, Err] =
    middleware(self.asInstanceOf[HttpApp[R, Err]])

  /**
   * Applies Http based only if the condition function evaluates to true
   */
  final def when[In1 <: In](f: In1 => Boolean): Http[R, Err, In1, Out] =
    Http.fromHttp[In1] { in =>
      try {
        if (f(in)) self else Empty
      } catch {
        case failure: Throwable => Http.fromHandler(Handler.die(failure))
      }
    }

  final def whenZIO[R1 <: R, Err1 >: Err, In1 <: In](
    f: In1 => ZIO[R1, Err1, Boolean],
  ): Http[R1, Err1, In1, Out] =
    Http.fromHttpZIO { (in: In1) =>
      f(in).fastMap {
        case true  => self
        case false => Empty
      }
    }

  final def withDefaultErrorResponse(implicit ev1: Request <:< In, ev2: Out <:< Response): App[R] =
    self.mapError { _ =>
      Response(status = Status.InternalServerError)
    }.asInstanceOf[App[R]]
}

object Http {

  case object Empty extends Http[Any, Nothing, Any, Nothing]

  final case class Static[-R, +Err, -In, +Out](handler: Handler[R, Err, In, Out]) extends Http[R, Err, In, Out]

  sealed trait Route[-R, +Err, -In, +Out] extends Http[R, Err, In, Out] {
    def run(in: In): ZIO[R, Err, Http[R, Err, In, Out]]
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
  def collectRoute[In]: CollectHttp[In] = new CollectHttp[In](())

  /**
   * Creates an HTTP app which accepts a request and produces response
   * effectfully.
   */
  def collectZIO[In]: CollectZIO[In] = new CollectZIO[In](())

  /**
   * Creates an empty Http value
   */
  def empty: Http[Any, Nothing, Any, Nothing] = Empty

  /**
   * Creates an Http app from the contents of a file.
   */
  def fromFile(file: => File): Http[Any, Throwable, Any, Response] =
    fromFileZIO(ZIO.succeed(file))

  /**
   * Creates an Http app from the contents of a file which is produced from an
   * effect. The operator automatically adds the content-length and content-type
   * headers if possible.
   */
  def fromFileZIO[R](getFile: ZIO[R, Throwable, File]): Http[R, Throwable, Any, Response] =
    Http.fromOptionalHandlerZIO { (_: Any) =>
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

  def fromHandler[R, Err, In, Out](handler: Handler[R, Err, In, Out]): Http[R, Err, In, Out] =
    new Route[R, Err, In, Out] {
      override def run(in: In): ZIO[R, Err, Http[R, Err, In, Out]] =
        Exit.succeed(Static(handler))
    }

  private[zio] def fromHandlerZIO[In] = new FromHandlerZIO[In](())

  def fromHttp[In]: FromHttp[In] = new FromHttp[In](())

  def fromHttpZIO[In]: FromHttpZIO[In] = new FromHttpZIO[In](())

  def fromOptionalHandlerZIO[In]: FromOptionalHandlerZIO[In] = new FromOptionalHandlerZIO[In](())

  /**
   * Creates an HTTP that can serve files on the give path.
   */
  def fromPath(head: String, tail: String*): Http[Any, Throwable, Any, Response] =
    fromFile(Paths.get(head, tail: _*).toFile)

  /**
   * Creates an Http app from a resource path
   */
  def fromResource(path: String): Http[Any, Throwable, Any, Response] =
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
  def getResource(path: String): Http[Any, Throwable, Any, java.net.URL] =
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
  def getResourceAsFile(path: String): Http[Any, Throwable, Any, File] =
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
              if (handler eq null) Empty
              else Static(handler)
            }
          } catch {
            case failure: Throwable =>
              Exit.die(failure)
          }
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
          Exit.succeed(pf.applyOrElse(in, (_: In) => Empty))
      }
  }

  final class CollectZIO[In](val self: Unit) extends AnyVal {
    def apply[R, Err, Out](pf: PartialFunction[In, ZIO[R, Err, Out]]): Http[R, Err, In, Out] =
      Http.collectHandler[In].apply(pf.andThen(Handler.fromZIO(_)))
  }

  private[zio] final class FromHandlerZIO[In](val self: Unit) extends AnyVal {
    def apply[R, Err, Out](f: In => ZIO[R, Err, Option[Handler[R, Err, In, Out]]]): Http[R, Err, In, Out] =
      new Route[R, Err, In, Out] {
        override def run(in: In): ZIO[R, Err, Http[R, Err, In, Out]] =
          try {
            f(in).map(_.fold[Http[R, Err, In, Out]](Empty)(Static(_)))
          } catch {
            case error: Throwable => Exit.die(error)
          }
      }
  }

  final class FromOptionalHandlerZIO[In](val self: Unit) extends AnyVal {
    def apply[R, Err, Out](f: In => ZIO[R, Option[Err], Handler[R, Err, In, Out]]): Http[R, Err, In, Out] =
      new Route[R, Err, In, Out] {
        override def run(in: In): ZIO[R, Err, Http[R, Err, In, Out]] =
          f(in).map(Static(_)).catchAll {
            case None    => ZIO.succeed(Empty)
            case Some(e) => ZIO.fail(e)
          }
      }
  }

  final class FromHttp[In](val self: Unit) extends AnyVal {
    def apply[R, Err, Out](f: In => Http[R, Err, In, Out]): Http[R, Err, In, Out] =
      new Route[R, Err, In, Out] {
        override def run(in: In): ZIO[R, Err, Http[R, Err, In, Out]] =
          Exit.succeed {
            f(in)
          }
      }
  }

  final class FromHttpZIO[In](val self: Unit) extends AnyVal {
    def apply[R, Err, Out](f: In => ZIO[R, Err, Http[R, Err, In, Out]]): Http[R, Err, In, Out] =
      new Route[R, Err, In, Out] {
        override def run(in: In): ZIO[R, Err, Http[R, Err, In, Out]] =
          f(in)
      }
  }

  implicit class HttpRouteSyntax[R, Err](val self: HttpApp[R, Err]) extends AnyVal {
    def whenPathEq(path: Path): HttpApp[R, Err] =
      self.when[Request](_.path == path)

    def whenPathEq(path: String): HttpApp[R, Err] =
      self.when[Request](_.path.encode == path)
  }

  final implicit class ResponseOutputSyntax[-R, +Err, -In](val self: Http[R, Err, In, Response]) extends AnyVal {
    def body: Http[R, Err, In, Body] =
      self.map(_.body)

    def contentLength: Http[R, Err, In, Option[Long]] =
      self.map(_.contentLength)

    def contentType: Http[R, Err, In, Option[String]] =
      headerValue(HttpHeaderNames.CONTENT_TYPE)

    def headers: Http[R, Err, In, Headers] =
      self.map(_.headers)

    def headerValue(name: CharSequence): Http[R, Err, In, Option[String]] =
      self.map(_.headerValue(name))

    def status: Http[R, Err, In, Status] =
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
  ): Http[Any, Throwable, Any, Response] = {
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
              response.withMediaType(t).withContentLength(contentLength)
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

}
