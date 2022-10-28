package zio.http.model

import zio.http.model.Multipart.{Attribute, FileUpload}
import zio.stream.ZStream

sealed trait Multipart {
  def name: String
  def length: Long

  /**
   * Returns the defined length of the HttpData.
   *
   * If no Content-Length is provided in the request, the defined length is
   * always 0 (whatever during decoding or in final state).
   *
   * If Content-Length is provided in the request, this is this given defined
   * length. This value does not change, whatever during decoding or in the
   * final state.
   *
   * This method could be used for instance to know the amount of bytes
   * transmitted for one particular HttpData, for example one {@link FileUpload}
   * or any known big {@link Attribute}.
   *
   * @return
   *   the defined length of the HttpData
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
