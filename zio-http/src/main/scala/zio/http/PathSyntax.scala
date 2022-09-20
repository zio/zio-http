package zio.http

import zio.http.Path.Segment
import zio.stacktracer.TracingImplicits.disableAutoTrace // scalafix:ok;

private[zio] trait PathSyntax { module =>
  val !! : Path = Path.root

  val ~~ : Path = Path.empty

  object /: {
    def unapply(path: Path): Option[(String, Path)] =
      for {
        head <- path.segments.headOption.map {
          case Segment.Text(text) => text
          case Segment.Root       => ""
        }
        tail = path.segments.drop(1)
      } yield (head, Path(tail))
  }

  object / {
    def unapply(path: Path): Option[(Path, String)] = {
      if (path.segments.length == 1) {
        val last = path.segments.last match {
          case Segment.Text(text) => text
          case Segment.Root       => ""
        }
        Some(~~ -> last)
      } else if (path.segments.length >= 2) {
        val last = path.segments.last match {
          case Segment.Root       => ""
          case Segment.Text(text) => text
        }
        Some(Path(path.segments.init) -> last)
      } else None
    }
  }
}
