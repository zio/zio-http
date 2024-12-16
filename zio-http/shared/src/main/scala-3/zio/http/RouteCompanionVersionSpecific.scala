package zio.http

trait RouteCompanionVersionSpecific {
  private[http] class ApplyContextAspect[-Env, +Err, Env0](private val self: Route[Env, Err]) {
    transparent inline def apply[Env1, Env2 <: Env, Ctx](aspect: HandlerAspect[Env1, Ctx])(implicit
      ev: Env0 with Ctx <:< Env,
    ): Route[Env0 with Env1, Err] = self.transform(_.@@[Env0](aspect))
  }

}
