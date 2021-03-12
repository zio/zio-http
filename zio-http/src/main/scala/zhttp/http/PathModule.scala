package zhttp.http

trait PathModule { module =>
  sealed trait Path {
    self =>
    def asString: String

    def /(name: String): Path = append(name)

    def append(name: String): Path = if (name.isEmpty) this else module./(this, name)

    def toList: List[String]

    override def toString: String = this.asString
  }

  object Path {
    def apply(): Path                               = Root
    def apply(string: String): Path                 = if (string.trim.isEmpty) Root else Path(string.split("/").toList)
    def apply(seqString: String*): Path             = Path(seqString.toList)
    def apply(list: List[String]): Path             = list.foldLeft[Path](Root)((a, s) => a.append(s))
    def unapplySeq(arg: Path): Option[List[String]] = Option(arg.toList)
    def empty: Path                                 = Root
  }

  case class /(path: Path, name: String) extends Path {
    override lazy val asString: String = s"${path.asString}/${name}"
    override def toList: List[String]  = path.toList ::: List(name)
  }

  case object Root extends Path {
    override lazy val asString: String = ""
    override def toList: List[String]  = Nil
  }
}
