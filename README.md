Multi-threaded Job Scheduler
A production-ready Java implementation of a thread-safe job scheduler with priority-based execution, semaphore-controlled concurrency, and delayed job scheduling capabilities.
Features

Thread Pool Management: Configurable core and maximum thread pools with named worker threads
Priority-based Execution: Jobs execute in priority order (higher numbers = higher priority)
Semaphore Control: Limits concurrent job execution to prevent system overload
Delayed Job Scheduling: Schedule jobs to execute after a specified delay
Real-time Monitoring: Comprehensive statistics and metrics
Graceful Shutdown: Proper resource cleanup and job completion handling
Thread Safety: All operations are thread-safe using concurrent data structures

Architecture Overview
┌─────────────────┐    ┌──────────────────┐    ┌─────────────────┐
│   Job Submit    │───▶│  Priority Queue  │───▶│  Job Processor  │
│                 │    │                  │    │     Thread      │
└─────────────────┘    └──────────────────┘    └─────────────────┘
                                                        │
                                                        ▼
┌─────────────────┐    ┌──────────────────┐    ┌─────────────────┐
│ Delayed Jobs    │    │    Semaphore     │    │  Thread Pool    │
│   Scheduler     │    │   (Concurrency   │    │   Executor      │
│                 │    │    Control)      │    │                 │
└─────────────────┘    └──────────────────┘    └─────────────────┘
