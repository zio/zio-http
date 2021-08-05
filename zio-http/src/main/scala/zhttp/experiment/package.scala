package zhttp

package object experiment {
  type AnyRequest          = HttpMessage.AnyRequest
  type AnyResponse[+A]     = HttpMessage.HResponse[Any, Nothing, A]
  type CompleteRequest[+A] = HttpMessage.CompleteRequest[A]
  type BufferedRequest[+A] = HttpMessage.BufferedRequest[A]
}
