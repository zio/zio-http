package zhttp.http.middleware

import zhttp.http._

private[zhttp] trait Compression {

  def serveCompressed(compression: CompressionFormat): HttpMiddleware[Any, Nothing] =
    serveCompressed(Set(compression))

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

  def serveCompressed(compressions: Set[CompressionFormat]) = {
    Middleware.collect[Request] { case req =>
      req.getHeaders.getAcceptEncoding match {
        case Some(header) =>
          val clientAcceptedEncoding  = parseAcceptEncodingHeaders(header).map(_._1)
          val compressionNames        = compressions.map(_.name)
          // the client accepted encoding are sorted, we want to get the extension of the first
          // encoding that is supported by both the client and the server
          val commonSupportedEncoding = clientAcceptedEncoding.collect {
            case encoding if compressionNames.contains(encoding) => compressions.find(_.name == encoding)
          }.flatten

          // we currently take the first common header, but we would need to try all of them, in order
          commonSupportedEncoding.headOption match {
            case Some(compression) =>
              Middleware.identity
                .contramap[Request] { req =>
                  val path = req.url.path.toString() + compression.extension
                  req.copy(url = req.url.copy(path = Path(path)))
                }
                .andThen(Middleware.addHeaders(Headers.contentEncoding(compression.name))) <> Middleware.identity
            case None              => Middleware.identity
          }
        case None         => Middleware.identity
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
