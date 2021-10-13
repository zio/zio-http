package zhttp.http

private[zhttp] trait RequestSyntax {
  object -> {
    def unapply(request: Request): Option[Router] =
      Option(request.method -> request.path)
  }
}
