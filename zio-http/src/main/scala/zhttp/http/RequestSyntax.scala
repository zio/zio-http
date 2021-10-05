package zhttp.http

private[zhttp] trait RequestSyntax {
  object -> {
    def unapply(request: Request): Option[Route] =
      Option(request.method -> request.path)
  }
}
