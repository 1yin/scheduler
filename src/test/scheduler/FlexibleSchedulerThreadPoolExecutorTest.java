package scheduler;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyLong;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static scheduler.FlexibleScheduledThreadPoolExecutor.CompletedFuture;
import static scheduler.FlexibleScheduledThreadPoolExecutor.getSchedulingFunc;

public class FlexibleSchedulerThreadPoolExecutorTest {
    private final FlexibleScheduledThreadPoolExecutor executor = spy(new FlexibleScheduledThreadPoolExecutor(1));

    @BeforeEach
    public void setUp() {
        reset(executor);
    }

    @Test
    public void testOneShotOperation() throws Exception {
        Boolean result = Boolean.TRUE;
        ScheduledFuture<Boolean> future = executor.schedule(() -> result, null, null);
        assertEquals(result, future.get());
    }

    @Test
    public void testOperationRunSeveralTimes() throws Exception {
        List<Integer> result = new ArrayList<>();
        result.add(10);

        Callable<Integer> command = mock(Callable.class);
        when(command.call()).thenAnswer(invocationOnMock -> {
            int ret = result.get(result.size() - 1) - 1;
            result.add(ret);
            return ret;
        });

        CountDownLatch latch = new CountDownLatch(1);
        executor.schedule(
                command,
                null,
                fut -> {
                    try {
                        Duration ret = (fut.get() > 0 ? Duration.ofMillis(1) : null);
                        if (ret == null) {
                            latch.countDown();
                        }
                        return ret;
                    } catch (Exception e) {
                        return null;
                    }
                });

        latch.await();
        verify(command, times(result.get(0))).call();
        assertEquals(result.get(0) + 1, result.size());
    }

    @Test
    public void testCancellation() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        CountDownLatch started = new CountDownLatch(1);
        Callable<Boolean> command = mock(Callable.class);
        when(command.call()).thenAnswer(invocationOnMock -> {
            started.countDown();
            latch.await();
            return Boolean.TRUE;
        });

        ScheduledFuture<Boolean> future = executor.schedule(command, null, fut -> Duration.ZERO);
        started.await();
        future.cancel(true);
        latch.countDown();

        verify(executor, times(1)).schedule(any(Callable.class), anyLong(), any(TimeUnit.class));
        verify(command, times(1)).call();
    }

    @Test
    public void testSchedulingFuncShouldNotThrow() {
        Function<Future<Object>, Duration> func = getSchedulingFunc(future -> {
            throw new RuntimeException();
        });

        CompletedFuture<Object> future = new CompletedFuture<>(null);
        func.apply(future);
    }

    @Test
    public void testException() throws Exception {
        Callable<Boolean> command = mock(Callable.class);
        when(command.call()).thenThrow(new RuntimeException());

        CountDownLatch latch = new CountDownLatch(1);
        Function<Future<Boolean>, Duration> fx = mock(Function.class);
        when(fx.apply(any(Future.class)))
                .thenReturn(Duration.ofMillis(1))
                .thenReturn(Duration.ofMillis(1))
                .thenAnswer(invocationOnMock -> {
                    latch.countDown();
                    return null;
                });

        ScheduledFuture<Boolean> future = executor.schedule(command, null, fx);

        try {
            future.get();
            fail("expected exception not thrown");
        } catch (ExecutionException e) {
            assertTrue(RuntimeException.class.isInstance(e.getCause()));
        }

        latch.await();
        verify(command, atLeast(3)).call();
        verify(fx, times(3)).apply(any(Future.class));
        verify(executor, times(3)).schedule(any(Callable.class), anyLong(), any(TimeUnit.class));
    }
}

