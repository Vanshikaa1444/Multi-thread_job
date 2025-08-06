import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicInteger;

// Job interface - defines what can be executed
interface Job {
    String getId();
    void execute();
    int getPriority();
    long getDelayMs();
}

// Abstract base class for jobs
abstract class AbstractJob implements Job, Comparable<Job> {
    protected final String id;
    protected final int priority;
    protected final long delayMs;
    
    public AbstractJob(String id, int priority, long delayMs) {
        this.id = id;
        this.priority = priority;
        this.delayMs = delayMs;
    }
    
    @Override
    public String getId() { return id; }
    
    @Override
    public int getPriority() { return priority; }
    
    @Override
    public long getDelayMs() { return delayMs; }
    
    // Higher priority jobs execute first
    @Override
    public int compareTo(Job other) {
        int priorityComparison = Integer.compare(other.getPriority(), this.priority);
        if (priorityComparison != 0) return priorityComparison;
        return this.id.compareTo(other.getId()); // Tie-breaker by ID
    }
}

// Sample job implementation
class SampleJob extends AbstractJob {
    private final String taskName;
    private final int duration;
    
    public SampleJob(String id, String taskName, int priority, long delayMs, int duration) {
        super(id, priority, delayMs);
        this.taskName = taskName;
        this.duration = duration;
    }
    
    @Override
    public void execute() {
        try {
            System.out.printf("[%s] Starting job %s - %s (Priority: %d)%n", 
                Thread.currentThread().getName(), getId(), taskName, getPriority());
            Thread.sleep(duration); // Simulate work
            System.out.printf("[%s] Completed job %s - %s%n", 
                Thread.currentThread().getName(), getId(), taskName);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            System.out.printf("[%s] Job %s was interrupted%n", 
                Thread.currentThread().getName(), getId());
        }
    }
}

// Main Job Scheduler implementation
class JobScheduler {
    private final ThreadPoolExecutor executor;
    private final PriorityBlockingQueue<Job> jobQueue;
    private final Semaphore concurrentJobsSemaphore;
    private final ScheduledExecutorService delayedExecutor;
    private final AtomicLong completedJobs;
    private final AtomicLong submittedJobs;
    private final AtomicInteger activeJobs;
    private volatile boolean isShutdown = false;
    
    // Configuration constants
    private static final int DEFAULT_CORE_POOL_SIZE = 4;
    private static final int DEFAULT_MAX_POOL_SIZE = 8;
    private static final long DEFAULT_KEEP_ALIVE_TIME = 60L;
    private static final int DEFAULT_MAX_CONCURRENT_JOBS = 10;
    
    public JobScheduler() {
        this(DEFAULT_CORE_POOL_SIZE, DEFAULT_MAX_POOL_SIZE, DEFAULT_MAX_CONCURRENT_JOBS);
    }
    
    public JobScheduler(int corePoolSize, int maxPoolSize, int maxConcurrentJobs) {
        // Priority queue for job ordering
        this.jobQueue = new PriorityBlockingQueue<>();
        
        // Semaphore to limit concurrent job execution
        this.concurrentJobsSemaphore = new Semaphore(maxConcurrentJobs, true);
        
        // Custom thread pool with named threads
        this.executor = new ThreadPoolExecutor(
            corePoolSize,
            maxPoolSize,
            DEFAULT_KEEP_ALIVE_TIME,
            TimeUnit.SECONDS,
            new LinkedBlockingQueue<>(),
            new ThreadFactory() {
                private final AtomicInteger threadNumber = new AtomicInteger(1);
                @Override
                public Thread newThread(Runnable r) {
                    Thread t = new Thread(r, "JobScheduler-Worker-" + threadNumber.getAndIncrement());
                    t.setDaemon(false);
                    return t;
                }
            }
        );
        
        // Separate executor for handling delayed jobs
        this.delayedExecutor = Executors.newScheduledThreadPool(2, r -> {
            Thread t = new Thread(r, "JobScheduler-Delayed");
            t.setDaemon(true);
            return t;
        });
        
        // Metrics
        this.completedJobs = new AtomicLong(0);
        this.submittedJobs = new AtomicLong(0);
        this.activeJobs = new AtomicInteger(0);
        
        // Start the main job processing loop
        startJobProcessor();
        
        System.out.println("JobScheduler initialized with " + corePoolSize + " core threads, " 
            + maxPoolSize + " max threads, and " + maxConcurrentJobs + " max concurrent jobs");
    }
    
    // Submit a job for immediate execution
    public void submitJob(Job job) {
        if (isShutdown) {
            throw new IllegalStateException("JobScheduler is shutdown");
        }
        
        submittedJobs.incrementAndGet();
        
        if (job.getDelayMs() > 0) {
            // Schedule delayed job
            delayedExecutor.schedule(() -> {
                jobQueue.offer(job);
                System.out.println("Delayed job " + job.getId() + " added to queue");
            }, job.getDelayMs(), TimeUnit.MILLISECONDS);
            
            System.out.printf("Job %s scheduled with %dms delay%n", job.getId(), job.getDelayMs());
        } else {
            // Add to queue immediately
            jobQueue.offer(job);
            System.out.println("Job " + job.getId() + " added to queue");
        }
    }
    
