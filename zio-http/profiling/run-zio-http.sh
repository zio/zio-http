#!/bin/bash

COMMIT_SHA=$(git rev-parse --short HEAD)
docker run -i -p 8080:8080 -p 10001:10001 --rm zio-http:$COMMIT_SHA