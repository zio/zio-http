package zio.http

private[zio] trait RequestSyntax {
  object -> {
    def unapply(request: Request): Option[(Method, Path)] =
      Option(request.method -> request.path)
  }
}
