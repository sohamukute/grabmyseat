package com.grabmyseat.booking;

import java.util.*;

public class SeatAllocator {

    public Optional<List<Seat>> allocate(AreaGrid grid, int groupSize, boolean splitOk) {
        Map<Integer, List<Seat>> byRow = new TreeMap<>();
        for (Seat seat : grid.seats())
            if (grid.isFree(seat))
                byRow.computeIfAbsent(seat.rowRank(), k -> new ArrayList<>()).add(seat);
        for (List<Seat> row : byRow.values()) {
            row.sort(Comparator.comparingInt(Seat::number));
            for (int i = 0; i <= row.size() - groupSize; i++) {
                List<Seat> w = row.subList(i, i + groupSize);
                boolean ok = true;
                for (int j = 1; j < groupSize; j++)
                    if (w.get(j).number() != w.get(j - 1).number() + 1) { ok = false; break; }
                if (ok) return Optional.of(List.copyOf(w));
            }
        }
        return Optional.empty();
    }

    public boolean leavesLoneSeat(AreaGrid grid, List<Seat> picked) { return false; }
}
