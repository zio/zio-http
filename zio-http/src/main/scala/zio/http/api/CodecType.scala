package zio.http.api

sealed trait CodecType

object CodecType {
  type RequestType <: Route with Body with Query with Header with Method
  type ResponseType <: Body with Header with Status

  type Route <: CodecType
  type Body <: CodecType
  type Query <: CodecType
  type Header <: CodecType
  type Method <: CodecType
  type Status <: CodecType
}
