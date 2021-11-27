package zhttp

import zio.Has

package object internal {
  type HttpAppCollection = Has[AppCollection.Service]
}
