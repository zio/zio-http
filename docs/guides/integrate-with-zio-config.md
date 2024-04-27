---
id: integration-with-zio-config
title: "ZIO HTTP: How to Integrate with ZIO Config"
sidebar: Integration with ZIO Config
---

When building HTTP applications, it is common to have configuration settings that need to be loaded from various sources such as environment variables, system properties, or configuration files. It is essential especially when deploying applications to different environments like development, testing, and production, or we want to have a cloud-native application that can be configured dynamically.

ZIO HTTP provides seamless integration with [ZIO Config](https://zio.dev/zio-config/), a powerful configuration library for ZIO, to manage configurations in your HTTP applications.

In this guide, we will learn how to integrate ZIO HTTP with ZIO Config to load configuration settings for our HTTP applications.

## ZIO Config Overview

The ZIO core library has a built-in configuration system that allows us to define a type-safe configuration schema, load configurations from various sources, validate configurations, and access configuration settings in a functional way.

We can define a configuration schema for any custom data type. For example, if we have a `DatabaseConfig` case class as follows:

```scala mdoc:silent
case class DatabaseConfig(
  url: String,
  username: String,
  password: String,
  poolSize: Int,
)
```

We can derive a configuration schema for `DatabaseConfig` using ZIO Config as follows:

```scala mdoc:compile-only
object DatabaseConfig {
  val config: Config[DatabaseConfig] =
    DeriveConfig.deriveConfig[DatabaseConfig]
      .mapKey(toSnakeCase)
      .nested("database")
}
```

Now, we can load the configuration settings for `DatabaseConfig` by calling `ZIO.config(DatabaseConfig.config)`:

```scala
object MainApp extends ZIOAppDefault {
  def run = {
    for {
      config <- ZIO.config(DatabaseConfig.config)
      _      <- ZIO.debug("Just started right now!")
      _      <- ZIO.debug(s"Connecting to the database: ${config.url}")
    } yield () 
  }
}
```

By default, ZIO will load the configs from environment variables, so we need to set the following environment variables:

```bash
export DATABASE_URL="jdbc:postgresql://localhost:5432/mydb"
export DATABASE_USERNAME="admin"
export DATABASE_PASSWORD="password"
export DATABASE_POOL_SIZE=10
```

## Loading Configuration Settings from a File

As we mentioned earlier, by default, ZIO loads configurations from environment variables. However, we can change the `ConfigProvider` to load configurations from other sources such as system properties, console, and system properties. All of these are built-in providers in the ZIO core library.

ZIO Config also provides more advanced `ConfigProvider`s such as HOCON, JSON, YAML, and XML. Based on the configuration format, we need to add one of the following dependencies to our project:

```scala
libraryDependencies += "dev.zio" %% "zio-config-typesafe" % "@ZIO_CONFIG_VERSION@" // HOCON
libraryDependencies += "dev.zio" %% "zio-config-yaml"     % "@ZIO_CONFIG_VERSION@" // YAML and JSON
libraryDependencies += "dev.zio" %% "zio-config-xml"      % "@ZIO_CONFIG_VERSION@" // XML
```

Assuming we have an `application.conf` file inside the `resources` directory with the following content:

```hocon
database {
  url: "jdbc:mysql://localhost:3306/mydatabase"
  url: ${?DATABASE_URL} 
  username: "user"
  username: ${?DATABASE_USERNAME} 
  password: "password"
  password: ${?DATABASE_PASSWORD} 
  pool_size: 20
  pool_size: ${?DATABASE_POOL_SIZE} 
}
```

Then, we can load it using the `ConfigProvider.fromResourcePath` method:

```scala
object MainApp extends ZIOAppDefault {
  override val bootstrap: ZLayer[ZIOAppArgs, Any, Any] =
    Runtime.setConfigProvider(ConfigProvider.fromResourcePath())

  def run = 
    for {
      config <- ZIO.config(DatabaseConfig.config)
      _      <- ZIO.debug("Just started right now!")
      _      <- ZIO.debug(s"Connecting to the database: ${config.url}")
    } yield () 
}
```

## Client and Server Configuration

Both `Client` and `Server` have the `default` layer that requires no configuration and provides an instance of `Client` and `Server` with default settings:

```scala
object Client {
  val default: ZLayer[Any, Throwable, Client] = ???
}

object Server {
  val default: ZLayer[Any, Throwable, Server] = ???
}
```

But in some cases, we need to customize the client or server settings such as timeouts, host, port, and other parameters. To do that, ZIO HTTP provides `live` and `customized` layers that require additional configuration settings:

```scala
object Client {
  val live      : ZLayer[Client.Config with NettyConfig with DnsResolver, Throwable, Client]  = ??? 
  def customized: ZLayer[Client.Config with ClientDriver with DnsResolver, Throwable, Client] = ???
}

object Server {
  val live      : ZLayer[Server.Config, Throwable, Server]               = ???
  val customized: ZLayer[Server.Config & NettyConfig, Throwable, Server] = ???
}
```

So, to have a customized client or server, we need to provide configuration layers to satisfy the required dependencies. For example, to create a `live` server, we need to provide a `ZLayer` that produces a `Server.Config`.

For a practical example, see the following code which enables the response compression in the server:

```scala mdoc:passthrough
import zio.http._

printSource("zio-http-example/src/main/scala/example/ServerResponseCompression.scala")
```

In the above example, we updated the default server configuration to enable the response compression. Finally, we provided the `Server.live` and our customized config layer to the `Server.serve` method.

### Predefined Configuration Schemas

Until now, we changed the server configuration programmatically inside the code. But what if we want to load the client or server configuration from a file, e.g. `application.conf`? We need to have a configuration schema for the client and server settings, i.e. `zio.Config[Client.Config]` and `zio.Config[Server.Config]`. Fortunately, ZIO HTTP provides these configuration schemas by default.

Before going further, let's take a look at the `Server.Config` and `Client.Config` and see how are they defined in ZIO HTTP:

```scala
object Server {
  case class Config(
    // list of all server configuration settings
  )
  object Config {
    // configuration schema for Server.Config
    val config: zio.Config[Server.Config] = ???

    // default configuration for Server.Config
    lazy val default: Server.Config = ???
  }
}

object Client {
  case class Config(
    // list of all client configuration settings
  )
  object Config {
    // Configuration Schema for Cleint.Config
    val config: zio.Config[Client.Config] = ???

    // default configuration for Client.Config
    lazy val default: Client.Config = ???
  }
}
```

The `Server` and `Client` modules have predefined config schema, i.e. `Server.Config.config` and `Client.Config.config`, that can be used to load the server/client configuration from the environment, system properties, or any other configuration sources.

## Loading Configuration Settings from Environment Variables

As the ZIO HTTP‌ provided these configuration schemas by default, we can easily use them to load the configuration settings from the considered sources using the corresponding `ConfigProvider`:

```scala
import zio._
import zio.http._

object MainApp extends ZIOAppDefault {
  def run = {
    Server
      .install(
        Routes(
          Method.GET / "hello" -> handler(Response.text("Hello, world!")),
        ),
      )
      .flatMap(port => ZIO.debug(s"Sever started on http://localhost:$port") *> ZIO.never)
      .provide(
        Server.live,
        ZLayer.fromZIO(
          ZIO.config(Server.Config.config.mapKey(_.replace('-', '_'))),
        ),
      )
  }
}
```

```shell
export BINDING_HOST=localhost
export BINDING_PORT=8081
```

:::note
In the above example, we used the `mapKey` method to replace the `-` character with `_` in the configuration keys. This is because the environment variables do not allow the `-` character in the key names.
:::

### Loading Configuration Settings from an HOCON File

By changing the `ConfigProvider` to `ConfigProvider.fromResourcePath()`, we can load the server configuration from the `application.conf` file:

```hocon
zio.http.server {
  binding_port: 8083
  binding_host: localhot
}
```

```scala
import zio._
import zio.http._

object LoadServerConfigFromHoconFile extends ZIOAppDefault {
    override val bootstrap: ZLayer[ZIOAppArgs, Any, Any] =
      Runtime.setConfigProvider(ConfigProvider.fromResourcePath())
      
  def run = {
    Server
      .install(
        Routes(
          Method.GET / "hello" -> handler(Response.text("Hello, world!")),
        ),
      )
      .flatMap(port => ZIO.debug(s"Sever started on http://localhost:$port") *> ZIO.never)
      .provide(
        Server.live,
        ZLayer.fromZIO(
          ZIO.config(Server.Config.config.nested("zio.http.server").mapKey(_.replace('-', '_'))),
        ),
      )
  }
}
```

Instead of providing two layers (`Server.live` and `ZLayer.fromZIO(ZIO.config(Server.Config.config))`) to the `Server.serve` method, we can combine them into a single layer using the `Server.configured` layer:

```scala
import zio._
import zio.http._

object MainApp extends ZIOAppDefault {
    override val bootstrap: ZLayer[ZIOAppArgs, Any, Any] =
      Runtime.setConfigProvider(ConfigProvider.fromResourcePath())
      
  def run = {
    Server
      .install(
        Routes(
          Method.GET / "hello" -> handler(Response.text("Hello, world!")),
        ),
      )
      .flatMap(port => ZIO.debug(s"Sever started on http://localhost:$port") *> ZIO.never)
      .provide(Server.configured())
  }
}
```

### Customized Layers

If we need to have more control, the `Server` and `Client` companion objects have also `customized` layers that require additional configuration settings to customize the underlying settings for the server and client:

- `Server.customized` is a layer that requires a `Server.Config` and `NettyConfig` and returns a `Server` layer.
- `Client.customized` is a layer that requires a `Client.Config`, `NettyConfig`, and `DnsResolver` and returns a `Client` layer.

```scala
object Clinet {
  val customized: ZLayer[Config with ClientDriver with DnsResolver, Throwable, Client] = ???
}

object Server {
  val customized: ZLayer[Config & NettyConfig, Throwable, Server] = ???
}
```

## Summary

In this guide, we learned how to integrate ZIO HTTP with ZIO Config to load configuration settings for our HTTP applications. We also learned how to load configuration settings from environment variables, system properties, and configuration files, such as HOCON and YAML using ZIO Config's configuration providers.
