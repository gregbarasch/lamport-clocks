package com.gregbarasch.lamportclocks.model;

import java.io.Serializable;
import java.util.Objects;

public class LogicalClock implements Comparable<LogicalClock>, Serializable {

    private static final long serialVersionUID = 1L;
    private long clock = 0;

    public long getClock() {
        return clock;
    }

    public void increment() {
        ++clock;
    }

    // If the other clock is greater than ours, we want to catch up
    public void sync(LogicalClock other) {
        final long otherClock = other.clock;
        if (otherClock >= clock) {
            clock = otherClock+1;
        }
    }

    @Override
    public int compareTo(LogicalClock other) {
        return Long.compare(clock, other.clock);
    }

    @Override
    @SuppressWarnings("MethodDoesntCallSuperMethod")
    public LogicalClock clone() {
        final LogicalClock clone = new LogicalClock();
        clone.clock = clock;
        return clone;
    }

    @Override
    public boolean equals(Object o) {
        if (o == null) return false;
        if (o == this) return true;
        if (!(LogicalClock.class.isAssignableFrom(o.getClass()))) return false;

        final LogicalClock other = (LogicalClock) o;
        return clock == other.clock;
    }

    @Override
    public int hashCode() {
        return Objects.hash(clock);
    }
}
