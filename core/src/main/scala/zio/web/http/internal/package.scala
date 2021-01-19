package zio.web.http

import zio.Has

package object internal {
  type HttpRouter = Has[HttpRouter.Service]
}
