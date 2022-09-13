package zio.http.middleware

import zio.{Clock, ZIO}
import zio.http.{Middleware, Patch}

import java.io.IOException

private[zio] trait RequestLogging {

  final def requestLogging: HttpMiddleware[Any, IOException] =
    Middleware.interceptZIOPatch(req => Clock.nanoTime.map(start => (req.method, req.url, start))) {
      case (response, (method, url, start)) =>
        for {
          end <- Clock.nanoTime
          _   <- ZIO
            .logInfo(s"${response.status.asJava.code()} ${method} ${url.encode} ${(end - start) / 1000000}ms") // TODO
        } yield Patch.empty
    }

}
