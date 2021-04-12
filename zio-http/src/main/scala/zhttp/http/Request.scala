package zhttp.http

import zio.{Chunk, UIO, ZIO}

// REQUEST
final case class Request(endpoint: Endpoint, headers: List[Header], content: HttpData[Any, Nothing]) {
  self =>
  val method: Method = endpoint._1
  val url: URL       = endpoint._2
  val route: Route   = method -> url.path

  def getBodyAsString[R]: ZIO[R, Nothing, Option[String]] = content match {
    case HttpData.CompleteData(data)   => UIO.some(data.map(_.toChar).mkString)
    case data @ HttpData.StreamData(_) => data.asString.asSome
    case _                             => UIO.none
  }

  def discardContent: UIO[Unit] = content match {
    case HttpData.StreamData(data) => data.runDrain
    case _                         => UIO.unit
  }

  def contentSize: UIO[Long] = content match {
    case HttpData.StreamData(data)   =>
      data.mapChunks(c => Chunk(c.size)).fold(0L)(_ + _)
    case HttpData.CompleteData(data) => UIO.succeed(data.length.toLong)
    case _                           => UIO.succeed(0L)
  }

}
