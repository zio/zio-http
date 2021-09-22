package zhttp.experiment

import zhttp.http._

object HttpMessage {

  /**
   * Response
   */
  case class AnyResponse[-R, +E](
    status: Status = Status.OK,
    headers: List[Header] = Nil,
    content: HttpData[R, E] = HttpData.empty,
  )

  object AnyResponse {}
}
