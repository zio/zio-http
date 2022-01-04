package zhttp

import zio.Has

package object internal {
  type ReusableServer = Has[ReusableServer.Service]
}
