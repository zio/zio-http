#!/bin/bash

# Install SDKMAN
curl -s "https://get.sdkman.io" | bash
source "$HOME/.sdkman/bin/sdkman-init.sh"

# Install and use Java
sdk install java 21.0.7-tem
sdk use java 21.0.7-tem

# Install and use SBT
sdk install sbt 1.11.1
sdk use sbt 1.11.1

# Run mdoc
sbt mdoc

# Build website
cd website
yarn install
yarn run build
