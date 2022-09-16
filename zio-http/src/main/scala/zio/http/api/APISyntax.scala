package zio.http.api

private[api] trait APISyntax extends APISyntaxLowPriority {
  implicit class APITuple2Syntax[Id, A, B, Z](val api: API.WithId[(A, B), Z, Id])                                     {
    def apply(a: A, b: B): Invocation[Id, (A, B), Z] = Invocation(api, (a, b))
  }
  implicit class APITuple3Syntax[Id, A, B, C, Z](val api: API.WithId[(A, B, C), Z, Id])                               {
    def apply(a: A, b: B, c: C): Invocation[Id, (A, B, C), Z] = Invocation(api, (a, b, c))
  }
  implicit class APITuple4Syntax[Id, A, B, C, D, Z](val api: API.WithId[(A, B, C, D), Z, Id])                         {
    def apply(a: A, b: B, c: C, d: D): Invocation[Id, (A, B, C, D), Z] = Invocation(api, (a, b, c, d))
  }
  implicit class APITuple5Syntax[Id, A, B, C, D, E, Z](val api: API.WithId[(A, B, C, D, E), Z, Id])                   {
    def apply(a: A, b: B, c: C, d: D, e: E): Invocation[Id, (A, B, C, D, E), Z] = Invocation(api, (a, b, c, d, e))
  }
  implicit class APITuple6Syntax[Id, A, B, C, D, E, F, Z](val api: API.WithId[(A, B, C, D, E, F), Z, Id])             {
    def apply(a: A, b: B, c: C, d: D, e: E, f: F): Invocation[Id, (A, B, C, D, E, F), Z] =
      Invocation(api, (a, b, c, d, e, f))
  }
  implicit class APITuple7Syntax[Id, A, B, C, D, E, F, G, Z](val api: API.WithId[(A, B, C, D, E, F, G), Z, Id])       {
    def apply(a: A, b: B, c: C, d: D, e: E, f: F, g: G): Invocation[Id, (A, B, C, D, E, F, G), Z] =
      Invocation(api, (a, b, c, d, e, f, g))
  }
  implicit class APITuple8Syntax[Id, A, B, C, D, E, F, G, H, Z](val api: API.WithId[(A, B, C, D, E, F, G, H), Z, Id]) {
    def apply(a: A, b: B, c: C, d: D, e: E, f: F, g: G, h: H): Invocation[Id, (A, B, C, D, E, F, G, H), Z] =
      Invocation(api, (a, b, c, d, e, f, g, h))
  }
  implicit class APITuple9Syntax[Id, A, B, C, D, E, F, G, H, I, Z](
    val api: API.WithId[(A, B, C, D, E, F, G, H, I), Z, Id],
  ) {
    def apply(a: A, b: B, c: C, d: D, e: E, f: F, g: G, h: H, i: I): Invocation[Id, (A, B, C, D, E, F, G, H, I), Z] =
      Invocation(api, (a, b, c, d, e, f, g, h, i))
  }
  implicit class APITuple10Syntax[Id, A, B, C, D, E, F, G, H, I, J, Z](
    val api: API.WithId[(A, B, C, D, E, F, G, H, I, J), Z, Id],
  ) {
    def apply(a: A, b: B, c: C, d: D, e: E, f: F, g: G, h: H, i: I, j: J)
      : Invocation[Id, (A, B, C, D, E, F, G, H, I, J), Z] = Invocation(api, (a, b, c, d, e, f, g, h, i, j))
  }
  implicit class APITuple11Syntax[Id, A, B, C, D, E, F, G, H, I, J, K, Z](
    val api: API.WithId[(A, B, C, D, E, F, G, H, I, J, K), Z, Id],
  ) {
    def apply(a: A, b: B, c: C, d: D, e: E, f: F, g: G, h: H, i: I, j: J, k: K)
      : Invocation[Id, (A, B, C, D, E, F, G, H, I, J, K), Z] = Invocation(api, (a, b, c, d, e, f, g, h, i, j, k))
  }
}
private[api] trait APISyntaxLowPriority {
  implicit class APISyntax[Id, A, Z](val api: API.WithId[A, Z, Id]) {
    def apply(a: A): Invocation[Id, A, Z] = Invocation(api, a)
  }
}
