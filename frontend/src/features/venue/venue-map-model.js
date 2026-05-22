export const VENUE_LAYOUTS = Object.freeze({
  AUDITORIUM: { kind: 'seated', label: 'Stage' },
  STADIUM: { kind: 'seated', label: 'Pitch' },
  FESTIVAL_FIELD: { kind: 'standing', label: 'Main stage' },
  CLUB_STAGE: { kind: 'standing', label: 'DJ booth' },
  THEATRE: { kind: 'seated', label: 'Curtain' },
});

const layoutKeyFor = (layoutKey) => VENUE_LAYOUTS[layoutKey] ? layoutKey : 'AUDITORIUM';

export const layoutFor = (layoutKey) => VENUE_LAYOUTS[layoutKeyFor(layoutKey)];

export const standingQuantity = (available, quantity) => Math.min(Math.max(0, Number(quantity) || 0), Math.min(4, Math.max(0, Number(available) || 0)));

export const seatStatusMarker = (status) => status === 'AVAILABLE' ? null : status.charAt(0) + status.slice(1).toLowerCase();

export const mapModel = (event, zone) => {
  const layoutKey = layoutKeyFor(event.layoutKey);
  const available = Math.max(0, Number(zone.availableSeats) || 0);

  return {
    ...layoutFor(layoutKey),
    layoutKey,
    presentationClass: `venue-map--${layoutKey}`,
    interaction: 'zone',
    available,
    maximumQuantity: Math.min(4, available),
    price: Number(zone.price),
  };
};
