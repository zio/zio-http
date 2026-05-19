---
id: index
sidebar_label: Datastar SDK
title: Integration of Datastar with ZIO HTTP
---

[Datastar](https://data-star.dev/) is a hypermedia-driven framework for building reactive web applications with minimal JavaScript. The `zio-http-datastar-sdk` integrates Datastar with ZIO HTTP, bringing these capabilities to the ZIO ecosystem and allowing developers to create server-driven UIs with minimal frontend complexity.

In Datastar, the server sends HTML elements that are integrated into the web page. Instead of building a data based API (JSON, XML, etc.) and rendering HTML on the client, the rendering happens on the server, and the HTML elements—including hypermedia controls—are sent to the browser.

This matters because it solves a critical problem in modern web development: building interactive, real-time applications traditionally requires heavy frontend frameworks and complex state synchronization. The Datastar integration provides a simpler alternative for server-driven applications where state lives on the backend, updates flow via SSE or HTTP transactions, and the frontend remains lightweight (about 10.7 KB). 

## Datastar Overview

Datastar uses declarative `data-*` HTML attributes to define the application state and behavior on the client side.

Datastar uses signals to represent reactive state variables that can be updated both on the client and server sides. Signals are prefixed with `$` (like `$username`, `$count`). These signals are automatically sent to the backend with each request, and the server can patch them by sending signal patches back to the client.

For example, when a user types into an input field bound with `data-bind:email`, the `$email` signal updates locally and gets transmitted to the server with subsequent requests. The server can then push signal updates back using JSON Merge Patch (RFC 7396), or send HTML fragments that morph into the DOM. This flow can happen over SSE connections or regular HTTP transactions.

Datastar shines in scenarios where you want to build dynamic, real-time web applications without the overhead of heavy frontend frameworks. Here are some common use cases:

- Chat messages appearing live.
- Monitoring logs, metrics, or notifications.
- Live search results that update as you type.
- Real-time dashboard panels updating from streaming endpoints.

## Reactive Hypermedia with ZIO HTTP

The `zio-http-datastar-sdk` provides both server-side and client-side utilities to provide a unified web development experience within the ZIO ecosystem:

1. The server-side API shields developers from low-level SSE protocol details, providing server-sent event generators for creating [Datastar SSE event types](https://data-star.dev/reference/sse_events) such as patching elements and signals, and executing scripts.
2. The client-side API offers a ZIO-friendly way to embed Datastar [attributes](https://data-star.dev/reference/attributes) into HTML responses, making it easy to create reactive UIs that seamlessly integrate with ZIO HTTP's templating capabilities.

## Installation

To use the Datastar SDK with ZIO HTTP, add the following dependency to your `build.sbt` file:

```scala
libraryDependencies += "dev.zio" %% "zio-http-datastar-sdk" % "@VERSION@"
```

You also have to include the Datastar JavaScript client module in your HTML pages. You can do this by adding the following script tag to your HTML:

```scala mdoc:compile-only
import zio.http.datastar._
import zio.http.template2._

// Uses the default version of datastar from the zio-http-datastar-sdk module
head(datastarScript)

// Or specify a custom version of datastar
head(datastarScript(version = "1.2.3"))
```

Pick the proper version of the module according to the [installation instructions](https://data-star.dev/guide/getting_started#installation) in the Datastar's documentation.

## Basic Usage

After reading the [Getting Started](https://data-star.dev/guide/getting_started) guide and learning the basics of Datastar, you are ready to dive into examples and the reference documentation.

## Documentation Structure

The Datastar SDK reference is organized into the following sections:

- **[HTML Attributes](./attributes.md)** — Complete reference of all `data-*` attributes and how to use them for declaring state and behavior
- **[Extracting Signals](./signals.md)** — How to read client signal values from requests on the server
- **[Event Generation](./server-api.md)** — Server-side helpers for sending updates via single-shot responses or SSE streaming
- **[Examples](./examples.md)** — Runnable example applications demonstrating key patterns and features
