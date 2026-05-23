import { useEffect } from 'react';

/** Displays the server-issued QR payload without fabricating a QR image. */
export function TicketPanel({ ticket, onReturnHome }) {
  useEffect(() => {
    if (!ticket || !onReturnHome || window.matchMedia?.('(prefers-reduced-motion: reduce)').matches) return undefined;
    const timer = window.setTimeout(onReturnHome, 10_000);
    return () => window.clearTimeout(timer);
  }, [onReturnHome, ticket]);

  if (!ticket) return null;
  const attendees = ticket.attendeeNames ?? [];
  const attendance = ticket.attendance ?? {};

  return <section aria-labelledby="ticket-heading">
    {ticket.artworkUrl && <img src={ticket.artworkUrl} alt="" />}
    <h2 id="ticket-heading">{ticket.eventName || 'Your ticket'}</h2>
    <p>{ticket.holderName || 'Ticket holder'}</p>
    {attendees.length > 0 && <p>Attendees: {attendees.join(', ')}</p>}
    {Object.keys(attendance).length > 0 && <p>Entry status: {Object.values(attendance).join(', ')}</p>}
    {ticket.seatIds?.length > 0 && <p>Seats: {ticket.seatIds.join(', ')}</p>}
    <p>Present this code at entry{ticket.qrExpiresAt ? ` before ${new Date(ticket.qrExpiresAt).toLocaleTimeString()}` : ''}:</p>
    <output aria-label="Ticket QR payload">{ticket.qrPayload || 'Ticket code unavailable.'}</output>
    {onReturnHome && <p role="status">Returning to events in 10 seconds.</p>}
  </section>;
}
