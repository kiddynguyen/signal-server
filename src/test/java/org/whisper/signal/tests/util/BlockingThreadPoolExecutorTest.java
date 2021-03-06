package org.whisper.signal.tests.util;

import org.junit.Test;
import org.whisper.signal.util.BlockingThreadPoolExecutor;
import org.whisper.signal.util.Util;

import static org.junit.Assert.assertTrue;

public class BlockingThreadPoolExecutorTest {

    @Test
    public void testBlocking() {
        BlockingThreadPoolExecutor executor = new BlockingThreadPoolExecutor(1, 3);
        long start = System.currentTimeMillis();

        executor.execute(new Runnable() {
            @Override
            public void run() {
                Util.sleep(1000);
            }
        });

        assertTrue(System.currentTimeMillis() - start < 500);
        start = System.currentTimeMillis();

        executor.execute(new Runnable() {
            @Override
            public void run() {
                Util.sleep(1000);
            }
        });

        assertTrue(System.currentTimeMillis() - start < 500);

        start = System.currentTimeMillis();

        executor.execute(new Runnable() {
            @Override
            public void run() {
                Util.sleep(1000);
            }
        });

        assertTrue(System.currentTimeMillis() - start < 500);

        start = System.currentTimeMillis();

        executor.execute(new Runnable() {
            @Override
            public void run() {
                Util.sleep(1000);
            }
        });

        assertTrue(System.currentTimeMillis() - start > 500);
    }

}
