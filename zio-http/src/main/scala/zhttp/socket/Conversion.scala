package zhttp.socket

import zhttp.http.Response

trait Conversion {
  import scala.language.implicitConversions

  implicit def asResponse[R, E](app: SocketApp[R, E]): Response[R, E] = app.asResponse()
}
