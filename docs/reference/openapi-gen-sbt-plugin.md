---
id: openapi-gen-sbt-plugin
title: OpenAPI codegen sbt plugin
---

This plugin allows to easily generate scala source code with zio-http Endpoints from OpenAPI spec files.

## How to use

The plugin offers 2 modes of operation that can be mixed and used together:
- Generating from unmanaged static OpenAPI spec files
- Generating from managed dynamic OpenAPI spec files

in `project/plugins.sbt` add the following line:
```scala
addSbtPlugin("dev.zio" % "zio-http-sbt-codegen" % "@VERSION@") // make sure the version of the sbt plugin
                                                               // matches the version of zio-http you are using
```

in `build.sbt` enable the plugin by adding:
```scala
enablePlugins(ZioHttpCodegen)
```

### 1. Generating from unmanaged static OpenAPI spec files
Place your manually curated OpenAPI spec files (`.yml`, `.yaml`, or `.json`) in `src/main/oapi/<path as package>/<openapi spec file>`.\
That's it. No other configuration is needed for basic usage. \
Once you `compile` your project, the `zioHttpCodegenMake` task is automatically invoked, and the generated code will be placed under `target/scala-<scala_binary_version>/src_managed/main/scala`.

### 2. Generating from managed dynamic OpenAPI spec files
In this mode, you can hook into `ZIOpenApi / sourceGenerators` a task to generate OpenAPI spec file, exactly like you would do with regular `Compile / sourceGenerators` for scala source files.
You might have some OpenAPI spec files hosted on [swaggerhub](https://app.swaggerhub.com/) or a similar service, 
or maybe you use services that expose OpenAPI specs via REST API, or perhaps you have a local project that can build its own spec and you want to run the spec generate command.
Whatever the scenario you're dealing with, it can be very handy to dynamically fetch/generate the latest most updated spec file, so the generated code stays up to date with any changes introduced.

Here's how you can do it:
```scala
import gigahorse.support.apachehttp.Gigahorse
import scala.concurrent.Await
import scala.concurrent.duration.DurationInt

ZIOpenApi / sourceGenerators += Def.task[Seq[File]] {
  // we'll fetch a spec from https://www.petstore.dev/
  // gigahorse comes builtin with sbt, but any other http client can be used
  val http = Gigahorse.http(Gigahorse.config)
  val request = Gigahorse.url("https://raw.githubusercontent.com/readmeio/oas-examples/main/3.0/yaml/response-http-behavior.yaml")
  val response = http.run(request, Gigahorse.asString)
  val content = Await.result(response, 1.minute)

  // path under target/scala-<scala_bin_version>/src_managed/oapi/
  // corresponds to the package where scala sources will be generated
  val outFile = (ZIOpenApi / sourceManaged).value / "dev" / "petstore" / "http" / "test" / "api.yaml"
  IO.write(outFile, content)

  // as long the task yields a Seq[File] of valid OpenAPI spec files,
  // and those files follow the path structure `src_managed/oapi/<path as package>/<openapi spec file>`,
  // the plugin will pick it up, and generate the corresponding scala sources.
  Seq(outFile)
}
```

## Configuration
The plugin offers a setting key which you can set to control how code is generated:
```scala
zioHttpCodegenConf := zio.http.gen.openapi.Config.default
```

## Caveats
The plugin allows you to provide multiple files.
Note that if you place multiple files in the same directory,
which means same package for the generated code - you must make sure there are no "collisions" between generated classes.
If the same class is going to be generated differently in different files, you probably want to have a different package for it.

Also, please note that the plugin relies on the file extension to determine how to parse it.
So files must have the correct extension (`.yml`, `.yaml`, or `.json`), and the content must be formatted accordingly.