    // Main job processing loop
    private void startJobProcessor() {
        Thread processorThread = new Thread(() -> {
            while (!isShutdown || !jobQueue.isEmpty()) {
                try {
                    Job job = jobQueue.take(); // Blocks until a job is available
                    
                    // Submit to thread pool with semaphore control
                    executor.submit(new JobWrapper(job));
                    
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }, "JobScheduler-Processor");
        
        processorThread.setDaemon(false);
        processorThread.start();
    }
    
    // Wrapper class to handle semaphore and metrics
    private class JobWrapper implements Runnable {
        private final Job job;
        
        public JobWrapper(Job job) {
            this.job = job;
        }
        
        @Override
        public void run() {
            try {
                // Acquire semaphore permit (blocks if max concurrent jobs reached)
                concurrentJobsSemaphore.acquire();
                activeJobs.incrementAndGet();
                
                try {
                    job.execute();
                } finally {
                    // Always release the semaphore permit
                    concurrentJobsSemaphore.release();
                    activeJobs.decrementAndGet();
                    completedJobs.incrementAndGet();
                }
                
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                System.out.printf("Job %s was interrupted while waiting for semaphore%n", job.getId());
            } catch (Exception e) {
                System.err.printf("Error executing job %s: %s%n", job.getId(), e.getMessage());
                e.printStackTrace();
            }
        }
    }
    
    // Get scheduler statistics
    public SchedulerStats getStats() {
        return new SchedulerStats(
            submittedJobs.get(),
            completedJobs.get(),
            activeJobs.get(),
            jobQueue.size(),
            executor.getActiveCount(),
            executor.getPoolSize(),
            concurrentJobsSemaphore.availablePermits()
        );
    }
    
    // Graceful shutdown
    public void shutdown() {
        System.out.println("Initiating JobScheduler shutdown...");
        isShutdown = true;
        
        delayedExecutor.shutdown();
        executor.shutdown();
        
        try {
            if (!executor.awaitTermination(30, TimeUnit.SECONDS)) {
                System.out.println("Forcing shutdown...");
                executor.shutdownNow();
            }
            if (!delayedExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                delayedExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            executor.shutdownNow();
            delayedExecutor.shutdownNow();
        }
        
        System.out.println("JobScheduler shutdown complete");
    }
    
    // Statistics class
    public static class SchedulerStats {
        public final long submittedJobs;
        public final long completedJobs;
        public final int activeJobs;
        public final int queuedJobs;
        public final int activeThreads;
        public final int poolSize;
        public final int availablePermits;
        
        public SchedulerStats(long submitted, long completed, int active, int queued, 
                            int activeThreads, int poolSize, int availablePermits) {
            this.submittedJobs = submitted;
            this.completedJobs = completed;
            this.activeJobs = active;
            this.queuedJobs = queued;
            this.activeThreads = activeThreads;
            this.poolSize = poolSize;
            this.availablePermits = availablePermits;
        }
        
        @Override
        public String toString() {
            return String.format("Stats{submitted=%d, completed=%d, active=%d, queued=%d, " +
                               "activeThreads=%d, poolSize=%d, availablePermits=%d}", 
                               submittedJobs, completedJobs, activeJobs, queuedJobs,
                               activeThreads, poolSize, availablePermits);
        }
    }
}

// Demo class to test the scheduler
public class Main {
    public static void main(String[] args) {
        JobScheduler scheduler = new JobScheduler(3, 6, 5);
        
        System.out.println("\n=== Submitting various jobs ===");
        
        // Submit jobs with different priorities and delays
        scheduler.submitJob(new SampleJob("job-1", "Data Processing", 5, 0, 2000));
        scheduler.submitJob(new SampleJob("job-2", "Email Notification", 8, 1000, 1000));
        scheduler.submitJob(new SampleJob("job-3", "Database Backup", 3, 0, 3000));
        scheduler.submitJob(new SampleJob("job-4", "Report Generation", 7, 500, 1500));
        scheduler.submitJob(new SampleJob("job-5", "Log Cleanup", 2, 0, 1000));
        scheduler.submitJob(new SampleJob("job-6", "Cache Refresh", 9, 2000, 800));
        scheduler.submitJob(new SampleJob("job-7", "Security Scan", 6, 0, 2500));
        scheduler.submitJob(new SampleJob("job-8", "File Compression", 4, 1500, 1200));
        
        // Monitor progress
        monitorScheduler(scheduler);
        
        // Let jobs run for a while
        try {
            Thread.sleep(8000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        System.out.println("\n=== Final Statistics ===");
        System.out.println(scheduler.getStats());
        
        scheduler.shutdown();
    }
    
    private static void monitorScheduler(JobScheduler scheduler) {
        Thread monitor = new Thread(() -> {
            for (int i = 0; i < 15; i++) {
                try {
                    Thread.sleep(500);
                    System.out.println("Monitor: " + scheduler.getStats());
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }, "Monitor");
        
        monitor.setDaemon(true);
        monitor.start();
    }
}