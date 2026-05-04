import { useEffect, useRef, useState } from 'react';
import { api, when } from '../api.js';
import { eventPhoto } from '../photos.js';
import { pay } from '../pay.js';

export function Tickets() {
  const [tickets, setTickets] = useState(null);
  const [error, setError] = useState('');
  const versions = useRef(null);

  const load = async () => {
    const query = versions.current === null ? '' : `?versions=${encodeURIComponent(versions.current)}`;
    const response = await api(`/tickets/mine${query}`);
    if (!response.ok || !response.data) return;
    versions.current = response.data.versions;
    setTickets(response.data.tickets);
  };

  useEffect(() => {
    load();
    const timer = setInterval(load, 10000);
    return () => clearInterval(timer);
  }, []);

  const cancel = async (ticket) => {
    if (!window.confirm(`Give back seat ${ticket.rowLabel}${ticket.number} for ${ticket.attendeeName}?`)) return;
    const response = await api(`/tickets/${ticket.ticketId}/cancel`, { method: 'POST' });
    if (!response.ok) setError(response.message);
    load();
  };

  const payNow = async (ticket) => {
    setError('');
    try {
      await pay(ticket.bookingId, ticket.eventTitle);
    } catch (failed) {
      setError(failed.message);
    }
    versions.current = null;
    load();
  };

  if (!tickets) return null;
  const events = [...new Map(tickets.map((ticket) => [ticket.eventId, ticket])).values()];

  return (
    <section>
      <h1 className="page-title">My tickets</h1>
      {error && <p className="error" role="alert">{error}</p>}
      {tickets.length === 0 && <p className="intro">No tickets yet. <a href="#/">Find an event.</a></p>}
      {events.map((first) => (
        <section key={first.eventId} className="ticket-event">
          <div className="ticket-event-head">
            <img src={eventPhoto({ id: first.eventId, title: first.eventTitle }).small} alt="" />
            <div>
              <h2 className="section-title">{first.eventTitle}</h2>
              <p className="quiet">{when(first.startsAt)}. Show these codes at the door.</p>
            </div>
          </div>
          <ul className="tickets">
            {tickets.filter((ticket) => ticket.eventId === first.eventId).map((ticket) => (
              <li key={ticket.ticketId} className={`ticket ${ticket.status.toLowerCase()}`}>
                <div className="ticket-fields">
                  <span><small>Area</small>{ticket.areaName}</span>
                  <span><small>Row</small><b>{ticket.rowLabel}</b></span>
                  <span><small>Seat</small><b className="yours">{ticket.number}</b></span>
                </div>
                <div className="ticket-stub">
                  <span className="name">{ticket.attendeeName}</span>
                  {ticket.status === 'HELD'
                    ? <span className="quiet">Code after payment</span>
                    : <button type="button" className="code" title="Copy code" aria-label={`Copy code ${ticket.code.split('').join(' ')}`}
                              onClick={() => navigator.clipboard?.writeText(ticket.code)}>{ticket.code}</button>}
                  {ticket.status === 'HELD'
                    ? <button className="link" onClick={() => payNow(ticket)}>Pay now to keep this seat</button>
                    : ticket.status === 'VALID' && new Date(ticket.startsAt) > new Date()
                    ? <button className="link" onClick={() => cancel(ticket)}>Give back this seat</button>
                    : <span className="quiet">{ticket.status === 'USED' ? 'Checked in' : ticket.status === 'CANCELLED' ? 'Cancelled' : 'Event started'}</span>}
                </div>
              </li>
            ))}
          </ul>
        </section>
      ))}
    </section>
  );
}
