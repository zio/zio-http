package zio.http.endpoint

sealed trait CodecType

object CodecType {
  type RequestType <: Path with Body with Query with Header with Method
  type ResponseType <: Body with Header with Status

  type Path <: CodecType
  type Body <: CodecType
  type Query <: CodecType
  type Header <: CodecType
  type Method <: CodecType
  type Status <: CodecType
}
