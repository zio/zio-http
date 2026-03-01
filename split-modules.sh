#!/bin/bash
# Split zio-http into multiple modules
# Based on PR #3934 approach

set -e

echo "=== ZIO-HTTP Module Splitter ==="
echo "Splitting zio-http into core/endpoint/netty modules"

# Create directory structure
echo "Creating module directories..."
mkdir -p zio-http-core/shared/src/main/scala/zio/http
mkdir -p zio-http-core/shared/src/test/scala/zio/http
mkdir -p zio-http-endpoint/shared/src/main/scala/zio/http/endpoint
mkdir -p zio-http-endpoint/shared/src/test/scala/zio/http/endpoint
mkdir -p zio-http-netty/jvm/src/main/scala/zio/http/netty
mkdir -p zio-http-netty/jvm/src/test/scala/zio/http/netty

# Core module files (no Netty dependency)
CORE_FILES=(
  "Body.scala"
  "Boundary.scala"
  "Channel.scala"
  "ChannelEvent.scala"
  "Charsets.scala"
  "Cookie.scala"
  "Credentials.scala"
  "Decompression.scala"
  "Flash.scala"
  "Form.scala"
  "FormDecodingError.scala"
  "FormField.scala"
  "Handler.scala"
  "HandlerAspect.scala"
  "Header.scala"
  "Headers.scala"
  "MediaType.scala"
  "MediaTypes.scala"
  "Method.scala"
  "Middleware.scala"
  "Mode.scala"
  "Path.scala"
  "QueryParams.scala"
  "Request.scala"
  "Response.scala"
  "Route.scala"
  "Routes.scala"
  "Scheme.scala"
  "Status.scala"
  "URL.scala"
  "Version.scala"
  "ZClient.scala"
  "ZConnectionPool.scala"
)

# Copy core files
echo "Copying core module files..."
for file in "${CORE_FILES[@]}"; do
  if [ -f "zio-http/shared/src/main/scala/zio/http/$file" ]; then
    cp "zio-http/shared/src/main/scala/zio/http/$file" "zio-http-core/shared/src/main/scala/zio/http/"
    echo "  ✓ $file"
  fi
done

# Copy codec and internal packages
echo "Copying codec and internal packages..."
cp -r zio-http/shared/src/main/scala/zio/http/codec zio-http-core/shared/src/main/scala/zio/http/ 2>/dev/null || true
cp -r zio-http/shared/src/main/scala/zio/http/internal zio-http-core/shared/src/main/scala/zio/http/ 2>/dev/null || true
cp -r zio-http/shared/src/main/scala/zio/http/template zio-http-core/shared/src/main/scala/zio/http/ 2>/dev/null || true
cp -r zio-http/shared/src/main/scala/zio/http/template2 zio-http-core/shared/src/main/scala/zio/http/ 2>/dev/null || true

# Endpoint module files
ENDPOINT_FILES=(
  "endpoint/Endpoint.scala"
  "endpoint/EndpointExecutor.scala"
  "endpoint/EndpointLocator.scala"
  "endpoint/Alternator.scala"
  "endpoint/AuthType.scala"
  "endpoint/ChunkedCodec.scala"
  "endpoint/Contract.scala"
  "endpoint/Doc.scala"
  "endpoint/EndpointGen.scala"
  "endpoint/EndpointMiddleware.scala"
  "endpoint/HeaderCodecs.scala"
  "endpoint/HttpCodec.scala"
  "endpoint/HttpContentCodec.scala"
  "endpoint/Mechanism.scala"
  "endpoint/MultipartCodec.scala"
  "endpoint/OpenAPI.scala"
  "endpoint/OpenAPIGen.scala"
  "endpoint/PathCodec.scala"
  "endpoint/QueryCodecs.scala"
  "endpoint/RequestEncoder.scala"
  "endpoint/ResponseDecoder.scala"
  "endpoint/Routes.scala"
  "endpoint/SegmentCodec.scala"
  "endpoint/package.scala"
)

echo "Copying endpoint module files..."
for file in "${ENDPOINT_FILES[@]}"; do
  if [ -f "zio-http/shared/src/main/scala/zio/http/$file" ]; then
    mkdir -p "zio-http-endpoint/shared/src/main/scala/zio/http/$(dirname $file)"
    cp "zio-http/shared/src/main/scala/zio/http/$file" "zio-http-endpoint/shared/src/main/scala/zio/http/$file"
    echo "  ✓ $file"
  fi
done

# Netty module files (JVM only)
NETTY_FILES=(
  "netty/NettyBody.scala"
  "netty/NettyBodyWriter.scala"
  "netty/NettyChannel.scala"
  "netty/NettyClientDriver.scala"
  "netty/NettyConfig.scala"
  "netty/NettyConnectionPool.scala"
  "netty/NettyCookieEncoding.scala"
  "netty/NettyDriver.scala"
  "netty/NettyFutureExecutor.scala"
  "netty/NettyHeaderEncoding.scala"
  "netty/NettyHttpApp.scala"
  "netty/NettyRequest.scala"
  "netty/NettyResponse.scala"
  "netty/NettyRuntime.scala"
  "netty/NettyServer.scala"
  "netty/NettyServerHandler.scala"
  "netty/NettySocketProgramming.scala"
  "netty/NettyStreamBody.scala"
  "netty/NettyWsApp.scala"
  "netty/WebSocketApp.scala"
)

echo "Copying netty module files..."
for file in "${NETTY_FILES[@]}"; do
  if [ -f "zio-http/jvm/src/main/scala/zio/http/$file" ]; then
    mkdir -p "zio-http-netty/jvm/src/main/scala/zio/http/$(dirname $file)"
    cp "zio-http/jvm/src/main/scala/zio/http/$file" "zio-http-netty/jvm/src/main/scala/zio/http/$file"
    echo "  ✓ $file"
  fi
done

# Also copy from shared if exists
for file in "${NETTY_FILES[@]}"; do
  if [ -f "zio-http/shared/src/main/scala/zio/http/$file" ]; then
    mkdir -p "zio-http-netty/shared/src/main/scala/zio/http/$(dirname $file)"
    cp "zio-http/shared/src/main/scala/zio/http/$file" "zio-http-netty/shared/src/main/scala/zio/http/$file"
    echo "  ✓ $file (shared)"
  fi
done

echo ""
echo "=== Module split complete ==="
echo "Core files: $(find zio-http-core -name '*.scala' | wc -l)"
echo "Endpoint files: $(find zio-http-endpoint -name '*.scala' | wc -l)"
echo "Netty files: $(find zio-http-netty -name '*.scala' | wc -l)"
