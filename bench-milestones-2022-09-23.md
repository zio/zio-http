# ZIO HTTP TechEmpower Benchmark Milestones

This document provides metrics for documenting performance deltas over the past few PRs that focused on increasing performance whilst hiding
Netty implementation details from zio-http's public facing API.

Early refactorings had a noticeably deleterious impact on performance.  As such, the effort was made to gain back lost performance by
revaluating these initial refactoring attempts.

## Commit References

  - Baseline:                 commit `0bf3168b`
  - Performance Pass 1 final: commit `ffd3d91f`
  - Request Domain split:     commit `eed2a559`

## Overall Results and Summary

All numbers represent Requests/sec.

| Benchmark                     | Baseline  | Performance Pass 1 | Request Domain Split | Perf Pass 1 over Baseline | Domain Split over Baseline | Domain Split over Perf Pass 1 |
| :---------------------------- | :-------- | :----------------- | :------------------- | :------------------------ | :------------------------- | :---------------------------- |
| 5 Threads @ 256 Connections   | 432994.78 | 466184.56          | 486750.66            | 7.67%                     | 12.41%                     | 4.41%                         |
| 5 Threads @ 1024 Connections  | 404421.91 | 458839.42          | 484600.55            | 13.46%                    | 19.83%                     | 5.61%                         |
| 5 Threads @ 4096 Connections  | 308526.28 | 453528.09          | 480459.06            | 47.00%                    | 55.73%                     | 5.94%                         |
| 5 Threads @ 16384 Connections | 277258.97 | 411902.48          | 425504.64            | 48.56%                    | 53.47%                     | 3.30%                         |
|                               |           |                    |                      |                           |                            |                               |

Overall there has not only been a marked increase in performance in each iteration but also a decrease in relative the performance degradation as the load is increased.


## Benchmarking Environment

### Machine
- **Hardware:** Apple M1 Max 64 GB RAM
- **OS:**       Monterey 12.5.1

### Docker
- **CPUs:** 5
- **Memory:** 7.90 GB
- **Swap:** 1 GB

## Raw Results

### Baseline

