package zio.http.endpoint.cli

import java.nio.file.Path

import zio._

import zio.http._

/**
 * Represents a sealed trait that can retrieve a FormField. It allows to specify
 * different methods to obtain parts of a body: from an URL, from a file, from
 * JSON...
 */

private[cli] sealed trait Retriever {

  def retrieve(): Task[FormField]

}

private[cli] object Retriever {

  final case class URL(url: String) extends Retriever {
    override def retrieve(): Task[FormField] = ???
  }

  final case class File(name: String, path: Path, mediaType: Option[MediaType]) extends Retriever {

    override def retrieve(): Task[FormField] = {
      val media = mediaType match {
        case Some(media) => media
        case None        => MediaType.any
      }
      for {
        chunk <- Body.fromFile(new java.io.File(path.toUri())).asChunk
      } yield FormField.binaryField(name, chunk, media)
    }

  }

  final case class Content(formField: FormField) extends Retriever {
    override def retrieve(): UIO[FormField] = ZIO.succeed(formField)
  }

}
