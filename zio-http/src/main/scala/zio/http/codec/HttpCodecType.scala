package zio.http.codec

sealed trait HttpCodecType

object HttpCodecType {
  type RequestType <: Path with Body with Query with Header with Method
  type ResponseType <: Body with Header with Status

  type Path <: HttpCodecType
  type Body <: HttpCodecType
  type Query <: HttpCodecType
  type Header <: HttpCodecType
  type Method <: HttpCodecType
  type Status <: HttpCodecType
}
