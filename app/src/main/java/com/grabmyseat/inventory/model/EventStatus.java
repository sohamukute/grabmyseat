package com.grabmyseat.inventory.model;

public enum EventStatus {
    DRAFT, PUBLISHED, CANCELLED;

    public boolean canMoveTo(EventStatus next) {
        switch (this) {
            case DRAFT:
                return next == PUBLISHED || next == CANCELLED;
            case PUBLISHED:
                return next == CANCELLED;
            default:
                return false;
        }
    }
}
