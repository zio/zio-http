package zio.http.model.headers.values

sealed trait DNT
object DNT {
  case object InvalidDNTValue            extends DNT
  case object TrackingAllowedDNTValue    extends DNT
  case object TrackingNotAllowedDNTValue extends DNT
  case object NotSpecifiedDNTValue       extends DNT

  def toDNT(value: Either[Int, String]): DNT = {
    value match {
      case Left(value) =>
        value match {
          case 1 => TrackingNotAllowedDNTValue
          case 0 => TrackingAllowedDNTValue
          case _ => InvalidDNTValue
        }
      case Right(_)    => NotSpecifiedDNTValue

    }

  }

  def fromDNT(dnt: DNT): Either[Int, String] =
    dnt match {
      case NotSpecifiedDNTValue       => Right(null)
      case TrackingAllowedDNTValue    => Left(0)
      case TrackingNotAllowedDNTValue => Left(1)
      case InvalidDNTValue            => Right("invalid")
    }
}
