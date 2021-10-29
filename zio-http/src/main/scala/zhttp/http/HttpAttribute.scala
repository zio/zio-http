package zhttp.http

import zhttp.socket.SocketApp
import zio.NeedsEnv

/**
 * Attribute holder for and Responses
 */
private[zhttp] sealed trait HttpAttribute[-R, +E] extends Product with Serializable { self =>

  def provide(r: R)(implicit ev: NeedsEnv[R]): HttpAttribute[Any, E] = self match {
    case HttpAttribute.Empty       => HttpAttribute.empty
//    case HttpAttribute.Socket(app) => HttpAttribute.Socket(app.provide(r))
  }

}

object HttpAttribute {
  private[zhttp] case object Empty                                   extends HttpAttribute[Any, Nothing]
  private[zhttp] final case class Socket[R, E](app: SocketApp[R, E]) extends HttpAttribute[R, E]

  /**
   * Helper to create an empty HttpData
   */
  def empty: HttpAttribute[Any, Nothing] = Empty

  /**
   * Helper to create Attribute from a SocketApp
   */
  def fromSocket[R, E](socketApp: SocketApp[R, E]): HttpAttribute[R, E] = Socket(socketApp)

}
