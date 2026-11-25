package zio.http.endpoint

/**
 * Platform-specific members of [[Endpoint]]. On the JVM it adds no members; on
 * Scala.js it adds `urlAbsolute`.
 */
trait EndpointPlatformSpecific[PathInput, Input, Err, Output, Auth]
