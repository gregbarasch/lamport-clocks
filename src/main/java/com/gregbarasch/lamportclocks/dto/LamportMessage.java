package com.gregbarasch.lamportclocks.dto;

import akka.actor.Address;
import com.gregbarasch.lamportclocks.model.LogicalClock;

import java.io.Serializable;

public abstract class LamportMessage implements Comparable<LamportMessage>, Serializable {

    private static final long serialVersionUID = 1L;
    private final Address senderAddress;
    private final LogicalClock timestamp;

    LamportMessage(Address senderAddress, LogicalClock timestamp) {
        this.senderAddress = senderAddress;
        this.timestamp = timestamp;
    }

    public Address getSenderAddress() {
        return senderAddress;
    }

    public LogicalClock getTimestamp() {
        return timestamp;
    }

    @Override
    public int compareTo(LamportMessage other) {
        int clockComparison = timestamp.compareTo(other.timestamp);

        // Tie breaker
        if (clockComparison == 0) {
            final int h1 = senderAddress.hashCode(), h2 = other.senderAddress.hashCode();
            if (h1 < h2) return -1;
            if (h1 > h2) return 1;
            throw new TotalOrderException();
        }

        return clockComparison;
    }
}