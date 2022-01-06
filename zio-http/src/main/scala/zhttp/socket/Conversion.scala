package zhttp.socket

import zhttp.http.Response

trait Conversion {
  import scala.language.implicitConversions

  implicit def asResponse[R, E](app: SocketApp[R]): Response[R, E] = app.asResponse
}
