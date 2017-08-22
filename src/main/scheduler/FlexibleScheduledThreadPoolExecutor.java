package scheduler;

import java.time.Duration;
import java.util.concurrent.*;
import java.util.function.Consumer;
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

    static <T> Function<Future<T>, Duration> getSchedulingFunc(Function<Future<T>, Duration> schedulingFunc) {
        Function<Future<T>, Duration> schedulingFx;

        if (schedulingFunc == null) {
            schedulingFx = (future) -> null;
        } else {
            schedulingFx = (future) -> {
                try {
                    return schedulingFunc.apply(future);
                } catch (RuntimeException e) {
                    return null;
                }
            };
        }

        return schedulingFx;
    }

    @Override
    public <T> ScheduledFuture<T> schedule(
            Callable<T> command,
            Duration initialDelay,
            Function<Future<T>, Duration> schedulingFunc) {

        if (initialDelay == null) {
            initialDelay = Duration.ZERO;
        } else if (initialDelay.isNegative()) {
            throw new IllegalArgumentException("initial delay must be non negative");
        }

        Function<Future<T>, Duration> schedulingFx = getSchedulingFunc(schedulingFunc);

        ScheduledFutureTask<T> future = new ScheduledFutureTask<>();
        command = new ReSchedulingTask<>(command, schedulingFx, future);
        future.accept(super.schedule(command, initialDelay.getNano(), TimeUnit.NANOSECONDS));

        return future;
    }

    static class ScheduledFutureTask<V> implements ScheduledFuture<V>, Consumer<ScheduledFuture<V>> {
        private volatile ScheduledFuture<V> future;

        @Override
        public long getDelay(TimeUnit unit) {
            return future.getDelay(unit);
        }

        @Override
        public int compareTo(Delayed o) {
            return future.compareTo(o);
        }

        @Override
        public boolean cancel(boolean mayInterruptIfRunning) {
            return future.cancel(mayInterruptIfRunning);
        }

        @Override
        public boolean isCancelled() {
            return future.isCancelled();
        }

        @Override
        public boolean isDone() {
            return future.isDone();
        }

        @Override
        public V get() throws InterruptedException, ExecutionException {
            return future.get();
        }

        @Override
        public V get(long timeout, TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException {
            return future.get(timeout, unit);
        }

        @Override
        public void accept(ScheduledFuture<V> nextFuture) {
            future = requireNonNull(nextFuture, "next future is null");
        }
    }

    static class CompletedFuture<V> implements Future<V> {
        private final boolean succeeded;
        private final V result;
        private final ExecutionException exception;

        CompletedFuture(V result) {
            this.succeeded = true;
            this.result = result;
            this.exception = null;
        }

        CompletedFuture(Exception exception) {
            this.succeeded = false;
            this.result = null;
            this.exception = new ExecutionException(exception);
        }

        @Override
        public boolean cancel(boolean mayInterruptIfRunning) {
            return false;
        }

        @Override
        public boolean isCancelled() {
            return false;
        }

        @Override
        public boolean isDone() {
            return true;
        }

        @Override
        public V get() throws InterruptedException, ExecutionException {
            if (succeeded) {
                return result;
            }
            throw exception;
        }

        @Override
        public V get(long timeout, TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException {
            return get();
        }
    }

    class ReSchedulingTask<V> implements Callable<V> {
        private final Callable<V> command;
        private final Function<Future<V>, Duration> schedulingFx;
        private final Consumer<ScheduledFuture<V>> callback;

        ReSchedulingTask(
                Callable<V> command,
                Function<Future<V>, Duration> schedulingFx,
                Consumer<ScheduledFuture<V>> callback) {
            this.command = requireNonNull(command, "command is null");
            this.schedulingFx = requireNonNull(schedulingFx, "scheduling function is null");
            this.callback = callback;
        }

        @Override
        public V call() throws Exception {
            CompletedFuture<V> future;
            try {
                future = new CompletedFuture(command.call());
            } catch (InterruptedException e) {
                throw e;
            } catch (Exception e) {
                future = new CompletedFuture(e);
            }

            Duration delay = schedulingFx.apply(future);
            if (delay != null) {
                ScheduledFuture<V> nextFuture = FlexibleScheduledThreadPoolExecutor.this.schedule(
                        command,
                        delay.getNano(),
                        TimeUnit.NANOSECONDS);

                if (callback != null) {
                    callback.accept(nextFuture);
                }
            }

            try {
                return future.get();
            } catch (ExecutionException e) {
                throw (Exception) e.getCause();
            }
        }
    }
}
