package scheduler;

import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ScheduledFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static scheduler.FlexibleScheduledThreadPoolExecutor.ScheduledFutureTask;

public class ScheduledFutureTaskTest {

    @Test
    public void testChangingFuture() throws Exception {
        CountDownLatch secondGetCalled = new CountDownLatch(1);
        CountDownLatch changed = new CountDownLatch(1);

        ScheduledFuture<Boolean> future1 = mock(ScheduledFuture.class);
        ScheduledFuture<Boolean> future2 = mock(ScheduledFuture.class);
        ScheduledFutureTask<Boolean> future = new ScheduledFutureTask<>();

        when(future1.get()).thenAnswer(invocationOnMock -> {
            future.accept(future2);
            changed.countDown();
            secondGetCalled.await();
            return Boolean.TRUE;
        });

        when(future2.get()).thenAnswer(invocationOnMock -> {
            secondGetCalled.countDown();
            return Boolean.FALSE;
        });

        future.accept(future1);

        new Thread(() -> {
            try {
                changed.await();
                assertEquals(Boolean.FALSE, future.get());
            } catch (Exception e) {
                fail(e);
            }
        }).start();

        assertEquals(Boolean.TRUE, future.get());
        verify(future1, times(1)).get();
        verify(future2, times(1)).get();
    }
}
