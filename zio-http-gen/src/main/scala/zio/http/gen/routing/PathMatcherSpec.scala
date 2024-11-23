package zio.http.gen.routing

import zio.test._
import zio.test.Assertion._

object PathMatcherSpec extends ZIOSpecDefault {
  def spec = suite("PathMatcher")(
    test("should match exact paths") {
      val paths = Set("/users", "/posts", "/comments")
      val matcher = PathMatcher.compileNormalized(paths)
      
      assertTrue(
        matcher.matches("/users"),
        matcher.matches("/posts"),
        matcher.matches("/comments"),
        !matcher.matches("/unknown")
      )
    },
    
    test("should optimize common prefixes") {
      val paths = Set(
        "/api/v1/users",
        "/api/v1/posts",
        "/api/v2/users"
      )
      val matcher = PathMatcher.compileNormalized(paths)
      
      assertTrue(
        matcher.matches("/api/v1/users"),
        matcher.matches("/api/v1/posts"),
        matcher.matches("/api/v2/users"),
        !matcher.matches("/api/v1/unknown")
      )
    },
    
    test("should handle path normalization") {
      val paths = Set("/users/", "posts")
      val matcher = PathMatcher.compileNormalized(paths)
      
      assertTrue(
        matcher.matches("/users"),
        matcher.matches("posts"),
        matcher.matches("/users"),
        !matcher.matches("/users/"),
        !matcher.matches("/posts/")
      )
    }
  )
}