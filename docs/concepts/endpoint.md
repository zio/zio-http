---
id: endpoint
title: "Endpoint"
---

# Endpoint

In ZIO HTTP, an `Endpoint` is a fundamental building block that represents a single HTTP route or operation. It offers a declarative approach to defining your API's contract, encompassing the HTTP method, path pattern, input parameters, output types and potential errors.

## Key Concepts of Endpoint:


### Defining an Endpoint

An `Endpoint` is typically defined using the `Endpoint` constructor or builder methods. The constructor accepts parameters to specify the endpoint's characteristics like the HTTP method, path pattern, query parameters, request body and response types.

### Implementing an Endpoint

After defining an endpoint, you need to provide a handler function to process incoming requests and produce desired responses or handle errors. This handler function is attached using the `implement` method.

### Handling Errors

ZIO HTTP provides various ways to handle errors in endpoints. You can define specific error types for your endpoint and map them to appropriate HTTP status codes.

## Optional

### OpenAPI Documentation

ZIO HTTP allows us to generate OpenAPI documentation from `Endpoint` definitions, which can be used to create Swagger UI routes.


### Generating ZIO CLI App from Endpoint API

ZIO HTTP allows you to generate a ZIO CLI client application from `Endpoint` definitions using the `HttpCliApp.fromEndpoints` constructor.
