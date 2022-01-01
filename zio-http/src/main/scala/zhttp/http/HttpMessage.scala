package zhttp.http

trait HttpMessage {}

object HttpMessage {
  trait RequestMessage extends HttpMessage {
    def getHeaders: Headers

    def method: Method

    def url: URL
  }

  trait ResponseMessage extends HttpMessage {
    def headers: Headers

    def status: Status
  }
}
