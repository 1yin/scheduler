package scheduler;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import scheduler.FlexibleScheduledThreadPoolExecutor.ReSchedulingTask;

import java.time.Duration;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyLong;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class ReschedulingTaskTest {
    private final FlexibleScheduledThreadPoolExecutor executor =
            spy(new FlexibleScheduledThreadPoolExecutor(1));
    private final Function<Future<Integer>, Duration> fx = mock(Function.class);


    @BeforeEach
    public void setUp() {
        reset(executor);
        reset(fx);

        when(fx.apply(any(Future.class)))
                .thenReturn(Duration.ofMillis(1))
                .thenReturn(null);
    }

    @Test
    public void testWrappedTask() throws Exception {
        Integer result = 1;
        Consumer<ScheduledFuture<Integer>> callback = mock(Consumer.class);
        doAnswer(
                invocationOnMock -> {
                    ScheduledFuture<Integer> nextFuture = (ScheduledFuture<Integer>) invocationOnMock.getArguments()[0];
                    try {
                        assertEquals(result, nextFuture.get());
                    } catch (Exception e) {
                        fail(e);
                    }
                    return null;
                })
                .when(callback).accept(any(ScheduledFuture.class));

        ReSchedulingTask<Integer> wrappedTask = executor.new ReSchedulingTask<>(
                () -> result,
                fx,
                callback);

        assertEquals(result, wrappedTask.call());
        verify(executor, times(1)).schedule(any(Callable.class), anyLong(), any(TimeUnit.class));
        verify(callback, times(1)).accept(any(ScheduledFuture.class));
    }

    @Test
    public void testInterruptedExceptionInWrappedTask() throws Exception {
        FlexibleScheduledThreadPoolExecutor executor = spy(new FlexibleScheduledThreadPoolExecutor(1));
        Consumer<ScheduledFuture<Integer>> callback = mock(Consumer.class);
        ReSchedulingTask<Integer> wrappedTask = executor.new ReSchedulingTask<>(
                () -> {
                    throw new InterruptedException();
                },
                fx,
                callback);

        try {
            wrappedTask.call();
            fail("expected exception not thrown");
        } catch (InterruptedException e) {
            verify(executor, never()).schedule(any(Callable.class), anyLong(), any(TimeUnit.class));
            verify(callback, never()).accept(any(ScheduledFuture.class));
        }
    }

    @Test
    public void testOtherExceptionInWrappedTask() {
        FlexibleScheduledThreadPoolExecutor executor = spy(new FlexibleScheduledThreadPoolExecutor(1));
        Consumer<ScheduledFuture<Integer>> callback = mock(Consumer.class);
        doAnswer(
                invocationOnMock -> {
                    ScheduledFuture<Integer> nextFuture = (ScheduledFuture<Integer>) invocationOnMock.getArguments()[0];
                    try {
                        assertThrows(ExecutionException.class, () -> nextFuture.get());
                    } catch (Exception e) {
                        fail(e);
                    }
                    return null;
                })
                .when(callback).accept(any(ScheduledFuture.class));

        ReSchedulingTask<Integer> wrappedTask = executor.new ReSchedulingTask<>(
                () -> {
                    throw new RuntimeException();
                },
                fx,
                callback);

        assertThrows(RuntimeException.class, () -> wrappedTask.call());
        verify(executor, times(1)).schedule(any(Callable.class), anyLong(), any(TimeUnit.class));
        verify(callback, times(1)).accept(any(ScheduledFuture.class));
    }
}