```
---------------------------------------------------------
 Running Primer plaintext
 wrk -H 'Host: tfb-server' -H 'Accept: text/plain,text/html;q=0.9,application/xhtml+xml;q=0.9,application/xml;q=0.8,*/*;q=0.7' -H 'Connection: keep-alive' --latency -d 5 -c 8 --timeout 8 -t 8 http://tfb-server:8080/plaintext
---------------------------------------------------------
Running 5s test @ http://tfb-server:8080/plaintext
  8 threads and 8 connections
  Thread Stats   Avg      Stdev     Max   +/- Stdev
    Latency   212.76us    1.13ms  39.21ms   98.02%
    Req/Sec    12.45k     4.22k   24.47k    74.13%
  Latency Distribution
     50%   56.00us
     75%  125.00us
     90%  275.00us
     99%    3.06ms
  498222 requests in 5.10s, 63.19MB read
Requests/sec:  97697.56
Transfer/sec:     12.39MB
---------------------------------------------------------
 Running Warmup plaintext
 wrk -H 'Host: tfb-server' -H 'Accept: text/plain,text/html;q=0.9,application/xhtml+xml;q=0.9,application/xml;q=0.8,*/*;q=0.7' -H 'Connection: keep-alive' --latency -d 15 -c 512 --timeout 8 -t 5 http://tfb-server:8080/plaintext
---------------------------------------------------------
Running 15s test @ http://tfb-server:8080/plaintext
  5 threads and 512 connections
  Thread Stats   Avg      Stdev     Max   +/- Stdev
    Latency     3.13ms    3.05ms  47.12ms   88.38%
    Req/Sec    36.51k    12.96k   82.64k    58.50%
  Latency Distribution
     50%    2.63ms
     75%    4.26ms
     90%    6.47ms
     99%   14.61ms
  2723670 requests in 15.09s, 345.47MB read
Requests/sec: 180443.43
Transfer/sec:     22.89MB
---------------------------------------------------------
 Concurrency: 256 for plaintext
 wrk -H 'Host: tfb-server' -H 'Accept: text/plain,text/html;q=0.9,application/xhtml+xml;q=0.9,application/xml;q=0.8,*/*;q=0.7' -H 'Connection: keep-alive' --latency -d 15 -c 256 --timeout 8 -t 5 http://tfb-server:8080/plaintext -s pipeline.lua -- 16
---------------------------------------------------------
Running 15s test @ http://tfb-server:8080/plaintext
  5 threads and 256 connections
  Thread Stats   Avg      Stdev     Max   +/- Stdev
    Latency     7.83ms    7.56ms 168.62ms   89.00%
    Req/Sec    87.17k    22.93k  142.40k    64.80%
  Latency Distribution
     50%    5.86ms
     75%   10.79ms
     90%   15.95ms
     99%   33.45ms
  6507808 requests in 15.03s, 825.44MB read
Requests/sec: 432994.78
Transfer/sec:     54.92MB
STARTTIME 1663714313
ENDTIME 1663714329
---------------------------------------------------------
 Concurrency: 1024 for plaintext
 wrk -H 'Host: tfb-server' -H 'Accept: text/plain,text/html;q=0.9,application/xhtml+xml;q=0.9,application/xml;q=0.8,*/*;q=0.7' -H 'Connection: keep-alive' --latency -d 15 -c 1024 --timeout 8 -t 5 http://tfb-server:8080/plaintext -s pipeline.lua -- 16
---------------------------------------------------------
Running 15s test @ http://tfb-server:8080/plaintext
  5 threads and 1024 connections
  Thread Stats   Avg      Stdev     Max   +/- Stdev
    Latency    27.46ms   20.67ms 383.80ms   74.58%
    Req/Sec    81.70k    21.33k  178.72k    56.67%
  Latency Distribution
     50%   22.71ms
     75%   37.09ms
     90%   54.82ms
     99%   94.11ms
  6102224 requests in 15.09s, 774.00MB read
Requests/sec: 404421.91
Transfer/sec:     51.30MB
STARTTIME 1663714331
ENDTIME 1663714346
---------------------------------------------------------
 Concurrency: 4096 for plaintext
 wrk -H 'Host: tfb-server' -H 'Accept: text/plain,text/html;q=0.9,application/xhtml+xml;q=0.9,application/xml;q=0.8,*/*;q=0.7' -H 'Connection: keep-alive' --latency -d 15 -c 4096 --timeout 8 -t 5 http://tfb-server:8080/plaintext -s pipeline.lua -- 16
---------------------------------------------------------
Running 15s test @ http://tfb-server:8080/plaintext
  5 threads and 4096 connections
  Thread Stats   Avg      Stdev     Max   +/- Stdev
    Latency   323.96ms  915.05ms   7.97s    93.41%
    Req/Sec    62.43k    14.50k  111.19k    70.34%
  Latency Distribution
     50%   89.74ms
     75%  143.90ms
     90%  235.60ms
     99%    5.15s 
  4633344 requests in 15.02s, 587.69MB read
  Socket errors: connect 0, read 0, write 0, timeout 742
Requests/sec: 308526.28
Transfer/sec:     39.13MB
STARTTIME 1663714348
ENDTIME 1663714363
---------------------------------------------------------
 Concurrency: 16384 for plaintext
 wrk -H 'Host: tfb-server' -H 'Accept: text/plain,text/html;q=0.9,application/xhtml+xml;q=0.9,application/xml;q=0.8,*/*;q=0.7' -H 'Connection: keep-alive' --latency -d 15 -c 16384 --timeout 8 -t 5 http://tfb-server:8080/plaintext -s pipeline.lua -- 16
---------------------------------------------------------
Running 15s test @ http://tfb-server:8080/plaintext
  5 threads and 16384 connections
  Thread Stats   Avg      Stdev     Max   +/- Stdev
    Latency   430.06ms  313.59ms   8.00s    79.61%
    Req/Sec    71.33k    46.90k  295.55k    73.58%
  Latency Distribution
     50%  392.24ms
     75%  591.55ms
     90%  761.53ms
     99%    1.03s 
  4180096 requests in 15.08s, 530.20MB read
  Socket errors: connect 0, read 0, write 0, timeout 54
Requests/sec: 277258.97
Transfer/sec:     35.17MB
STARTTIME 1663714365
ENDTIME 1663714380

```

### Performance Pass 1


