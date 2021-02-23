# ZIO-HTTP
##Environment
**We have used 2 machines**
1. EC2(C5.4xLarge) 16 vCPUs 32 GB RAM as server
2. EC2(C5.4xLarge) 16 vCPUs 32 GB RAM as client with wrk setup

##Benchmarks

### ZIO-HTTP
####Plain Text
```dtd
./wrk -t12 -c1000 --latency --timeout=10s --duration=10s http://10.10.105.8:8090
Running 10s test @ http://10.10.105.8:8090
  12 threads and 1000 connections
  Thread Stats   Avg      Stdev     Max   +/- Stdev
    Latency     1.37ms  844.59us 206.84ms   97.72%
    Req/Sec    60.42k     2.20k   74.51k    70.22%
  Latency Distribution
     50%    1.28ms
     75%    1.48ms
     90%    1.72ms
     99%    2.55ms
  7267713 requests in 10.10s, 346.55MB read
Requests/sec: 719576.04
Transfer/sec:     34.31MB
```
####JSON
```dtd
./wrk -t12 -c1000 --latency --timeout=10s --duration=10s http://10.10.105.8:8090/json
Running 10s test @ http://10.10.105.8:8090/json
  12 threads and 1000 connections
  Thread Stats   Avg      Stdev     Max   +/- Stdev
    Latency     1.40ms  421.62us  32.84ms   90.14%
    Req/Sec    58.73k     2.81k   68.19k    68.51%
  Latency Distribution
     50%    1.32ms
     75%    1.53ms
     90%    1.80ms
     99%    2.49ms
  7070158 requests in 10.10s, 660.78MB read
Requests/sec: 700073.31
```
