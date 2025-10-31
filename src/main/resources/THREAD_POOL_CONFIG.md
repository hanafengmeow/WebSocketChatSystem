# Thread Pool Configuration Guide

This document provides detailed analysis and guidelines for configuring async thread pools in the WebSocket Chat System for both Server and Consumer components.

## Table of Contents
1. [Overview](#overview)
2. [Configuration Parameters](#configuration-parameters)
3. [Calculation Methodology](#calculation-methodology)
4. [Server Thread Pool Configuration](#server-thread-pool-configuration)
5. [Consumer Thread Pool Configuration](#consumer-thread-pool-configuration)
6. [Instance Type Recommendations](#instance-type-recommendations)
7. [Monitoring and Tuning](#monitoring-and-tuning)
8. [Troubleshooting](#troubleshooting)

---

## Overview

The WebSocket Chat System uses Spring's `@Async` for non-blocking operations. Proper thread pool configuration is critical for:
- **Performance**: Maximizing throughput without overwhelming resources
- **Stability**: Preventing OutOfMemoryErrors and task rejections
- **Scalability**: Handling traffic spikes gracefully

### Key Async Operations

**Server:**
- `ClientSessionManager.broadcastToRoom()` - Broadcasting messages to WebSocket clients
- `StompSubscriptionService.subscribe()` - Subscribing to consumer STOMP topics
- `CloudWatchMetricsService.recordMetric()` - Publishing CloudWatch metrics
- `SqsMessageService.sendChatMessageToQueue()` - Sending messages to SQS
- `SqsQueueLifecycleService.deleteChatRoomQueue()` - Deleting SQS queues

**Consumer:**
- `ConsumerRegistryService.registerRoom()` - Registering rooms in DynamoDB (parallelized)
- `ConsumerRegistryService.heartbeat()` - Periodic heartbeat updates
- `CloudWatchMetricsService.recordMetric()` - Publishing CloudWatch metrics
- `SqsMessageService.sendChatMessageToDlq()` - Sending failed messages to DLQ

---

## Configuration Parameters

### Core Pool Size
```properties
websocketchat.async.core-pool-size=10
```
- **Definition**: Minimum number of threads kept alive
- **Behavior**: Created on-demand until core size is reached
- **Impact**: Higher values = faster startup but more idle resource usage

### Max Pool Size
```properties
websocketchat.async.max-pool-size=40
```
- **Definition**: Maximum number of threads allowed
- **Behavior**: Additional threads created only when queue is full
- **Impact**: Higher values = more burst capacity but more memory usage

### Queue Capacity
```properties
websocketchat.async.queue-capacity=1000
```
- **Definition**: Number of tasks buffered before creating new threads
- **Behavior**: Tasks wait in queue if all core threads are busy
- **Impact**: Higher values = more buffering but higher latency during spikes

### Thread Lifecycle
```java
executor.setAllowCoreThreadTimeOut(true);
executor.setKeepAliveSeconds(60);
```
- **Core thread timeout**: Enabled - core threads can terminate after 60s idle
- **Keep-alive**: 60 seconds
- **Impact**: Reduces resource usage during low traffic

### Rejection Policy
```java
executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
```
- **Policy**: CallerRunsPolicy
- **Behavior**: Calling thread executes task when pool is exhausted
- **Impact**: Provides backpressure instead of dropping tasks
- **Alternative**: AbortPolicy (throws exception), DiscardPolicy (drops silently)

---

## Calculation Methodology

### Formula for I/O-Bound Tasks

Our workload is **I/O-bound** (WebSocket sends, network calls, DynamoDB/SQS operations):

```
Optimal Threads = CPU_cores × (1 + Wait_time / Compute_time)
```

**Example for WebSocket broadcast:**
- Compute time: ~5-10ms (JSON serialization, loop overhead)
- Wait time: ~50-100ms (WebSocket I/O per client)
- Ratio: 50ms / 5ms = 10

For **2 vCPU instance**:
```
Optimal = 2 × (1 + 10) = 22 threads
```

### Conservative Multipliers

We use conservative multipliers to account for variability:

```properties
core-pool-size = CPU_cores × 5
max-pool-size = CPU_cores × 20
queue-capacity = max-pool-size × 25
```

### Memory Constraint

**Thread stack memory:**
```
Max threads × Stack size per thread < 10% of available RAM
```

**Example for t3.micro (1GB RAM):**
```
40 threads × 1MB = 40MB (4% of RAM) ✓ Safe
200 threads × 1MB = 200MB (20% of RAM) ✗ Too high
```

**Total memory budget:**
- JVM heap: ~50-60% of RAM
- Thread stacks: ~5-10% of RAM
- OS + overhead: ~30-40% of RAM

---

## Server Thread Pool Configuration

### Current Configuration (t3.micro)

```properties
websocketchat.async.core-pool-size=10
websocketchat.async.max-pool-size=40
websocketchat.async.queue-capacity=1000
websocketchat.async.thread-name-prefix=server-async-
```

### Workload Analysis

**Primary async operations:**
1. **Broadcasting to WebSocket clients** (highest volume)
   - Frequency: Every message received
   - Duration: 50-200ms (depends on client count)
   - Concurrency: Multiple rooms broadcasting simultaneously
   - Bottleneck: WebSocket I/O

2. **STOMP subscriptions** (moderate volume)
   - Frequency: When first client joins a room
   - Duration: 100-500ms (DynamoDB query + STOMP connection)
   - Concurrency: Bursts during peak connection times
   - Bottleneck: Network I/O

3. **CloudWatch metrics** (low volume)
   - Frequency: Per message + periodic
   - Duration: 50-100ms
   - Concurrency: Low
   - Bottleneck: AWS API calls

4. **SQS operations** (high volume)
   - Frequency: Every message received
   - Duration: 20-50ms
   - Concurrency: One per message
   - Bottleneck: SQS API calls

### Sizing Rationale

**Core pool = 10:**
- Handles steady-state load efficiently
- Low idle resource consumption
- Sufficient for ~5-10 concurrent broadcasts

**Max pool = 40:**
- Burst capacity for traffic spikes
- Supports ~20 concurrent rooms actively broadcasting
- Memory-safe for t3.micro (1GB RAM)

**Queue = 1000:**
- Buffers ~25 seconds of traffic at peak (40 tasks/sec)
- Prevents premature thread creation during brief spikes
- Allows CallerRunsPolicy to provide backpressure

### Expected Behavior

| Load Level | Active Threads | Queue Depth | Behavior |
|-----------|---------------|-------------|----------|
| Idle | 0-5 | 0 | Core threads timeout and terminate |
| Normal | 5-10 | 0-100 | Core threads handle all work |
| Peak | 10-30 | 100-500 | Additional threads created |
| Spike | 30-40 | 500-1000 | Max threads, queue filling |
| Overload | 40 | 1000 | CallerRunsPolicy kicks in |

---

## Consumer Thread Pool Configuration

### Current Configuration (t3.micro)

```properties
websocketchat.async.core-pool-size=10
websocketchat.async.max-pool-size=40
websocketchat.async.queue-capacity=1000
websocketchat.async.thread-name-prefix=consumer-async-
```

### Workload Analysis

**Primary async operations:**
1. **Room registration (parallelized)** (periodic, high volume)
   - Frequency: Every 30 seconds (heartbeat)
   - Count: Up to `max-rooms` DynamoDB writes in parallel
   - Duration: 50-100ms per room
   - Concurrency: All rooms registered simultaneously
   - Bottleneck: DynamoDB API

2. **CloudWatch metrics** (low volume)
   - Frequency: Per message processed
   - Duration: 50-100ms
   - Concurrency: Low
   - Bottleneck: AWS API calls

3. **DLQ operations** (rare, low volume)
   - Frequency: Only on message processing failures
   - Duration: 20-50ms
   - Concurrency: Very low
   - Bottleneck: SQS API calls

### Sizing Rationale

**Core pool = 10:**
- Sufficient for heartbeat registration of 10 rooms in parallel
- Handles CloudWatch metrics without queueing
- Low overhead during idle periods

**Max pool = 40:**
- Supports parallel registration of up to 40 rooms
- Burst capacity for high room counts
- Memory-safe for t3.micro

**Queue = 1000:**
- Large buffer for scheduled heartbeat bursts
- Accommodates `max-rooms > 40` by queueing excess registrations
- Prevents task rejection during startup initialization

### Expected Behavior

| Scenario | Active Threads | Queue Depth | Behavior |
|----------|---------------|-------------|----------|
| Idle | 0-2 | 0 | Minimal activity between heartbeats |
| Heartbeat (10 rooms) | 10 | 0 | All rooms registered in parallel |
| Heartbeat (40 rooms) | 40 | 0 | Max threads, no queueing |
| Heartbeat (100 rooms) | 40 | 60 | 40 parallel, 60 queued |
| Message processing | 5-15 | 0-50 | DLQ + metrics operations |

---

## Instance Type Recommendations

### AWS EC2 Instance Types

| Instance Type | vCPUs | RAM | Core Pool | Max Pool | Queue | Max Concurrent Rooms |
|--------------|-------|-----|-----------|----------|-------|---------------------|
| **t3.micro** | 2 | 1 GB | 10 | 40 | 1,000 | 20-30 |
| **t3.small** | 2 | 2 GB | 10 | 50 | 1,250 | 30-40 |
| **t3.medium** | 2 | 4 GB | 20 | 80 | 2,000 | 50-70 |
| **t3.large** | 2 | 8 GB | 30 | 120 | 3,000 | 80-100 |
| **m5.large** | 2 | 8 GB | 30 | 120 | 3,000 | 80-100 |
| **m5.xlarge** | 4 | 16 GB | 50 | 200 | 5,000 | 150-200 |
| **m5.2xlarge** | 8 | 32 GB | 100 | 400 | 10,000 | 300-400 |
| **c5.large** | 2 | 4 GB | 20 | 80 | 2,000 | 50-70 |
| **c5.xlarge** | 4 | 8 GB | 40 | 160 | 4,000 | 100-150 |

### Recommendations by Scale

**Development/Testing (Low traffic):**
- Instance: t3.micro or t3.small
- Config: Default values (10/40/1000)
- Cost: $7-15/month

**Production (Moderate traffic, <1000 concurrent users):**
- Instance: t3.medium or m5.large
- Config: 20/80/2000
- Cost: $30-70/month

**Production (High traffic, 1000-5000 concurrent users):**
- Instance: m5.xlarge or c5.xlarge
- Config: 50/200/5000
- Cost: $140-170/month

**Production (Very high traffic, >5000 concurrent users):**
- Instance: m5.2xlarge or larger
- Config: 100/400/10000
- Cost: $280+/month
- Consider: Horizontal scaling with multiple instances

---

## Monitoring and Tuning

### Automated CloudWatch Metrics

The system automatically publishes thread pool metrics to CloudWatch via `ThreadPoolMetricsService`.

**Configuration:**
```properties
# application.properties
websocketchat.metrics.thread-pool.interval-ms=60000
```

**Published Metrics:**
- `ThreadPool.ActiveCount` - Currently executing tasks
- `ThreadPool.PoolSize` - Current number of threads in pool
- `ThreadPool.QueueSize` - Tasks waiting to execute
- `ThreadPool.CompletedTaskCount` - Total tasks completed since startup
- `ThreadPool.CorePoolSize` - Configured minimum threads
- `ThreadPool.MaxPoolSize` - Configured maximum threads
- `ThreadPool.Utilization` - Active threads / pool size (%)
- `ThreadPool.QueueUtilization` - Queue size / capacity (%)
- `ThreadPool.LargestPoolSize` - Peak thread count reached

**Metric Intervals:**
- Default: 60 seconds (60000ms)
- High-frequency monitoring: 30 seconds (30000ms)
- Cost-optimized: 5 minutes (300000ms)

**CloudWatch Namespaces:**
- Server metrics: `WebSocketChatServer`
- Consumer metrics: `WebSocketChatConsumer`

### Key Metrics to Monitor

#### 1. Thread Pool Utilization
**Via CloudWatch**: Monitor `ThreadPool.Utilization` and `ThreadPool.QueueUtilization` metrics

**Via Code** (for debugging):
```java
ThreadPoolTaskExecutor executor = (ThreadPoolTaskExecutor) asyncExecutor;
int activeCount = executor.getActiveCount();
int poolSize = executor.getPoolSize();
int queueSize = executor.getThreadPoolExecutor().getQueue().size();
```

**Healthy ranges:**
- Active threads: 30-70% of pool size during peak
- Pool size: Core to Max during normal operation
- Queue depth: <50% capacity

**Warning signs:**
- Active threads consistently at max pool size
- Queue consistently >80% full
- Frequent CallerRunsPolicy activation

#### 2. Task Rejection Rate
Monitor log files for `TaskRejectedException`:
```bash
grep "TaskRejectedException" /var/log/application.log | wc -l
```

**Healthy**: 0 rejections (CallerRunsPolicy prevents this)
**Warning**: If CallerRunsPolicy frequently used, increase pool size

#### 3. Memory Usage
```bash
# JVM heap usage
jstat -gc <pid>

# Total process memory
ps aux | grep java
```

**Healthy**: <70% heap usage, <80% total RAM
**Warning**: Consistent >80% heap or frequent GC

#### 4. Response Time Latency
**Metrics:**
- WebSocket message latency (send to receive)
- Broadcast latency (message to all clients in room)

**Healthy**: p99 latency <200ms
**Warning**: p99 latency >500ms indicates thread starvation

### CloudWatch Alarms

Configure CloudWatch alarms to alert on thread pool issues:

**Recommended Alarms:**

1. **High Thread Pool Utilization**
   ```
   Metric: ThreadPool.Utilization
   Threshold: > 80%
   Period: 5 minutes
   Evaluation: 2 consecutive periods
   Action: Send SNS notification
   ```

2. **High Queue Utilization**
   ```
   Metric: ThreadPool.QueueUtilization
   Threshold: > 70%
   Period: 3 minutes
   Evaluation: 2 consecutive periods
   Action: Send SNS notification
   ```

3. **Pool Size at Maximum**
   ```
   Metric: ThreadPool.PoolSize == ThreadPool.MaxPoolSize
   Threshold: True
   Period: 10 minutes
   Evaluation: 3 consecutive periods
   Action: Send SNS notification, consider scaling
   ```

4. **Growing Queue Depth**
   ```
   Metric: ThreadPool.QueueSize
   Threshold: > 500 (for t3.micro)
   Period: 5 minutes
   Evaluation: 2 consecutive periods
   Action: Send SNS notification
   ```

**CloudWatch Dashboard Example:**
```json
{
  "widgets": [
    {
      "type": "metric",
      "properties": {
        "metrics": [
          ["WebSocketChatServer", "ThreadPool.Utilization"],
          [".", "ThreadPool.QueueUtilization"]
        ],
        "period": 60,
        "stat": "Average",
        "region": "us-east-1",
        "title": "Thread Pool Health"
      }
    }
  ]
}
```

### Tuning Guidelines

#### Scenario 1: High Queue Depth, Low Thread Count
**Symptom**: Queue consistently >50%, active threads <max pool
**Cause**: Queue capacity too high - threads not being created
**Solution**: Reduce `queue-capacity` by 25-50%

#### Scenario 2: Frequent CallerRunsPolicy Activation
**Symptom**: Logs show calling thread executing tasks
**Cause**: Pool exhausted, queue full
**Solution**:
1. Increase `max-pool-size` by 25%
2. Verify memory headroom available
3. Consider horizontal scaling

#### Scenario 3: High Memory Usage
**Symptom**: JVM heap >80%, frequent GC
**Cause**: Too many threads, insufficient heap
**Solution**:
1. Reduce `max-pool-size` by 25%
2. Increase JVM heap: `-Xmx768m` → `-Xmx896m`
3. Upgrade instance type

#### Scenario 4: Slow Response Times
**Symptom**: p99 latency >500ms
**Cause**: Thread starvation or blocking operations
**Solution**:
1. Profile to identify blocking operations
2. Increase `core-pool-size` if CPU underutilized
3. Check for synchronization bottlenecks

---

## Troubleshooting

### Common Issues

#### Issue: TaskRejectedException
```
org.springframework.core.task.TaskRejectedException:
ExecutorService in active state did not accept task
```

**Cause**: Task submitted when pool and queue are both full

**Solutions:**
1. **Immediate**: CallerRunsPolicy should prevent this (verify configured)
2. **Short-term**: Increase `max-pool-size` or `queue-capacity`
3. **Long-term**: Horizontal scaling or optimize async operations

**Verification:**
```java
// Check rejection policy
RejectedExecutionHandler handler =
    executor.getThreadPoolExecutor().getRejectedExecutionHandler();
// Should be: ThreadPoolExecutor.CallerRunsPolicy
```

#### Issue: OutOfMemoryError
```
java.lang.OutOfMemoryError: unable to create new native thread
```

**Cause**: Too many threads for available memory

**Solutions:**
1. Reduce `max-pool-size`:
   - t3.micro: Max 40 threads
   - t3.small: Max 50 threads
   - t3.medium: Max 80 threads

2. Increase JVM heap (reduces thread stack space):
   ```bash
   # Reduce heap to increase thread stack space
   -Xmx512m instead of -Xmx768m
   ```

3. Reduce thread stack size:
   ```bash
   -Xss512k  # Default is 1MB
   ```

**Prevention:**
```
Max safe threads = (Available RAM - JVM Heap - OS overhead) / Thread stack size
For t3.micro: (1024MB - 512MB - 300MB) / 1MB = ~200 threads theoretical
For safety: Use 20-25% of theoretical = 40-50 threads
```

#### Issue: Slow Task Execution
```
Tasks taking much longer than expected to complete
```

**Diagnosis:**
1. Check active thread count:
   ```java
   executor.getActiveCount() == executor.getMaxPoolSize()
   ```
   If true: Thread pool saturated

2. Check queue depth:
   ```java
   executor.getThreadPoolExecutor().getQueue().size()
   ```
   If high: Tasks waiting for threads

**Solutions:**
1. Increase `core-pool-size` to reduce queue time
2. Profile async methods for blocking operations
3. Add more aggressive timeouts on network calls

#### Issue: Thread Leaks
```
Thread pool size grows but tasks not completing
```

**Diagnosis:**
```bash
# Check thread count over time
jstack <pid> | grep "server-async-" | wc -l
```

**Common causes:**
1. Async methods with infinite loops
2. Deadlocks between async tasks
3. Resource leaks (unclosed connections)

**Solutions:**
1. Add timeouts to all network operations:
   ```java
   @Async
   public void asyncMethod() {
       // Add timeout
       CompletableFuture.runAsync(() -> {
           // work
       }).orTimeout(30, TimeUnit.SECONDS);
   }
   ```

2. Enable thread dump on OutOfMemoryError:
   ```bash
   -XX:+HeapDumpOnOutOfMemoryError
   -XX:HeapDumpPath=/tmp/heapdump.hprof
   ```

---

## Performance Testing

### Load Testing Scenarios

#### Test 1: Steady State Load
**Goal**: Verify core pool handles normal traffic

**Setup:**
- 10 concurrent rooms
- 5 clients per room
- 1 message/second per room

**Expected:**
- Active threads: 5-10
- Queue depth: 0-10
- No CallerRunsPolicy activation

#### Test 2: Burst Load
**Goal**: Verify max pool handles spikes

**Setup:**
- 40 concurrent rooms
- 10 clients per room
- 10 messages/second per room for 10 seconds

**Expected:**
- Active threads: Ramp to 30-40
- Queue depth: Peak at 200-500
- CallerRunsPolicy may activate briefly
- Recovery to steady state within 30 seconds

#### Test 3: Sustained Peak Load
**Goal**: Verify system stability under prolonged stress

**Setup:**
- 30 concurrent rooms
- 10 clients per room
- 5 messages/second per room for 5 minutes

**Expected:**
- Active threads: Sustained at 25-35
- Queue depth: Stable at 100-300
- Memory usage: <70% heap
- No rejections or errors

### Load Testing Tools

**Recommended:** Apache JMeter, Gatling, or custom client

**Sample client command:**
```bash
java -jar ChatWebSocketClient.jar \
  --rooms 10 \
  --clients-per-room 5 \
  --messages-per-second 1 \
  --duration 300
```

---

## Configuration Examples

### Development Environment
```properties
# Local development on laptop/desktop
websocketchat.async.core-pool-size=5
websocketchat.async.max-pool-size=20
websocketchat.async.queue-capacity=500
```

### Staging Environment (t3.small)
```properties
# Realistic testing with 2GB RAM
websocketchat.async.core-pool-size=10
websocketchat.async.max-pool-size=50
websocketchat.async.queue-capacity=1250
```

### Production - Small (t3.medium)
```properties
# Up to 50 concurrent rooms
websocketchat.async.core-pool-size=20
websocketchat.async.max-pool-size=80
websocketchat.async.queue-capacity=2000
```

### Production - Medium (m5.large)
```properties
# Up to 100 concurrent rooms
websocketchat.async.core-pool-size=30
websocketchat.async.max-pool-size=120
websocketchat.async.queue-capacity=3000
```

### Production - Large (m5.xlarge)
```properties
# Up to 200 concurrent rooms
websocketchat.async.core-pool-size=50
websocketchat.async.max-pool-size=200
websocketchat.async.queue-capacity=5000
```

---

## References

- [Spring Boot Async Configuration](https://docs.spring.io/spring-framework/docs/current/reference/html/integration.html#scheduling-annotation-support-async)
- [Java ThreadPoolExecutor](https://docs.oracle.com/javase/8/docs/api/java/util/concurrent/ThreadPoolExecutor.html)
- [AWS EC2 Instance Types](https://aws.amazon.com/ec2/instance-types/)
- [Thread Pool Sizing Guidelines](https://github.com/brettwooldridge/HikariCP/wiki/About-Pool-Sizing)

---

**Document Version**: 1.0
**Last Updated**: 2025-01-31
**Maintained By**: WebSocket Chat System Team
