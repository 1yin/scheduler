package scheduler;

import org.junit.jupiter.api.Test;
import scheduler.FlexibleScheduledThreadPoolExecutor.ReSchedulingTask;

import java.time.Duration;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyLong;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

public class ReschedulingTaskTest {

    @Test
    public void testWrappedTask() throws Exception {
        Integer result = 1;
        FlexibleScheduledThreadPoolExecutor executor = spy(new FlexibleScheduledThreadPoolExecutor(1));
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
                future -> Duration.ofMillis(1),
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
                future -> Duration.ofMillis(1),
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
                future -> Duration.ofMillis(1),
                callback);

        assertThrows(RuntimeException.class, () -> wrappedTask.call());
        verify(executor, times(1)).schedule(any(Callable.class), anyLong(), any(TimeUnit.class));
        verify(callback, times(1)).accept(any(ScheduledFuture.class));
    }
}
