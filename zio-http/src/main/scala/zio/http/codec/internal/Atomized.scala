package zio.http.codec.internal

import zio.http.codec.HttpCodec

final case class Atomized[A](
  method: A,
  status: A,
  path: A,
  query: A,
  header: A,
  content: A,
) {
  def get(tag: HttpCodec.AtomTag): A = {
    tag match {
      case HttpCodec.AtomTag.Status  => status
      case HttpCodec.AtomTag.Path    => path
      case HttpCodec.AtomTag.Content => content
      case HttpCodec.AtomTag.Query   => query
      case HttpCodec.AtomTag.Header  => header
      case HttpCodec.AtomTag.Method  => method
    }
  }

  def update(tag: HttpCodec.AtomTag)(f: A => A): Atomized[A] = {
    tag match {
      case HttpCodec.AtomTag.Status  => copy(status = f(status))
      case HttpCodec.AtomTag.Path    => copy(path = f(path))
      case HttpCodec.AtomTag.Content => copy(content = f(content))
      case HttpCodec.AtomTag.Query   => copy(query = f(query))
      case HttpCodec.AtomTag.Header  => copy(header = f(header))
      case HttpCodec.AtomTag.Method  => copy(method = f(method))
    }
  }
}
object Atomized {
  def apply[A](defValue: => A): Atomized[A] = Atomized(defValue, defValue, defValue, defValue, defValue, defValue)
}
