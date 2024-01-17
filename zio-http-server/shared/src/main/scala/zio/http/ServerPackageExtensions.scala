package zio.http

import zio.ZIO

trait ServerPackageExtensions {

  /**
   * A smart constructor that attempts to construct a handler from the specified
   * value. If you have difficulty using this function, then please use the
   * constructors on [[zio.http.Handler]] directly.
   */
  def handler[H](handler: => H)(implicit h: ToHandler[H]): Handler[h.Env, h.Err, h.In, h.Out] =
    h.toHandler(handler)

  def handlerTODO(message: String): Handler[Any, Nothing, Any, Nothing] =
    handler(ZIO.dieMessage(message))

  type RequestHandler[-R, +Err] = Handler[R, Err, Request, Response]

  implicit class BodyExtensions(body: Body.type) {
    def fromSocketApp(app: WebSocketApp[Any]): WebsocketBody =
      WebsocketBody(app)
  }

}
