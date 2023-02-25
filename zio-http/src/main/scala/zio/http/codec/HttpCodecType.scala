package zio.http.codec

sealed trait HttpCodecType

object HttpCodecType {
  type RequestType <: Path with Content with Query with Header with Method
  type ResponseType <: Content with Header with Status

  type Path <: HttpCodecType
  type Content <: HttpCodecType
  type Query <: HttpCodecType
  type Header <: HttpCodecType
  type Method <: HttpCodecType
  type Status <: HttpCodecType
}
