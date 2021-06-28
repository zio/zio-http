package zhttp

import zhttp.http.Http

package object channel {
  type HttpChannel[-R, +E, -A, +B] = Http[R, E, Event[A], Operation[B]]
}
