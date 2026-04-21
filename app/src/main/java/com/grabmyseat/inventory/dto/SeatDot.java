package com.grabmyseat.inventory.dto;

public record SeatDot(long id, String rowLabel, int rowRank, int number, boolean taken) {
}
