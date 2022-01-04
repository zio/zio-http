package zhttp

import zio.Has

package object internal {
  type DynamicServer = Has[DynamicServer.Service]
}
