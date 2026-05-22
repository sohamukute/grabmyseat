export function bookingShellState(event) {
  return { event, selection: { zoneId: event.zones[0]?.id, quantity: 1 } };
}

export function hasLiveHold(hold, ticket) {
  return Boolean(hold && !ticket);
}

export function requiresFreshBooking(status) {
  return status === 'FAILED' || status === 'COMPENSATED';
}

export function recoverFromExpiredHold(setHold, setPermitToken, setQueueToken, joinQueue) {
  setHold(null);
  setPermitToken(null);
  setQueueToken(null);
  return joinQueue;
}

export function isSelectionLocked(step, hasHold = false) {
  return hasHold || ['HOLD', 'CONFIRMING', 'TICKET'].includes(step);
}

export function selectZone(state, zone, locked = false) {
  return locked ? state : { ...state, selection: { zoneId: zone.id, quantity: 1 } };
}

export function changeQuantity(state, quantity, locked = false) {
  return locked ? state : { ...state, selection: { ...state.selection, quantity } };
}

export function closeBookingShell(state) {
  return { ...state, event: null };
}
