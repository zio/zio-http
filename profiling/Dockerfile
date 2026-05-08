FROM eclipse-temurin:21-jdk

RUN wget https://www.yourkit.com/download/docker/YourKit-JavaProfiler-2022.9-docker.zip -P /tmp/ && \
  unzip /tmp/YourKit-JavaProfiler-2022.9-docker.zip -d /usr/local && \
  rm /tmp/YourKit-JavaProfiler-2022.9-docker.zip

ARG ARCH
ENV RUN_SERVER="java -agentpath:/usr/local/YourKit-JavaProfiler-2022.9/bin/${ARCH}/libyjpagent.so=port=10001,listen=all -Xms2G -Xmx2G -server -jar /zio-http/out/profiling/assembly.dest/out.jar"

WORKDIR /zio-http
COPY . .
RUN chmod +x ./mill && ./mill profiling.assembly

EXPOSE 8080
EXPOSE 10001
CMD ${RUN_SERVER}
