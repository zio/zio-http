package zhttp.http.headers

private[zhttp] trait HeaderExtension[+A] extends HeaderModifier[A] with HeaderGetters with HeaderChecks {
  self: A =>
}
