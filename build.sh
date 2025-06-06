#!/bin/bash

# Install SDKMAN
curl -s "https://get.sdkman.io" | bash
source "$HOME/.sdkman/bin/sdkman-init.sh"

# Install and use Java
sdk install java 24.0.1-tem
sdk use java 24.0.1-tem

# Install and use SBT
sdk install sbt 1.10.11
sdk use sbt 1.10.11

# Run mdoc
sbt mdoc

# Build website
cd website
yarn install
yarn run build
