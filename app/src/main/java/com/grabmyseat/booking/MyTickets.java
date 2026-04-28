package com.grabmyseat.booking;

import java.util.List;

public record MyTickets(String versions, List<MyTicket> tickets) {
}
