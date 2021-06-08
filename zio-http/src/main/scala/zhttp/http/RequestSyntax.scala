package zhttp.http

private[zhttp] trait RequestSyntax {
  object -> {
    def unapply(request: Request[Any, Nothing, Any]): Option[Route] =
      Option(request.method -> request.url.path)
  }
}
