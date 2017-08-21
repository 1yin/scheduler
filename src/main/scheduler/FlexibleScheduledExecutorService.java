package scheduler;

import java.time.Duration;
import java.util.concurrent.Callable;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.function.Function;

public interface FlexibleScheduledExecutorService extends ScheduledExecutorService {
    <T> ScheduledFuture<T> schedule(
            Callable<T> command,
            Duration initialDelay,
            Function<Future<T>, Duration> schedulingFunc);
}