```
---------------------------------------------------------
 Running Primer plaintext
 wrk -H 'Host: tfb-server' -H 'Accept: text/plain,text/html;q=0.9,application/xhtml+xml;q=0.9,application/xml;q=0.8,*/*;q=0.7' -H 'Connection: keep-alive' --latency -d 5 -c 8 --timeout 8 -t 8 http://tfb-server:8080/plaintext
---------------------------------------------------------
Running 5s test @ http://tfb-server:8080/plaintext
  8 threads and 8 connections
  Thread Stats   Avg      Stdev     Max   +/- Stdev
    Latency   110.76us  503.50us  16.06ms   98.17%
    Req/Sec    17.72k     4.29k   31.21k    72.17%
  Latency Distribution
     50%   53.00us
     75%   57.00us
     90%  108.00us
     99%    1.80ms
  715947 requests in 5.10s, 90.81MB read
Requests/sec: 140379.07
Transfer/sec:     17.81MB
---------------------------------------------------------
 Running Warmup plaintext
 wrk -H 'Host: tfb-server' -H 'Accept: text/plain,text/html;q=0.9,application/xhtml+xml;q=0.9,application/xml;q=0.8,*/*;q=0.7' -H 'Connection: keep-alive' --latency -d 15 -c 512 --timeout 8 -t 5 http://tfb-server:8080/plaintext
---------------------------------------------------------
Running 15s test @ http://tfb-server:8080/plaintext
  5 threads and 512 connections
  Thread Stats   Avg      Stdev     Max   +/- Stdev
    Latency     3.13ms    2.83ms  41.36ms   82.11%
    Req/Sec    34.70k     9.74k   78.57k    65.15%
  Latency Distribution
     50%    2.67ms
     75%    4.24ms
     90%    6.16ms
     99%   13.84ms
  2588593 requests in 15.10s, 328.33MB read
  Socket errors: connect 0, read 0, write 0, timeout 46
Requests/sec: 171468.42
Transfer/sec:     21.75MB
---------------------------------------------------------
 Concurrency: 256 for plaintext
 wrk -H 'Host: tfb-server' -H 'Accept: text/plain,text/html;q=0.9,application/xhtml+xml;q=0.9,application/xml;q=0.8,*/*;q=0.7' -H 'Connection: keep-alive' --latency -d 15 -c 256 --timeout 8 -t 5 http://tfb-server:8080/plaintext -s pipeline.lua -- 16
---------------------------------------------------------
Running 15s test @ http://tfb-server:8080/plaintext
  5 threads and 256 connections
  Thread Stats   Avg      Stdev     Max   +/- Stdev
    Latency     6.96ms    6.49ms 150.40ms   88.17%
    Req/Sec    93.92k     8.47k  151.27k    86.67%
  Latency Distribution
     50%    5.02ms
     75%    9.86ms
     90%   14.11ms
     99%   27.74ms
  7009600 requests in 15.04s, 0.87GB read
Requests/sec: 466184.56
Transfer/sec:     59.13MB
STARTTIME 1663800912
ENDTIME 1663800928
---------------------------------------------------------
 Concurrency: 1024 for plaintext
 wrk -H 'Host: tfb-server' -H 'Accept: text/plain,text/html;q=0.9,application/xhtml+xml;q=0.9,application/xml;q=0.8,*/*;q=0.7' -H 'Connection: keep-alive' --latency -d 15 -c 1024 --timeout 8 -t 5 http://tfb-server:8080/plaintext -s pipeline.lua -- 16
---------------------------------------------------------
Running 15s test @ http://tfb-server:8080/plaintext
  5 threads and 1024 connections
  Thread Stats   Avg      Stdev     Max   +/- Stdev
    Latency    23.53ms   22.01ms 565.30ms   91.79%
    Req/Sec    92.63k     6.40k  148.80k    86.13%
  Latency Distribution
     50%   19.61ms
     75%   31.43ms
     90%   43.13ms
     99%   78.89ms
  6914416 requests in 15.07s, 0.86GB read
Requests/sec: 458839.42
Transfer/sec:     58.20MB
STARTTIME 1663800930
ENDTIME 1663800945
---------------------------------------------------------
 Concurrency: 4096 for plaintext
 wrk -H 'Host: tfb-server' -H 'Accept: text/plain,text/html;q=0.9,application/xhtml+xml;q=0.9,application/xml;q=0.8,*/*;q=0.7' -H 'Connection: keep-alive' --latency -d 15 -c 4096 --timeout 8 -t 5 http://tfb-server:8080/plaintext -s pipeline.lua -- 16
---------------------------------------------------------
Running 15s test @ http://tfb-server:8080/plaintext
  5 threads and 4096 connections
  Thread Stats   Avg      Stdev     Max   +/- Stdev
    Latency   293.95ms  900.60ms   7.98s    94.17%
    Req/Sec    92.03k    13.59k  145.74k    69.34%
  Latency Distribution
     50%   78.80ms
     75%  128.69ms
     90%  201.68ms
     99%    5.32s 
  6849008 requests in 15.10s, 868.72MB read
  Socket errors: connect 0, read 0, write 0, timeout 55
Requests/sec: 453528.09
Transfer/sec:     57.52MB
STARTTIME 1663800947
ENDTIME 1663800962
---------------------------------------------------------
 Concurrency: 16384 for plaintext
 wrk -H 'Host: tfb-server' -H 'Accept: text/plain,text/html;q=0.9,application/xhtml+xml;q=0.9,application/xml;q=0.8,*/*;q=0.7' -H 'Connection: keep-alive' --latency -d 15 -c 16384 --timeout 8 -t 5 http://tfb-server:8080/plaintext -s pipeline.lua -- 16
---------------------------------------------------------
Running 15s test @ http://tfb-server:8080/plaintext
  5 threads and 16384 connections
  Thread Stats   Avg      Stdev     Max   +/- Stdev
    Latency   306.34ms  283.85ms   7.98s    94.33%
    Req/Sec    97.28k    54.47k  342.79k    72.84%
  Latency Distribution
     50%  274.75ms
     75%  413.21ms
     90%  532.41ms
     99%  726.45ms
  6220288 requests in 15.10s, 788.97MB read
  Socket errors: connect 0, read 0, write 0, timeout 124
Requests/sec: 411902.48
Transfer/sec:     52.25MB
STARTTIME 1663800964
ENDTIME 1663800979
```

