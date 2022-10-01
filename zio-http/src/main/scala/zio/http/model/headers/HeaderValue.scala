package zio.http.model.headers

object HeaderValue {
  sealed trait DNT
  object DNT {
    case object InvalidDNTValue            extends DNT
    case object TrackingAllowedDNTValue    extends DNT
    case object TrackingNotAllowedDNTValue extends DNT
    case object NotSpecifiedDNTValue       extends DNT

    def toDNT(value: String): DNT = {
      value match {
        case null => NotSpecifiedDNTValue
        case "1"  => TrackingNotAllowedDNTValue
        case "0"  => TrackingAllowedDNTValue
        case _    => InvalidDNTValue
      }
    }

    def fromDNT(dnt: DNT): String =
      dnt match {
        case NotSpecifiedDNTValue       => null
        case TrackingAllowedDNTValue    => "0"
        case TrackingNotAllowedDNTValue => "1"
        case InvalidDNTValue            => ""
      }
  }
}
