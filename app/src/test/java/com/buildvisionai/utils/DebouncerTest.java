package com.buildvisionai.utils;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

public class DebouncerTest {
    /** A manual clock: tasks run when the test advances time. */
    static final class FakeTimer implements Debouncer.Timer {
        long now;
        final List<long[]> due = new ArrayList<>();        // {id, dueTime}
        final List<Runnable> tasks = new ArrayList<>();
        final List<Boolean> cancelled = new ArrayList<>();

        @Override public Runnable schedule(Runnable task, long delayMs) {
            final int id = tasks.size();
            tasks.add(task);
            cancelled.add(false);
            due.add(new long[]{id, now + delayMs});
            return () -> cancelled.set(id, true);
        }

        void advance(long ms) {
            now += ms;
            for (long[] d : new ArrayList<>(due)) {
                int id = (int) d[0];
                if (d[1] <= now && !cancelled.get(id)) {
                    cancelled.set(id, true);
                    tasks.get(id).run();
                }
            }
        }
    }

    @Test public void aBurstRunsOnlyTheLastTaskOnceAfterTheQuietPeriod() {
        FakeTimer t = new FakeTimer();
        Debouncer d = new Debouncer(t, 1000);
        List<String> ran = new ArrayList<>();
        d.submit(() -> ran.add("a"));
        t.advance(400);
        d.submit(() -> ran.add("b"));
        t.advance(400);
        d.submit(() -> ran.add("c"));
        t.advance(999);
        assertTrue("still waiting", ran.isEmpty());
        assertTrue(d.hasPending());
        t.advance(1);
        assertEquals(1, ran.size());
        assertEquals("c", ran.get(0));
        assertFalse(d.hasPending());
        t.advance(5000);
        assertEquals("nothing runs twice", 1, ran.size());
    }

    @Test public void flushRunsThePendingTaskNowAndOnlyOnce() {
        FakeTimer t = new FakeTimer();
        Debouncer d = new Debouncer(t, 1000);
        List<String> ran = new ArrayList<>();
        d.flush();                                   // nothing pending: no effect
        d.submit(() -> ran.add("x"));
        d.flush();
        assertEquals(1, ran.size());
        t.advance(5000);
        assertEquals("the cancelled timer does not fire it again", 1, ran.size());
        d.flush();
        assertEquals(1, ran.size());
    }

    @Test public void aTaskSubmittedByATaskIsScheduledAgain() {
        FakeTimer t = new FakeTimer();
        Debouncer d = new Debouncer(t, 100);
        List<String> ran = new ArrayList<>();
        d.submit(() -> { ran.add("first"); d.submit(() -> ran.add("second")); });
        t.advance(100);
        assertEquals(1, ran.size());
        t.advance(100);
        assertEquals(2, ran.size());
    }
}
