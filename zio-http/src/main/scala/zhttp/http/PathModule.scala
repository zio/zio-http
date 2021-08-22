package zhttp.http

private[zhttp] trait PathModule { module =>
  sealed trait Path { self =>
    def asString: String
    def /:(name: String): Path     = append(name)
    def append(name: String): Path = if (name.isEmpty) self else Path.Default(name, self)
    def toList: List[String]
    override def toString: String  = this.asString
  }

  object Path {
    def apply(): Path                               = End
    def apply(string: String): Path                 = if (string.trim.isEmpty) End else Path(string.split("/").toList)
    def apply(seqString: String*): Path             = Path(seqString.toList)
    def apply(list: List[String]): Path             = list.foldRight[Path](End)((s, a) => a.append(s))
    def unapplySeq(arg: Path): Option[List[String]] = Option(arg.toList)
    def empty: Path                                 = End

    case object End extends Path {
      override def asString: String     = ""
      override def toList: List[String] = Nil
    }

    case class Default(name: String, path: Path) extends Path {
      override def asString: String     = s"/${name}${path.asString}"
      override def toList: List[String] = name :: path.toList
    }
  }

  object /: {
    def unapply(path: Path): Option[(String, Path)] =
      path match {
        case Path.End                 => None
        case Path.Default(name, path) => Option(name -> path)
      }
  }

  val !! = Path.End
}
