#!/bin/bash

set -xeu pipeline

SUB_PROJECT=${SUB_PROJECT:-"example"}
SERVER_CLASS=${SERVER_FILE:-"example.SimpleServer"}
CLIENT_CLASS=${CLIENT_FILE:-"example.ClientBenchmark"}
PORT=${PORT:-7777}

function usage() {
    echo """
        This script will run a zio-http server and a client benchmarker.
    """
}

function healthcheck() {
    while [[ $(curl -sL -w "%{http_code}\\n" "http://localhost:${PORT}/" -o /dev/null) != "200" ]]; do
     sleep 2
    done
}

function main() {
    sbt "${SUB_PROJECT}/runMain ${SERVER_CLASS}" >/dev/null &
    SERVER=$!
    
    healthcheck $PORT
    
    sbt "${SUB_PROJECT}/runMain ${CLIENT_CLASS}" >client_benchmark.log
    
    lsof -t -i :${PORT} | xargs kill -9
}

main

