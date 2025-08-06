# Java Multi-threaded Job Scheduler

A fully-featured **Job Scheduler** built in Java using core concurrency primitives. It supports:

- Priority-based job execution
- Delayed job scheduling
- Maximum concurrency limits using semaphores
- Dynamic thread pool
- Real-time job monitoring
- Graceful shutdown



## Features

 **Priority Queue**: Higher-priority jobs are executed first.
 **Delay Scheduling**: Jobs can be delayed using `ScheduledExecutorService`.
 **Concurrency Control**: Limits the number of simultaneously executing jobs using `Semaphore`.
 **Thread Pool Execution**: Uses a configurable `ThreadPoolExecutor` for job execution.
 **Live Metrics**: Tracks active, queued, submitted, and completed jobs.
 **Graceful Shutdown**: Waits for ongoing tasks to complete before shutting down.


