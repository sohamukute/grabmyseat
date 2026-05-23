const capacityFields = [
  'generalAdmissionCapacity',
  'leftPremiumCapacity',
  'rightPremiumCapacity',
];
const priceFields = [
  'generalAdmissionPrice',
  'leftPremiumPrice',
  'rightPremiumPrice',
];

export function emptyEventDraft() {
  return {
    name: '',
    venue: '',
    artworkUrl: '',
    startsAt: '',
    endsAt: '',
    saleType: 'STANDARD',
    queueOpensAt: '',
    saleStartsAt: '',
    saleEndsAt: '',
    generalAdmissionCapacity: '',
    generalAdmissionPrice: '',
    leftPremiumCapacity: '',
    leftPremiumPrice: '',
    rightPremiumCapacity: '',
    rightPremiumPrice: '',
  };
}

const instant = (value) => value ? new Date(value).toISOString() : null;
const number = (value) => Number(value);

export function toFixedLayoutEventRequest(draft) {
  const layout = {
    generalAdmissionCapacity: number(draft.generalAdmissionCapacity),
    generalAdmissionPrice: number(draft.generalAdmissionPrice),
    leftPremiumCapacity: number(draft.leftPremiumCapacity),
    leftPremiumPrice: number(draft.leftPremiumPrice),
    rightPremiumCapacity: number(draft.rightPremiumCapacity),
    rightPremiumPrice: number(draft.rightPremiumPrice),
  };
  return {
    name: draft.name.trim(),
    venue: draft.venue.trim(),
    artworkUrl: draft.artworkUrl,
    startsAt: instant(draft.startsAt),
    endsAt: instant(draft.endsAt),
    queueOpensAt: draft.saleType === 'FLASH' ? instant(draft.queueOpensAt) : null,
    saleStartsAt: instant(draft.saleStartsAt),
    saleEndsAt: instant(draft.saleEndsAt),
    saleType: draft.saleType,
    layout,
    zones: [
      { name: 'General Admission', capacity: layout.generalAdmissionCapacity, price: layout.generalAdmissionPrice, seats: [], type: 'STANDING' },
      { name: 'Left Premium', capacity: layout.leftPremiumCapacity, price: layout.leftPremiumPrice, seats: [], type: 'SEATED' },
      { name: 'Right Premium', capacity: layout.rightPremiumCapacity, price: layout.rightPremiumPrice, seats: [], type: 'SEATED' },
    ],
  };
}

export function validateEventDraft(draft) {
  const errors = {};
  if (!draft.name.trim()) errors.name = 'Enter an event name.';
  if (!draft.venue.trim()) errors.venue = 'Enter a venue.';
  if (!draft.artworkUrl) errors.artworkUrl = 'Upload an event poster.';
  if (!draft.startsAt) errors.startsAt = 'Choose when the event starts.';
  if (!draft.saleStartsAt) errors.saleStartsAt = 'Choose when ticket sales start.';
  if (!draft.saleEndsAt) errors.saleEndsAt = 'Choose when ticket sales end.';

  for (const field of capacityFields) {
    if (!Number.isInteger(number(draft[field])) || number(draft[field]) < 1) {
      errors[field] = 'Capacity must be at least 1.';
    }
  }
  for (const field of priceFields) {
    if (!Number.isFinite(number(draft[field])) || number(draft[field]) <= 0) {
      errors[field] = 'Price must be greater than 0.';
    }
  }

  const startsAt = Date.parse(draft.startsAt);
  const saleStartsAt = Date.parse(draft.saleStartsAt);
  const saleEndsAt = Date.parse(draft.saleEndsAt);
  if (Number.isFinite(saleStartsAt) && Number.isFinite(startsAt) && saleStartsAt >= startsAt) {
    errors.saleStartsAt = 'Sale must start before the event.';
  }
  if (Number.isFinite(saleStartsAt) && Number.isFinite(saleEndsAt) && saleEndsAt <= saleStartsAt) {
    errors.saleEndsAt = 'Sale end must be after sale start.';
  } else if (Number.isFinite(saleEndsAt) && Number.isFinite(startsAt) && saleEndsAt > startsAt) {
    errors.saleEndsAt = 'Sale must end before the event starts.';
  }

  if (!['STANDARD', 'FLASH'].includes(draft.saleType)) errors.saleType = 'Choose Standard or Flash.';
  if (draft.saleType === 'FLASH') {
    if (!draft.queueOpensAt) {
      errors.queueOpensAt = 'Choose when the queue opens.';
    } else if (Number.isFinite(saleStartsAt) && Date.parse(draft.queueOpensAt) >= saleStartsAt) {
      errors.queueOpensAt = 'Queue must open before the flash sale.';
    }
  }
  return errors;
}
