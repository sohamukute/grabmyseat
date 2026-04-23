package com.grabmyseat.booking;

import java.util.List;
import java.util.Set;

public record AreaGrid(List<Seat> seats, Set<Long> taken) {

    public boolean isFree(Seat seat) {
        return !taken.contains(seat.id());
    }
}
