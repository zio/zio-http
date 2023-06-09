
# ZMX Metric Reference

All metrics in ZMX are defined as aspects that can be applied to effects without changing the signature of the effect it is applied to.

Metric aspects are further qualified by a type parameter `A` that must be compatible with the output type of the effect. This means that a `MetricAspect[Any]` can be applied to any effect, while a `MetricAspect[Double]` can only be applied to effects producing a `Double` value.

Each metric understands a certain data type it can observe to manipulate its state. Counters, Gauges, Histograms, and Summaries all understand `Double` values, while a Set understands `String` values.

In cases where the output type of an effect is not compatible with the type required to manipulate the metric, the API defines a `xxxxWith` method to construct a `MetricAspect[A]` with a mapper function from `A` to the type required by the metric.

The API functions in this document are implemented in the `MetricAspect` object. An aspect can be applied to an effect with the `@@` operator.

Once an application is instrumented with ZMX aspects, it can be configured with a client implementation that is responsible for providing the captured metrics to an appropriate backend. Currently, ZMX supports clients for StatsD and Prometheus out of the box.

## Counter

A counter in ZMX is simply a named variable that increases over time.

### API

Create a counter that is incremented by 1 every time it is executed successfully. This can be applied to any effect.

```scala
def count(name: String, tags: Label*): MetricAspect[Any]
```

Create a counter that counts the number of failed executions of the effect it is applied to. This can be applied to any effect.

```scala
def countErrors(name: String, tags: Label*): MetricAspect[Any]
```

Create a counter that can be applied to effects producing a `Double` value. The counter will be increased by the value the effect produces.

```scala
def countValue(name: String, tags: Label*): MetricAspect[Double]
```

Create a counter that can be applied to effects producing a value of type `A`. Given the effect produces `v: A`, the counter will be increased by `f(v)`.

```scala
def countValueWith[A](name: String, tags: Label*)(f: A => Double): MetricAspect[A]
```

### Examples

Create a counter named `countAll` that is incremented by 1 every time it is invoked.

```scala
val aspCountAll = MetricAspect.count("countAll")
```

Now the counter can be applied to any effect. Note that the same aspect can be applied to more than one effect. In the example, we count the sum of executions of both effects in the `for` comprehension.

```scala
val countAll = for {
  _ <- ZIO.unit @@ aspCountAll
  _ <- ZIO.unit @@ aspCountAll
} yield ()
```

Create a counter named `countBytes` that can be applied to effects producing a `Double` value.

```scala
val aspCountBytes = MetricAspect.countValue("countBytes")
```

Now we can apply it to effects producing a `Double`. In a real application, the value might be the number of bytes read from a stream or something similar.

```scala
val countBytes = nextDoubleBetween(0.0d, 100.0d) @@ aspCountBytes
```

## Gauges

A gauge in ZMX is a named

 variable of type `Double` that can change over time. It can either be set to an absolute value or relative to the current value.

### API

Create a gauge that can be set to absolute values. It can be applied to effects yielding a `Double` value.

```scala
def setGauge(name: String, tags: Label*): MetricAspect[Double]
```

Create a gauge that can be set to absolute values. It can be applied to effects producing a value of type `A`. Given the effect produces `v: A`, the gauge will be set to `f(v)` upon successful execution of the effect.

```scala
def setGaugeWith[A](name: String, tags: Label*)(f: A => Double): MetricAspect[A]
```

Create a gauge that can be set relative to its previous value. It can be applied to effects yielding a `Double` value.

```scala
def adjustGauge(name: String, tags: Label*): MetricAspect[Double]
```

Create a gauge that can be set relative to its previous value. It can be applied to effects producing a value of type `A`. Given the effect produces `v: A`, the gauge will be modified by `_ + f(v)` upon successful execution of the effect.

```scala
def adjustGaugeWith[A](name: String, tags: Label*)(f: A => Double): MetricAspect[A]
```

### Examples

Create a gauge that can be set to absolute values. It can be applied to effects yielding a `Double` value.

```scala
val aspGaugeAbs = MetricAspect.setGauge("setGauge")
```

Create a gauge that can be set relative to its current value. It can be applied to effects yielding a `Double` value.

```scala
val aspGaugeRel = MetricAspect.adjustGauge("adjustGauge")
```

