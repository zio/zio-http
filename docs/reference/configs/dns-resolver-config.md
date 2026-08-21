---
id: dns-resolver
title: "DNS Resolver Config"
description: "Reference for the ZIO HTTP DNS resolver configuration, including cache sizes, TTLs and retry behaviour."
---

```scala mdoc:passthrough
import zio.http.docs.ConfigReference
import zio.http.DnsResolver

println(ConfigReference.referencePageFor(DnsResolver.Config))
```