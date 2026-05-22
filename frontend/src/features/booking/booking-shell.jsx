import React, { useEffect, useReducer, useState } from 'react';
import { ChevronLeft } from 'lucide-react';
import { gateway } from '../../api/client.js';
import { BookingFlow } from './booking-flow.jsx';
import { VenueMap } from '../venue/venue-map.jsx';
import { standingQuantity } from '../venue/venue-map-model.js';
import { bookingShellState, changeQuantity, closeBookingShell, isSelectionLocked, selectZone } from './booking-shell-state.js';

const bookingActions = {
  joinQueue: (eventId) => gateway.queue.join(eventId),
  queuePosition: (eventId, token, options) => gateway.queue.position(eventId, token, options),
  queuePermit: (eventId, token, options) => gateway.queue.permit(eventId, token, options),
  createReservation: (body, options) => gateway.reservations.create(body, options),
  confirmBooking: (token) => gateway.saga.confirm(token),
  bookingStatus: (token, options) => gateway.saga.status(token, options),
  getTicket: (token) => gateway.tickets.get(token),
  cancelBooking: (token) => gateway.saga.cancel(token),
};

const date = (value) => new Intl.DateTimeFormat('en-IN', { weekday: 'short', day: 'numeric', month: 'short' }).format(new Date(value));

export function BookingShell({ event, authenticated, onBack }) {
  const [state, dispatch] = useReducer((current, action) => action(current), event, bookingShellState);
  const [bookingState, setBookingState] = useState({ step: 'DETAIL', hasHold: false });
  const selectionLocked = isSelectionLocked(bookingState.step, bookingState.hasHold);
  const [attendees, setAttendees] = useState([]);
  const [saleAccess, setSaleAccess] = useState(null);
  const { zoneId, quantity } = state.selection;
  const zone = event.zones.find((item) => item.id === zoneId);
  const selectionQuantity = standingQuantity(zone?.availableSeats, quantity);
  const chooseZone = (nextZone) => dispatch((current) => selectZone(current, nextZone, selectionLocked));
  const changeSelectedQuantity = (nextQuantity) => dispatch((current) => changeQuantity(current, nextQuantity, selectionLocked));
  useEffect(() => {
    if (!authenticated) { setAttendees([]); return undefined; }
    let active = true;
    gateway.attendees.list().then((result) => { if (active && result.ok) setAttendees(result.data); });
    return () => { active = false; };
  }, [authenticated]);
  useEffect(() => {
    let active = true;
    gateway.events.saleAccess(event.id).then((result) => { if (active && result.ok) setSaleAccess(result.data); });
    return () => { active = false; };
  }, [event.id]);
  const close = () => { dispatch(closeBookingShell); onBack(); };

  return <main className="booking-shell" aria-label={`Book ${event.name}`}>
    <header className="booking-shell__header"><button type="button" className="booking-shell__back" onClick={close}><ChevronLeft /> Back to events</button><b>GRABMYSEAT</b></header>
    <div className="booking-shell__layout">
      <section className="booking-shell__map-pane">
        <div className="booking-shell__event"><p>BOOK TICKETS</p><h1>{event.name}</h1><span>{date(event.startsAt)} · {event.venue}</span></div>
        <div className="zone-picker" role="list" aria-label="Ticket tiers">{event.zones.map((item) => <button type="button" key={item.id} className={item.id === zoneId ? 'selected' : ''} disabled={selectionLocked} onClick={() => chooseZone(item)}><b>{item.name}</b><small>{item.availableSeats} left · ₹{Number(item.price).toLocaleString('en-IN')}</small></button>)}</div>
        {zone && <VenueMap event={event} zone={zone} quantity={selectionQuantity} onQuantityChange={changeSelectedQuantity} selectionLocked={selectionLocked} />}
      </section>
      <aside className="booking-shell__summary" data-booking-step={bookingState.step} aria-label="Booking summary">
        <img src={event.artworkUrl} alt="" />
        <div><p>YOUR SELECTION</p><h2>{zone?.name}</h2><span>{selectionQuantity} ticket{selectionQuantity === 1 ? '' : 's'} · seats assigned together</span></div>
        {authenticated ? <BookingFlow eventId={event.id} zone={zone} quantity={selectionQuantity} attendees={attendees} saleAccess={saleAccess} actions={bookingActions} onStateChange={setBookingState} onReturnHome={close} /> : <p className="map-note">Sign in before joining the fair-access queue.</p>}
      </aside>
    </div>
  </main>;
}
