package zhttp.experiment

import zhttp.http._
import zio.ZIO

trait LazyRequest {
  def method: Method
  def url: URL
  def path: Path = url.path
  def headers: List[Header]
  def decodeContent[R, E, B](decoder: ContentDecoder[R, E, B]): ZIO[R, E, B]
}

object LazyRequest {
  // type Legit
}
