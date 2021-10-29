package zhttp.http

import zhttp.socket.SocketApp

/**
 * Attribute holder for and Responses
 */
private[zhttp] sealed trait HttpAttribute[-R, +E] extends Product with Serializable { self =>
  def exit: HttpAttribute.AttributeExit[R, E]                                   = HttpAttribute.exit(self)
  def ++[R1 <: R, E1 >: E](other: HttpAttribute[R1, E1]): HttpAttribute[R1, E1] = HttpAttribute.Combine(self, other)
}

object HttpAttribute {
  private[zhttp] case object Empty extends HttpAttribute[Any, Nothing]
  private[zhttp] case class Combine[R, E](self: HttpAttribute[R, E], other: HttpAttribute[R, E])
      extends HttpAttribute[R, E]

  private[zhttp] final case class Socket[R, E](app: SocketApp[R, E]) extends HttpAttribute[R, E]

  /**
   * Helper to create an empty HttpData
   */
  def empty: HttpAttribute[Any, Nothing] = Empty

  /**
   * Helper to create Attribute from a SocketApp
   */
  def socket[R, E](socketApp: SocketApp[R, E]): HttpAttribute[R, E] = Socket(socketApp)

  private[zhttp] case class AttributeExit[-R, +E](socketApp: SocketApp[R, E] = SocketApp.empty)

  private[zhttp] def exit[R, E](attribute: HttpAttribute[R, E]): AttributeExit[R, E] = {
    def loop(attribute: HttpAttribute[R, E], exit: AttributeExit[R, E]): AttributeExit[R, E] = {
      attribute match {
        case Empty                => exit
        case Combine(self, other) => loop(other, loop(self, exit))
        case Socket(app)          => AttributeExit(socketApp = app)
      }
    }
    loop(attribute, AttributeExit())
  }

}