### Request Domain Split 

```
---------------------------------------------------------
 Running Primer plaintext
 wrk -H 'Host: tfb-server' -H 'Accept: text/plain,text/html;q=0.9,application/xhtml+xml;q=0.9,application/xml;q=0.8,*/*;q=0.7' -H 'Connection: keep-alive' --latency -d 5 -c 8 --timeout 8 -t 8 http://tfb-server:8080/plaintext
---------------------------------------------------------
Running 5s test @ http://tfb-server:8080/plaintext
  8 threads and 8 connections
  Thread Stats   Avg      Stdev     Max   +/- Stdev
    Latency    93.55us  479.60us  20.18ms   98.73%
    Req/Sec    18.51k     4.56k   31.68k    74.07%
  Latency Distribution
     50%   52.00us
     75%   56.00us
     90%   92.00us
     99%    0.97ms
  745642 requests in 5.10s, 94.58MB read
Requests/sec: 146202.59
Transfer/sec:     18.54MB
---------------------------------------------------------
 Running Warmup plaintext
 wrk -H 'Host: tfb-server' -H 'Accept: text/plain,text/html;q=0.9,application/xhtml+xml;q=0.9,application/xml;q=0.8,*/*;q=0.7' -H 'Connection: keep-alive' --latency -d 15 -c 512 --timeout 8 -t 5 http://tfb-server:8080/plaintext
---------------------------------------------------------
Running 15s test @ http://tfb-server:8080/plaintext
  5 threads and 512 connections
  Thread Stats   Avg      Stdev     Max   +/- Stdev
    Latency     3.07ms    2.81ms  53.80ms   82.89%
    Req/Sec    35.78k     9.97k   60.24k    64.04%
  Latency Distribution
     50%    2.64ms
     75%    4.12ms
     90%    6.13ms
     99%   13.79ms
  2675501 requests in 15.09s, 339.36MB read
Requests/sec: 177281.91
Transfer/sec:     22.49MB
---------------------------------------------------------
 Concurrency: 256 for plaintext
 wrk -H 'Host: tfb-server' -H 'Accept: text/plain,text/html;q=0.9,application/xhtml+xml;q=0.9,application/xml;q=0.8,*/*;q=0.7' -H 'Connection: keep-alive' --latency -d 15 -c 256 --timeout 8 -t 5 http://tfb-server:8080/plaintext -s pipeline.lua -- 16
---------------------------------------------------------
Running 15s test @ http://tfb-server:8080/plaintext
  5 threads and 256 connections
  Thread Stats   Avg      Stdev     Max   +/- Stdev
    Latency     6.69ms    6.92ms 174.80ms   89.93%
    Req/Sec    97.95k     9.11k  118.63k    88.13%
  Latency Distribution
     50%    4.81ms
     75%    9.29ms
     90%   13.64ms
     99%   26.19ms
  7310608 requests in 15.02s, 0.91GB read
Requests/sec: 486750.66
Transfer/sec:     61.74MB
STARTTIME 1663946114
ENDTIME 1663946129
---------------------------------------------------------
 Concurrency: 1024 for plaintext
 wrk -H 'Host: tfb-server' -H 'Accept: text/plain,text/html;q=0.9,application/xhtml+xml;q=0.9,application/xml;q=0.8,*/*;q=0.7' -H 'Connection: keep-alive' --latency -d 15 -c 1024 --timeout 8 -t 5 http://tfb-server:8080/plaintext -s pipeline.lua -- 16
---------------------------------------------------------
Running 15s test @ http://tfb-server:8080/plaintext
  5 threads and 1024 connections
  Thread Stats   Avg      Stdev     Max   +/- Stdev
    Latency    26.83ms   54.32ms   1.44s    97.75%
    Req/Sec    97.80k     8.18k  139.64k    84.67%
  Latency Distribution
     50%   18.63ms
     75%   30.12ms
     90%   41.77ms
     99%  216.10ms
  7299808 requests in 15.06s, 0.90GB read
Requests/sec: 484600.55
Transfer/sec:     61.47MB
STARTTIME 1663946131
ENDTIME 1663946146
---------------------------------------------------------
 Concurrency: 4096 for plaintext
 wrk -H 'Host: tfb-server' -H 'Accept: text/plain,text/html;q=0.9,application/xhtml+xml;q=0.9,application/xml;q=0.8,*/*;q=0.7' -H 'Connection: keep-alive' --latency -d 15 -c 4096 --timeout 8 -t 5 http://tfb-server:8080/plaintext -s pipeline.lua -- 16
---------------------------------------------------------
Running 15s test @ http://tfb-server:8080/plaintext
  5 threads and 4096 connections
  Thread Stats   Avg      Stdev     Max   +/- Stdev
    Latency   336.90ms  990.25ms   7.97s    92.84%
    Req/Sec    97.24k    27.72k  159.52k    63.28%
  Latency Distribution
     50%   67.13ms
     75%  112.80ms
     90%  273.40ms
     99%    5.52s 
  7250048 requests in 15.09s, 0.90GB read
  Socket errors: connect 0, read 0, write 0, timeout 747
Requests/sec: 480459.06
Transfer/sec:     60.94MB
STARTTIME 1663946148
ENDTIME 1663946163
---------------------------------------------------------
 Concurrency: 16384 for plaintext
 wrk -H 'Host: tfb-server' -H 'Accept: text/plain,text/html;q=0.9,application/xhtml+xml;q=0.9,application/xml;q=0.8,*/*;q=0.7' -H 'Connection: keep-alive' --latency -d 15 -c 16384 --timeout 8 -t 5 http://tfb-server:8080/plaintext -s pipeline.lua -- 16
---------------------------------------------------------
Running 15s test @ http://tfb-server:8080/plaintext
  5 threads and 16384 connections
  Thread Stats   Avg      Stdev     Max   +/- Stdev
    Latency   309.03ms  275.57ms   7.98s    93.63%
    Req/Sec    99.51k    53.43k  422.06k    72.46%
  Latency Distribution
     50%  278.35ms
     75%  419.76ms
     90%  539.19ms
     99%  743.30ms
  6421968 requests in 15.09s, 814.55MB read
  Socket errors: connect 0, read 0, write 0, timeout 154
Requests/sec: 425504.64
Transfer/sec:     53.97MB
STARTTIME 1663946165
ENDTIME 1663946181
```