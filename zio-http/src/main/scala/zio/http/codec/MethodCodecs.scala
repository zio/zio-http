package zio.http.codec

private[codec] trait MethodCodecs {
  import HttpCodecType.Method

  def method(method: zio.http.model.Method): HttpCodec[HttpCodecType.Method, Unit] =
    HttpCodec.Method(SimpleCodec.Specified(method))

  val method: HttpCodec[HttpCodecType.Method, zio.http.model.Method] =
    HttpCodec.Method(SimpleCodec.Unspecified())

  def connect: HttpCodec[Method, Unit] = method(zio.http.model.Method.CONNECT)
  def delete: HttpCodec[Method, Unit]  = method(zio.http.model.Method.DELETE)
  def get: HttpCodec[Method, Unit]     = method(zio.http.model.Method.GET)
  def options: HttpCodec[Method, Unit] = method(zio.http.model.Method.OPTIONS)
  def post: HttpCodec[Method, Unit]    = method(zio.http.model.Method.POST)
  def put: HttpCodec[Method, Unit]     = method(zio.http.model.Method.PUT)
}
