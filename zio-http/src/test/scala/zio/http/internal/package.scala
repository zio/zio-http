package zio.http

import zio.http.service.ChannelFactory

package object internal {
  type HttpEnv = ChannelFactory with DynamicServer
}
