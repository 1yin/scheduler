package scheduler;

import org.junit.jupiter.api.Test;

import java.util.concurrent.ExecutionException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static scheduler.FlexibleScheduledThreadPoolExecutor.CompletedFuture;

public class CompletedFutureTest {

    @Test
    public void testResult() throws Exception {
        CompletedFuture<Boolean> future = new CompletedFuture<>(Boolean.TRUE);
        assertEquals(Boolean.TRUE, future.get());
    }

    @Test
    public void testException() throws InterruptedException {
        CompletedFuture<Boolean> future = new CompletedFuture<>(new RuntimeException());
        try {
            future.get();
            fail("expected exception not thrown");
        } catch (ExecutionException e) {
            assertTrue(RuntimeException.class.isInstance(e.getCause()));
        }
    }
}
