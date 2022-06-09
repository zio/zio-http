package zhttp.http

private[zhttp] trait PathSyntax { module =>
  val !! : Path = Path.empty

  object /: {
    def unapply(path: Path): Option[(String, Path)] = {
      for {
        head <- path.segments.headOption
        tail = path.segments.drop(1)
      } yield (head, path.copy(segments = tail))
    }
  }

  object / {
    def unapply(path: Path): Option[(Path, String)] = {
      if (path.segments.length == 1) {
        Some(!! -> path.segments.last)
      } else if (path.segments.length >= 2) {
        val init = path.segments.init
        val last = path.segments.last
        Some(path.copy(segments = init) -> last)
      } else {
        None
      }
    }
  }
}
