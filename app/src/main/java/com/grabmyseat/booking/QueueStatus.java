package com.grabmyseat.booking;

public enum QueueStatus {
    WAITING, SEATED, LEFT, CLOSED;

    public boolean canMoveTo(QueueStatus next) {
        switch (this) {
            case WAITING:
                return next == SEATED || next == LEFT || next == CLOSED;
            default:
                return false;
        }
    }
}
