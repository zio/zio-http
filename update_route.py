import sys

with open('zio-http/shared/src/main/scala/zio/http/Route.scala', 'r') as f:
    content = f.read()

replacement = """  /**
   * Applies a context-providing handler aspect to this route at the `Routes`
   * level.
   *
   * Unlike the other `@@` overloads on `Route`, which all return a `Route`,
   * this overload returns a [[zio.http.Routes.ApplyContextAspect]]. When you
   * later apply a context aspect to the returned value (via `.apply(aspect)`),
   * it will produce a `Routes` rather than a single `Route`.
   *
   * This overload is intended for use when you need to construct or modify
   * a `Routes` value while applying context-aware handler aspects derived from
   * a `Route`.
   */
  // This overload exists only to build an ApplyContextAspect (e.g. `route @@ [Env0] { ... }`).
  // The two DummyImplicit parameters are required to disambiguate this overload from the
  // other `@@` methods above, especially in the presence of type inference / partial
  // application. Removing them reintroduces an ambiguous overload compile error.
  // Minimal sketch:
  //   trait R {
  //     def @@(a: HandlerAspect[Any, Unit]): R
  //     def @@[Env0](implicit d1: DummyImplicit, d2: DummyImplicit): Builder[Env0]
  //   }
  final def @@[Env0](implicit dummy: DummyImplicit, dummy2: DummyImplicit): ApplyContextAspect[Env, Err, Env0] ="""

content = content.replace("  final def @@[Env0](implicit dummy: DummyImplicit, dummy2: DummyImplicit): ApplyContextAspect[Env, Err, Env0] =", replacement)

with open('zio-http/shared/src/main/scala/zio/http/Route.scala', 'w') as f:
    f.write(content)
