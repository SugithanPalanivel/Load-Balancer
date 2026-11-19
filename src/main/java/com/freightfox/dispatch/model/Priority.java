package com.freightfox.dispatch.model;

/** Delivery priority. Lower rank is dispatched first. */
public enum Priority {
    HIGH(0), MEDIUM(1), LOW(2);

    private final int rank;

    Priority(int rank) {
        this.rank = rank;
    }

    public int rank() {
        return rank;
    }
}
