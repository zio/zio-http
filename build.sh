#!/bin/bash

# Install SDKMAN
curl -s "https://get.sdkman.io" | bash
source "$HOME/.sdkman/bin/sdkman-init.sh"

# Install and use Java
sdk install java 11.0.20-tem
sdk use java 11.0.20-tem

# Install and use SBT
sdk install sbt 1.9.7
sdk use sbt 1.9.7

# Run mdoc
sbt mdoc

# Build website
cd website
yarn install
yarn run build
