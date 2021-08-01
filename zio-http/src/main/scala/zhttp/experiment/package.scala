package zhttp

import zhttp.experiment.HttpMessage.HResponse
import zhttp.http.Http
import zio.Chunk

package object experiment {
  type CByte             = Chunk[Byte]
  type AnyRequest        = HttpMessage.AnyRequest
  type AnyResponse       = HttpMessage.HResponse[Any, Nothing]
  type CompleteRequest   = HttpMessage.CompleteRequest
  type BufferedRequest   = HttpMessage.BufferedRequest
  type RHttp[-R, +E, -A] = Http[R, E, A, HResponse[R, E]]
}
