---
id: performance
title: Performance
---

## Performance 


The ZIO-HTTP server offers a streamlined layer that sits atop the underlying server APIs, resulting in minimal overhead compared to directly utilizing the raw server APIs. This abstraction layer allows for handling HTTP requests and responses in a purely functional and composable manner.


ZIO-HTTP leverages the powerful concurrency and composition capabilities of the ZIO library to provide high-performance, asynchronous handling of HTTP requests. It utilizes non-blocking I/O and lightweight fibers for concurrency, allowing efficient resource utilization and minimizing overhead.


## Web Frameworks Benchmark

Results of zio-http on a well-regarded [Web Frameworks Benchmark](https://web-frameworks-benchmark.netlify.app/) project, which evaluates frameworks through a series of practical tests.

ZIO-HTTP's performance is not just theoretical; it has been tested and validated against realistic benchmarks. `ZIO-HTTP` has demonstrated its ability to handle various real-world scenarios with exceptional speed and efficiency.

The full implementation of the benchmark can be found [here](https://github.com/the-benchmarker/web-frameworks/tree/master/scala/zio-http).


### Technical Details

ZIO-HTTP was benchmarked using wrk (threads: 8, timeout: 8, duration: 15 seconds) with 64, 256, and 512 concurrency.
Hardware used for the benchmark:

* CPU: 8 Cores (AMD FX-8320E Eight-Core Processor)
* RAM: 16 GB
* OS: Linux



### Benchmark Result

                                    Zio-http
                                    
| Benchmark                   | Resut       |
| :-------                    | -------:    |
| Requests/Second (64)        | 103 569     |
| Requests/second (256)       | 117 193     |
| Requests/second (512)       | 114 772     |
| P50 latency (64)            | 0.49 ms     |
| P50 latency (256)           | 1.84 ms     |
| P50 latency (512)           | 4.42 ms     |
| Average Latency (ms) (64)   | 1.17 ms     |
| Average Latency (ms) (256)  | 2.30 ms     |
| Average Latency (ms) (512)  | 4.76 ms     |
| Minimum Latency (ms) (64)   | 0.05 ms     |
| Minimum Latency (ms) (256)  | 0.05 ms     |
| Minimum Latency (ms) (512)  | 0.06 ms     |
| Maximum Latency (ms) (64)   | 104.91 ms   |
| Maximum Latency (ms) (256)  | 41.14 ms    |
| Maximum Latency (ms) (512)  | 57.66 ms    |




 ZIO-HTTP's remarkable benchmark performance can be attributed to its concurrency model, lightweight execution, asynchronous I/O, functional design principles, composable middleware, integration with the ZIO ecosystem, and validation through realistic benchmarking. These factors combined make ZIO-HTTP an excellent choice for building high-performance and scalable web applications.
