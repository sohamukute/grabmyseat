package com.grabmyseat.inventory.dto;

public record AreaCount(long areaId, String name, long version, long total, long free, long longestRun, long pricePaise) {
}
