package zio.http

import zio.Tag

trait RoutesCompanionVersionSpecific {
  private[http] class ApplyContextAspect[-Env, +Err, Env0](private val self: Routes[Env, Err]) {
    def apply[Env1, Env2 <: Env, Ctx: Tag](aspect: HandlerAspect[Env1, Ctx])(implicit
      ev: Env0 with Ctx <:< Env,
    ): Routes[Env0 with Env1, Err] = self.transform(_.@@[Env0](aspect))
  }

}
