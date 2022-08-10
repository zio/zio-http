package zhttp

import zhttp.service.EventLoopGroup

package object internal {
  type HttpEnv = EventLoopGroup with DynamicServer
}
