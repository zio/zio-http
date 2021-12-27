package zhttp.service

sealed trait ServerStart { self =>
  import ServerStart._

  def apply(c: Config = Config()): Config = self match {
    case Concat(self, other) => other(self(c))
    case Empty               => c
    case Port(p)             => c.copy(port = p)
  }

  /**
   * Combines two ServerStart into one.
   */
  def ++(other: ServerStart): ServerStart =
    Concat(self, other)
}

object ServerStart {
  final case class Config(
    port: Int = 0,
  )

  private final case class Port(p: Int)                                  extends ServerStart
  private case object Empty                                              extends ServerStart
  private final case class Concat(self: ServerStart, other: ServerStart) extends ServerStart

  def empty: ServerStart = Empty

  def port(p: Int): ServerStart = Port(p)
}
