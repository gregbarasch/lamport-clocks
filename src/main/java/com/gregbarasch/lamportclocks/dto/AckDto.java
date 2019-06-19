package com.gregbarasch.lamportclocks.dto;

import akka.actor.Address;
import com.gregbarasch.lamportclocks.model.LogicalClock;

import java.io.Serializable;

public class AckDto extends LamportMessage implements Serializable {

    private static final long serialVersionUID = 1L;

    public AckDto(Address senderAddress, LogicalClock timestamp) {
        super(senderAddress, timestamp);
    }
}
