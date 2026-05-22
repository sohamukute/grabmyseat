import React from 'react';
import { mapModel, standingQuantity } from './venue-map-model.js';
import './venue-map.css';

export function VenueMap({ event, zone, quantity, onQuantityChange, selectionLocked = false }) {
  const model = mapModel(event, zone);
  const displayedQuantity = standingQuantity(model.maximumQuantity, quantity);

  return <section className={`venue-map ${model.presentationClass}`} aria-label={`${event.venue} zone map`}>
    <div className="venue-map__stage" aria-label={model.label}>{model.label}</div>
    <div className="venue-map__heading"><span>{zone.type === 'STANDING' ? 'Standing area' : 'Allocated seating zone'}</span><small>{model.available} available</small></div>
    <div className="venue-map__capacity" aria-label={`${zone.name} ticket quantity`}>
      <b>{zone.name}</b><span>{zone.type === 'STANDING' ? 'General-admission area' : 'Seats assigned together after queue admission'} · ₹{model.price.toLocaleString('en-IN')}</span>
      <div className="quantity-control"><button type="button" aria-label="Decrease quantity" disabled={selectionLocked || displayedQuantity <= 1} onClick={() => onQuantityChange(displayedQuantity - 1)}>−</button><output aria-live="polite">{displayedQuantity}</output><button type="button" aria-label="Increase quantity" disabled={selectionLocked || displayedQuantity >= model.maximumQuantity} onClick={() => onQuantityChange(displayedQuantity + 1)}>+</button></div>
    </div>
  </section>;
}
