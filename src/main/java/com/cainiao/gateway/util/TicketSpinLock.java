package com.cainiao.gateway.util;

import java.util.concurrent.atomic.AtomicLong;

public class TicketSpinLock {

    private final AtomicLong nextTicket = new AtomicLong(0);
    private final AtomicLong nowServing = new AtomicLong(0);

    public void lock() {
        long myTicket = nextTicket.getAndIncrement();
        while (myTicket != nowServing.get()) {
            Thread.onSpinWait();
        }
    }

    public void unlock() {
        nowServing.incrementAndGet();
    }
}
