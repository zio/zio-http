package zio.http.html

private[html] sealed trait EncodingState {
  def nextElemSeparator: String
  def inner: EncodingState
}

object EncodingState {
  case object NoIndentation extends EncodingState {
    val nextElemSeparator: String = ""
    def inner: EncodingState      = NoIndentation
  }

  final case class Indentation(current: Int, spaces: Int) extends EncodingState {
    lazy val nextElemSeparator: String = "\n" + (" " * (current * spaces))
    def inner: EncodingState           = Indentation(current + 1, spaces)
  }
}
