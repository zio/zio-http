package zio.http

import zio.Tag

trait RoutesCompanionVersionSpecific {
  private[http] class ApplyContextAspect[-Env, +Err, Env0](private val self: Routes[Env, Err]) {
    def apply[Env1, Env2 <: Env, Ctx: Tag](aspect: HandlerAspect[Env1, Ctx])(implicit
      ev: Env0 with Ctx <:< Env,
      tag: Tag[Env0],
      tag1: Tag[Env1],
    ): Routes[Env0 with Env1, Err] = self.transform(_.@@[Env0](aspect))
  }

}
