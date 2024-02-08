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

  def retrieve(): ZIO[Client, Throwable, FormField]

}

private[cli] object Retriever {

  /**
   * Retrieves body from an URL and returns it in a BinaryField.
   */

  final case class URL(name: String, url: String, mediaType: MediaType) extends Retriever {

    lazy val request                                           = Request.get(http.URL(http.Path.decode(url)))
    override def retrieve(): ZIO[Client, Throwable, FormField] = for {
      client   <- ZIO.service[Client]
      response <- client.request(request).provide(Scope.default)
      chunk    <- response.body.asChunk
    } yield FormField.binaryField(name, chunk, mediaType)
  }

  final case class File(name: String, path: Path, mediaType: MediaType) extends Retriever {

    override def retrieve(): Task[FormField] =
      for {
        chunk <- Body.fromFile(new java.io.File(path.toUri)).flatMap(_.asChunk)
      } yield FormField.binaryField(name, chunk, mediaType)

  }

  final case class Content(formField: FormField) extends Retriever {
    override def retrieve(): UIO[FormField] = ZIO.succeed(formField)
  }

}
