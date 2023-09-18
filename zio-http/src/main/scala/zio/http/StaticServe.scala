package zio.http

import java.io.File

import zio.ZIO

import zio.http.codec.{PathCodec, SegmentCodec}

sealed trait StaticServe[-R, +E, -I, +A] { self =>

  def run(path: Path): Handler[R, E, I, A]

  def orElse[R1 <: R, E1, I1 <: I, A1 >: A](that: => StaticServe[R1, E1, I1, A1]): StaticServe[R1, E1, I1, A1] =
    StaticServe.run { path =>
      self.run(path).orElse(that.run(path))
    }

}

object StaticServe {

  def run[R, E, I, A](f: Path => Handler[R, E, I, A]): StaticServe[R, E, I, A] =
    new StaticServe[R, E, I, A] {
      override def run(path: Path) = f(path)
    }

  def fromFileZIO[R](zio: => ZIO[R, Throwable, File]): StaticServe[R, Throwable, Any, Response] = run { _ =>
    Handler.fromFileZIO(zio)
  }

  def fromDirectory(docRoot: File): StaticServe[Any, Throwable, Any, Response] = run { path =>
    val target = new File(docRoot.getAbsolutePath() + path.encode)
    if (target.getCanonicalPath.startsWith(docRoot.getCanonicalPath)) Handler.fromFile(target)
    else {
      Handler.fromZIO(
        ZIO.logWarning(s"attempt to access file outside of docRoot: ${target.getAbsolutePath}"),
      ) *> Handler.badRequest
    }
  }

  def fromDirectory(docRoot: String): StaticServe[Any, Throwable, Any, Response] =
    fromDirectory(new File(docRoot))

  def fromResource: StaticServe[Any, Throwable, Any, Response] = run { path =>
    Handler.fromResource(path.dropLeadingSlash.encode)
  }

  private def middleware[R, E](
    mountpoint: RoutePattern[_],
    staticServe: StaticServe[R, E, Any, Response],
  ): Middleware[R] =
    new Middleware[R] {

      private def checkFishy(acc: Boolean, segment: String): Boolean = {
        val stop = segment.indexOf('/') >= 0 || segment.indexOf('\\') >= 0 || segment == ".."
        acc || stop
      }

      override def apply[Env1 <: R, Err](routes: Routes[Env1, Err]): Routes[Env1, Err] = {
        val pattern = mountpoint / trailing
        val other   = Routes(
          pattern -> Handler
            .identity[Request]
            .flatMap { request =>
              val isFishy = request.path.segments.foldLeft(false)(checkFishy)
              if (isFishy) {
                Handler.fromZIO(ZIO.logWarning(s"fishy request detected: ${request.path.encode}")) *> Handler.badRequest
              } else {
                val segs   = pattern.pathCodec.segments.collect { case SegmentCodec.Literal(v, _) =>
                  v
                }
                val unnest = segs.foldLeft(Path.empty)(_ / _).addLeadingSlash
                val path   = request.path.unnest(unnest).addLeadingSlash
                staticServe.run(path).sandbox
              }
            },
        )
        routes ++ other
      }
    }

  def middleware[R, E](path: Path, staticServe: StaticServe[R, E, Any, Response]): Middleware[R] =
    middleware(
      Method.GET / path.segments.map(PathCodec.literal).reduceLeft(_ / _),
      staticServe,
    )

}
