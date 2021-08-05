package zhttp.experiment

object Params {
  sealed trait P1
  sealed trait P2
  sealed trait P3
  sealed trait P4
  sealed trait P5
  sealed trait P6
  sealed trait P7
  sealed trait P8
  sealed trait P9
  sealed trait P10
  sealed trait P11
  sealed trait P12

  implicit object P
      extends P1
      with P2
      with P3
      with P4
      with P5
      with P6
      with P7
      with P8
      with P9
      with P10
      with P11
      with P12
}
