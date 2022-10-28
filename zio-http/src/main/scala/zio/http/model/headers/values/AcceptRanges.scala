package zio.http.model.headers.values

/**
 * The Accept-Ranges HTTP response header is a marker used by the server
 * to advertise its support for partial requests from the client for file
 * downloads. The value of this field indicates the unit that can be used to
 * define a range. By default the RFC 7233 specification supports only 2 possible
 * values.
 */
sealed trait AcceptRanges {
  val name: String
}

object AcceptRanges {
  case object Bytes               extends AcceptRanges {
    override val name = "bytes"
  }
  case object None                extends AcceptRanges {
    override val name = "none"
  }
  case object InvalidAcceptRanges extends AcceptRanges {
    override val name = ""
  }

  def from(acceptRangers: AcceptRanges): String =
    acceptRangers.name

  def to(value: String): AcceptRanges =
    value match {
      case Bytes.name => Bytes
      case None.name  => None
      case _          => InvalidAcceptRanges
    }
}
