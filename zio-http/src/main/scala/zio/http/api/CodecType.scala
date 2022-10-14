package zio.http.api

sealed trait CodecType
object CodecType {
  type Route <: CodecType
  type Body <: CodecType
  type Query <: CodecType
  type Header <: CodecType
}
