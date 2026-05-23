import { useEffect, useState } from 'react';
import { gateway } from '../../api/client.js';

export function TicketsPanel() {
  const [tickets, setTickets] = useState([]); const [status, setStatus] = useState('Loading tickets…');
  const load = () => gateway.tickets.mine().then((result) => { if (result.ok) { setTickets(result.data); setStatus(''); } else setStatus(result.error?.message ?? 'Tickets are unavailable right now.'); });
  useEffect(() => { load(); }, []);
  const regenerate = async (token) => { const result = await gateway.tickets.regenerate(token); setStatus(result.ok ? 'QR code regenerated.' : result.error?.message ?? 'QR regeneration is unavailable right now.'); if (result.ok) load(); };
  const renderTicket = (ticket) => <li key={ticket.reservationToken ?? ticket.id}><b>{ticket.eventName ?? 'Event ticket'}</b> — {ticket.usedAt ? 'Used' : ticket.status ?? 'Ready'} {!ticket.usedAt && <button type="button" onClick={() => regenerate(ticket.reservationToken)}>Regenerate QR</button>}</li>;
  const upcoming = tickets.filter((ticket) => !ticket.usedAt);
  const history = tickets.filter((ticket) => ticket.usedAt);
  return <section className="profile-panel" aria-labelledby="tickets-heading"><p className="profile-panel__eyebrow">YOUR TICKETS</p><h2 id="tickets-heading">Tickets</h2>{status && <p className="profile-panel__message" role="status">{status}</p>}<h3>Upcoming tickets</h3>{upcoming.length ? <ul>{upcoming.map(renderTicket)}</ul> : <p>No upcoming tickets.</p>}<h3>Ticket history</h3>{history.length ? <ul>{history.map(renderTicket)}</ul> : <p>No past tickets.</p>}</section>;
}
