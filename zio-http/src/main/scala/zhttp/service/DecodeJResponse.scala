package zhttp.service

private[zhttp] trait DecodeJResponse {

  /**
   * Tries to decode netty request into ZIO Http Request
   */
//  def decodeJResponse(jRes: JFullHttpResponse): Either[Throwable, UHttpResponse] = Try {
//    val status  = Status.fromJHttpResponseStatus(jRes.status())
//    val headers = Header.parse(jRes.headers())
//    val content = HttpData.fromByteBuf(jRes.content())
//
//    Response.http(status, headers, content): UHttpResponse
//  }.toEither
}
