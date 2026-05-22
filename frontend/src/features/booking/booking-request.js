export function reservationBody({ eventId, zone, quantity, attendees = [] }) {
  const ticketCount = Number(quantity);
  if (!Number.isInteger(ticketCount) || ticketCount < 1 || attendees.length !== ticketCount) {
    throw new Error('Choose one saved attendee for each ticket.');
  }

  return {
    eventId,
    zoneId: zone.id,
    quantity: ticketCount,
    attendeeNames: attendees.map(({ name }) => name),
  };
}

export function canBookDirectly(saleAccess) {
  return saleAccess?.saleType === 'STANDARD' && saleAccess.status === 'ON_SALE';
}

export function shouldJoinQueue(saleAccess) {
  return saleAccess?.saleType === 'FLASH' && saleAccess.canJoinQueue === true;
}
