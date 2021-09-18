package zhttp

package object experiment {
  type AnyResponse[-R, +E] = HttpMessage.AnyResponse[R, E]
}
