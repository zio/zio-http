package zhttp

package object experiment {
  type AnyRequest              = HttpMessage.AnyRequest
  type AnyResponse[-R, +E, +A] = HttpMessage.AnyResponse[R, E, A]
  type CompleteRequest[+A]     = HttpMessage.CompleteRequest[A]
  type BufferedRequest[+A]     = HttpMessage.BufferedRequest[A]
}
