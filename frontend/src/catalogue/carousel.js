export function canAutoAdvance({ paused, reducedMotion, length, eventSelected }) {
  return !paused && !reducedMotion && length >= 2 && !eventSelected;
}

export function nextFeaturedIndex(current, direction, length) {
  return length ? (current + direction + length) % length : 0;
}

export function slotFor(index, active, length) {
  const slot = nextFeaturedIndex(index, -active, length);
  return slot === 0 ? 'active' : slot;
}

export function settleFeaturedMotion(active, transition, entering, length) {
  return { active: transition ? nextFeaturedIndex(active, transition, length) : active, transition: null, entering: null };
}

export function cancelFeaturedMotion(active) {
  return { active, transition: null, entering: null };
}

export function displayedFeatured(featured) {
  return featured;
}
