package zhttp.logging

sealed trait Transport { self =>

  def run(line: CharSequence): Unit = {
    self match {
      case Transport.UnsafeSync(log) => log(line)
      case Transport.Empty           => ()
    }
  }

}

object Transport {

  final case class UnsafeSync(log: CharSequence => Unit) extends Transport
  case object Empty                                      extends Transport
}