Now we can apply these effects to effects having an output type `Double`. Note that we can instrument an effect with any number of aspects if the type constraints are satisfied.

```scala
val gaugeSomething = for {
  _ <- nextDoubleBetween(0.0d, 100.0d) @@ aspGaugeAbs @@ aspCountAll
  _ <- nextDoubleBetween(-50d, 50d) @@ aspGaugeRel @@ aspCountAll
} yield ()
```

## Histograms

A histogram observes `Double` values and counts the observed values in buckets. Each bucket is defined by an upper boundary, and the count for a bucket with the upper boundary `b` increases by 1 if an observed value `v` is less than or equal to `b`.

As a consequence, all buckets that have a boundary `b1` with `b1 > b` will increase by 1 after observing `v`.

A histogram also keeps track of the overall count of observed values and the sum of all observed values.

By definition, the last bucket is always defined as `Double.MaxValue`, so that the count of observed values in the last bucket is always equal to the overall count of observed values within the histogram.

To define a histogram aspect, the API requires specifying the boundaries for the histogram when creating the aspect.

The mental model for a ZMX histogram is inspired by Prometheus.

### API

Create a histogram that can be applied to effects producing `Double` values. The values will be counted as outlined above.

```scala
def observeHistogram(name: String, boundaries: Chunk[Double], tags: Label*): MetricAspect[Double]
```

Create a histogram that can be applied to effects producing values `v` of type `A`. The values `f(v)` will be counted as outlined above.

```scala
def observeHistogramWith[A

](name: String, boundaries: Chunk[Double], tags: Label*)(f: A => Double): MetricAspect[A]
```

### Examples

Create a histogram with 12 buckets: 0..100 in steps of 10 and `Double.MaxValue`. It can be applied to effects yielding a `Double` value.

```scala
val aspHistogram =
  MetricAspect.observeHistogram("histogram", DoubleHistogramBuckets.linear(0.0d, 10.0d, 11).boundaries)
```

Now we can apply the histogram to effects producing `Double`:

```scala
val histogram = nextDoubleBetween(0.0d, 120.0d) @@ aspHistogram
```

## Summaries

Similar to a histogram, a summary also observes `Double` values. While a histogram directly modifies the bucket counters and does not keep the individual samples, the summary keeps the observed samples in its internal state. To avoid the set of samples growing uncontrolled, the summary needs to be configured with a maximum age `t` and a maximum size `n`. To calculate the statistics, at most `n` samples will be used, all of which are not older than `t`.

Essentially, the set of samples is a sliding window over the last observed samples matching the conditions above.

A summary is used to calculate a set of quantiles over the current set of samples. A quantile is defined by a `Double` value `q` with `0 <= q <= 1` and resolves to a `Double` as well.

The value of a given quantile `q` is the maximum value `v` out of the current sample buffer with size `n` where at most `q * n` values out of the sample buffer are less than or equal to `v`.

Typical quantiles for observation are 0.5 (the median) and 0.95. Quantiles are very good for monitoring Service Level Agreements.

The ZMX API also allows summaries to be configured with an error margin `e`. The error margin is applied to the count of values, so that a quantile `q` for a set of size `s` resolves to value `v` if the number `n` of values less than or equal to `v` is `(1 - e)q * s <= n <= (1+e)q`.

### API

A metric aspect that adds a value to a summary each time the effect it is applied to succeeds. This aspect can be applied to effects producing a `Double` value.

```scala
def observeSummary(
  name: String,
  maxAge: Duration,
  maxSize: Int,
  error: Double,
  quantiles: Chunk[Double],
  tags: Label*
): MetricAspect[Double]
```

A metric aspect that adds a value to a summary each time the effect it is applied to succeeds, using the specified function to transform the value returned by the effect to the value to add to the summary.

```scala
def observeSummaryWith[A](
  name: String,
  maxAge: Duration,
  maxSize: Int,
  error: Double,
  quantiles: Chunk[Double],
  tags: Label*
)(f: A => Double): MetricAspect[A]
```

### Examples

Create a summary that can hold 100 samples. The maximum age of the samples is 1 day, and the error margin is 3%. The summary should report the 10%, 50%, and 90% quantiles. It can be applied to effects yielding an `Int`.

```scala
val aspSummary =
  MetricAspect.observeSummaryWith[Int]("mySummary", 1.day, 100, 0.03d, Chunk(0.1, 