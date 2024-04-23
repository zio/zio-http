package zio.http

trait RoutesVersionSpecific {
  private[http] class ApplyContextAspect[-Env, +Err, Env0](private val self: Routes[Env, Err]) {
    transparent inline def apply[Env1, Env2 <: Env, Ctx](aspect: HandlerAspect[Env1, Ctx])(implicit
      ev: Env0 with Ctx <:< Env,
    ): Routes[Env0 with Env1, Err] = self.transform(_.@@[Env0](aspect))
  }

}
