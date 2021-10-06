package zhttp.http

import scala.annotation.tailrec

private[zhttp] trait PathModule { module =>
  sealed trait Path { self =>
    def asString: String
    def /(name: String): Path      = Path(self.toList :+ name)
    def /:(name: String): Path     = append(name)
    def append(name: String): Path = if (name.isEmpty) self else Path.Cons(name, self)
    def toList: List[String]
    def reverse: Path              = Path(toList.reverse)
    def last: Option[String]       = self match {
      case Path.End           => None
      case Path.Cons(name, _) => Option(name)
    }
    def initial: Path                 = self match {
      case Path.End           => self
      case Path.Cons(_, path) => path
    }
    def isEnd: Boolean             = self match {
      case Path.End        => true
      case Path.Cons(_, _) => false
    }
    override def toString: String  = this.asString

    @tailrec
    final def startsWith(other: Path): Boolean = {
      if (self == other) true
      else
        (self, other) match {
          case (/(p1, _), p2) => p1.startsWith(p2)
          case _              => false

        }
    }
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

    case class Cons(name: String, path: Path) extends Path {
      override def asString: String     = s"/${name}${path.asString}"
      override def toList: List[String] = name :: path.toList
    }
  }

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

  val !! = Path.End

  @deprecated("Use `!!` operator instead.", "23-Aug-2021")
  val Root = !!

}
