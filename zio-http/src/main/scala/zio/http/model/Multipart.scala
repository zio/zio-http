package zio.http.model

import zio.http.model.Multipart.{Attribute, FileUpload}
import zio.stream.ZStream

sealed trait Multipart {
  def name: String
  def length: Long

  /**
   * Returns the defined bytes length of the HttpData.
   *
   * If no Content-Length is provided in the request, the defined length is
   * always 0 (whatever during decoding or in final state).
   *
   * If Content-Length is provided in the request, this is this given defined
   * length. This value does not change, whatever during decoding or in the
   * final state.
   *
   * @return
   *   the defined bytes length of the HttpData
   */
  def definedLength: Long
  def charset: String
}

object Multipart {

  case class Attribute(
    name: String,
    length: Long,
    definedLength: Long,
    charset: String,
    value: String,
  ) extends Multipart

  case class FileUpload(
    name: String,
    length: Long,
    definedLength: Long,
    charset: String,
    filename: String,
    contentType: String,
    contentTransferEncoding: String,
    content: ZStream[Any, Throwable, Byte],
  ) extends Multipart
}
