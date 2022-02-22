package zhttp.http.middleware

import zhttp.http._

private[zhttp] trait Compression {

  def serveCompressed[R, E](compression: CompressionFormat): HttpMiddleware[R, E] =
    serveCompressed[R, E](Set(compression))

  // https://developer.mozilla.org/en-US/docs/Glossary/Quality_values
  // https://developer.mozilla.org/en-US/docs/Web/HTTP/Headers/Accept-Encoding
  private[zhttp] def parseAcceptEncodingHeaders(header: CharSequence): List[(String, Double)] = {
    if (header.toString().isEmpty()) List()
    else {
      val acceptedTypes                      = header.toString().split(",").map(_.trim()).toList
      val withWeight: List[(String, Double)] = acceptedTypes.map(accepted =>
        accepted.split(";q=") match {
          case Array(enc, weight) => (enc, weight.toDouble)
          case Array(enc)         => (enc, 1.0)
          case _                  => (accepted, 1.0)
        },
      )
      withWeight.sortBy(_._2).reverse
    }
  }

  private def chain(middlewares: List[HttpMiddleware[Any, Nothing]]): HttpMiddleware[Any, Any] = {
    middlewares
      .foldLeft[HttpMiddleware[Any, Any]](Middleware.fail(())) { case (acc, middleware) =>
        acc.orElse(middleware.flatMap {
          case response if response.status == Status.NOT_FOUND => acc
          case response                                        => Middleware.succeed(response)
        })
      }
  }

  def serveCompressed[R, E](compressions: Set[CompressionFormat]): HttpMiddleware[R, E] = {
    Middleware.collect[Request] { case req =>
      req.headers.acceptEncoding match {
        case Some(header) =>
          val clientAcceptedEncoding = parseAcceptEncodingHeaders(header).map(_._1)
          val compressionNames       = compressions.map(_.name)

          // the client accepted encoding are sorted, we want to get the extension of the first
          // encoding that is supported by both the client and the server
          val commonSupportedEncoding = clientAcceptedEncoding.collect {
            case encoding if compressionNames.contains(encoding) => compressions.find(_.name == encoding)
          }.flatten

          val middlewares = commonSupportedEncoding.map(buildMiddlewareForCompression)

          chain(middlewares).flatMap {
            case response if response.status == Status.NOT_FOUND =>
              println(response)
              Middleware.identity
            case r                                               =>
              println(r)
              Middleware.succeed(r)
          }.orElse(Middleware.identity)
        case None         => Middleware.identity
      }
    }
  }

  private def buildMiddlewareForCompression[R, E](
    compression: CompressionFormat,
  ): HttpMiddleware[R, E] = {
    new Middleware[R, E, Request, Response, Request, Response] {
      override def apply[R1 <: R, E1 >: E](http: Http[R1, E1, Request, Response]): Http[R1, E1, Request, Response] = {
        http
          .contramap[Request] { req =>
            val path = req.url.path.toString() + compression.extension
            req.copy(url = req.url.copy(path = Path(path)))
          }
          .map(_.addHeaders(Headers.contentEncoding(compression.name).addHeaders(Headers.contentType("text/html"))))
      }
    }
  }
}

trait CompressionFormat {
  def name: String
  def extension: String
}

object CompressionFormat {
  final case class Gzip(name: String = HeaderValues.gzip.toString, extension: String = ".gz") extends CompressionFormat
  final case class Brotli(name: String = HeaderValues.br.toString, extension: String = ".br") extends CompressionFormat
}
