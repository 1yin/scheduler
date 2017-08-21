package main.scheduler;

import java.time.Duration;
import java.util.concurrent.*;
import java.util.function.Function;

import static java.util.Objects.requireNonNull;

public class FlexibleScheduledThreadPoolExecutor
        extends ScheduledThreadPoolExecutor
        implements FlexibleScheduledExecutorService {

    public FlexibleScheduledThreadPoolExecutor(int corePoolSize) {
        super(corePoolSize);
    }

    public FlexibleScheduledThreadPoolExecutor(int corePoolSize, ThreadFactory threadFactory) {
        super(corePoolSize, threadFactory);
    }

    public FlexibleScheduledThreadPoolExecutor(int corePoolSize, RejectedExecutionHandler handler) {
        super(corePoolSize, handler);
    }

    public FlexibleScheduledThreadPoolExecutor(
            int corePoolSize,
            ThreadFactory threadFactory,
            RejectedExecutionHandler handler) {
        super(corePoolSize, threadFactory, handler);
    }

    @Override
    public <T> ScheduledFuture<T> schedule(
            Callable<T> command,
            Duration initialDelay,
            Function<Future<T>, Duration> schedulingFunc) {

        requireNonNull(command, "command is null");
        if (initialDelay.isNegative()) {
            throw new IllegalArgumentException("initialDelay is negative");
        }




    }

    private static class ScheduledFutureTask<V>
            extends FutureTask<V> implements RunnableScheduledFuture<V> {

    }
}
