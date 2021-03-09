package zhttp.http

trait RequestSyntax {
  object -> {
    def unapply(request: Request): Option[Route] =
      Option(request.endpoint._1 -> request.endpoint._2.path)
  }
}
