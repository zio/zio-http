package zhttp

package object experiment {
  type AnyRequest          = HttpMessage.AnyRequest
  type AnyResponse[-R, +E] = HttpMessage.AnyResponse[R, E]
}
