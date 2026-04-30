package com.grabmyseat.checkin;

import java.time.OffsetDateTime;

public record CheckInResult(boolean admitted, String message, String attendeeName, String seat,
                            OffsetDateTime checkedInAt, String checkedInBy) {
}
