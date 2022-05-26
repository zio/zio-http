package zhttp.http

private[zhttp] trait PathSyntax { module =>
  val !! : Path = Path.End

  object /: {
    def unapply(path: Path): Option[(String, Path)] =
      path match {
        case Path.End              => None
        case Path.Cons(name, path) => Option(name -> path)
      }
  }

  object / {
    def unapply(path: Path): Option[(Path, String)] = {
      path.toList.reverse match {
        case Nil          => None
        case head :: next => Option((Path(next.reverse), head))
      }
    }
  }
}
