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
    c=0
    while [[ $(curl -sL -w "%{http_code}\\n" "http://localhost:${PORT}/get/" -o /dev/null) != "200" && $c -lt 10 ]]; do
      ((c=$c + 1));
     sleep 2
    done
}

function main() {
    sbt "${SUB_PROJECT}/runMain ${SERVER_CLASS}" >server.log &
    SERVER=$!
    healthcheck $PORT
    if [[ $(curl -sL -w "%{http_code}\\n" "http://localhost:${PORT}/get/" -o /dev/null) = "200" ]]; then
        sbt "${SUB_PROJECT}/runMain ${CLIENT_CLASS}" >client_benchmark.log
    fi
    lsof -t -i :${PORT} | xargs kill -9
}

main

