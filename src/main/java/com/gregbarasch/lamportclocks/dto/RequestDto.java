package com.gregbarasch.lamportclocks.dto;

import akka.actor.Address;
import com.gregbarasch.lamportclocks.model.LogicalClock;

import java.io.Serializable;

public final class RequestDto extends LamportMessage implements Serializable {

    private static final long serialVersionUID = 1L;

    public RequestDto(Address senderAddress, LogicalClock timestamp) {
        super(senderAddress, timestamp);
    }
}
