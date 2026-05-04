import { useEffect, useState } from 'react';
import { api, when } from '../api.js';

const iso = (local) => (local ? new Date(local).toISOString() : null);

export function Organizer({ organizer }) {
  const [events, setEvents] = useState([]);
  const [venues, setVenues] = useState([]);
  const [draft, setDraft] = useState({ venueId: '', title: '', startsAt: '', endsAt: '', bookingOpensAt: '', bookingClosesAt: '', maxPerPerson: 6,
    category: 'Theatre', tagline: '', description: '', presentedBy: '', language: '', ageGuidance: 'All ages', lineup: '' });
  const [errors, setErrors] = useState({});
  const [message, setMessage] = useState('');

  const load = () => api('/inventory/organizer/events').then((response) => response.ok && setEvents(response.data));
  useEffect(() => {
    if (!organizer) return;
    load();
    api('/inventory/venues').then((response) => response.ok && setVenues(response.data));
  }, [organizer]);

  if (!organizer) return <p className="intro">This page is for organizers. Ask an organizer to set up your event.</p>;

  const field = (name) => ({ value: draft[name], onChange: (e) => setDraft({ ...draft, [name]: e.target.value }) });

  const create = async (submit) => {
    submit.preventDefault();
    const response = await api('/inventory/organizer/events', {
      method: 'POST',
      body: {
        venueId: Number(draft.venueId),
        title: draft.title,
        startsAt: iso(draft.startsAt),
        endsAt: iso(draft.endsAt),
        bookingOpensAt: iso(draft.bookingOpensAt),
        bookingClosesAt: iso(draft.bookingClosesAt),
        maxPerPerson: Number(draft.maxPerPerson),
        category: draft.category,
        tagline: draft.tagline,
        description: draft.description,
        presentedBy: draft.presentedBy,
        language: draft.language,
        ageGuidance: draft.ageGuidance,
        performers: draft.lineup.split('\n').map((line) => line.split(',')).filter((parts) => parts[0].trim())
          .map(([name, ...role]) => ({ name: name.trim(), role: role.join(',').trim() || 'Performer' })),
      },
    });
    if (!response.ok) {
      setErrors(response.fields);
      setMessage(response.message);
      return;
    }
    window.location.hash = `#/organizer/${response.data.id}`;
  };

  return (
    <section>
      <h1 className="page-title">Your events</h1>
      <ul className="rows">
        {events.map((event) => (
          <li key={event.id}>
            <a href={`#/organizer/${event.id}`}>
              <strong>{event.title}</strong>
              <span>{event.city}, {when(event.startsAt)}</span>
              <span className="quiet">{event.status.toLowerCase()}</span>
            </a>
          </li>
        ))}
      </ul>

      <h2 className="section">New event</h2>
      <form className="form two" onSubmit={create}>
        <label>Venue
          <select required {...field('venueId')}>
            <option value="">Pick a hall</option>
            {venues.map((venue) => <option key={venue.id} value={venue.id}>{venue.name}, {venue.city}</option>)}
          </select>
        </label>
        <label>Title<input required maxLength={150} {...field('title')} /></label>
        <label>Starts<input required type="datetime-local" {...field('startsAt')} /></label>
        <label>Ends<input required type="datetime-local" {...field('endsAt')} />{errors.endsAt && <em>{errors.endsAt}</em>}</label>
        <label>Booking opens<input required type="datetime-local" {...field('bookingOpensAt')} /></label>
        <label>Booking closes<input required type="datetime-local" {...field('bookingClosesAt')} />{errors.bookingClosesAt && <em>{errors.bookingClosesAt}</em>}</label>
        <label>Seats each person can hold<input required type="number" min={1} max={20} {...field('maxPerPerson')} /></label>
        <label>Category
          <select {...field('category')}>
            {['Theatre', 'Film', 'Music', 'Dance', 'Poetry', 'Talk', 'Comedy'].map((c) => <option key={c}>{c}</option>)}
          </select>
        </label>
        <label>Presented by<input required maxLength={100} placeholder="Your group or society" {...field('presentedBy')} /></label>
        <label>Language<input required maxLength={60} placeholder="Hindi, English" {...field('language')} /></label>
        <label>Age guidance
          <select {...field('ageGuidance')}>
            {['All ages', '12+', '16+', '18+'].map((a) => <option key={a}>{a}</option>)}
          </select>
        </label>
        <label className="full">One line people see on the card<input required maxLength={200} {...field('tagline')} /></label>
        <label className="full">About the show<textarea required rows={4} maxLength={4000} {...field('description')} /></label>
        <label className="full">Lineup, one per line as name, role<textarea rows={4} placeholder={'Aanya Sethi, Urdu ghazals\nDhruv Nair, Sarangi'} {...field('lineup')} /></label>
        {message && <p className="error" role="alert">{message}</p>}
        <div className="actions"><button className="pill" type="submit">Save draft</button></div>
      </form>
    </section>
  );
}

export function OrganizerEvent({ eventId }) {
  const [event, setEvent] = useState(null);
  const [report, setReport] = useState(null);
  const [staff, setStaff] = useState([]);
  const [username, setUsername] = useState('');
  const [message, setMessage] = useState('');

  const load = async () => {
    const found = await api(`/inventory/organizer/events/${eventId}`);
    if (!found.ok) { setMessage(found.message); return; }
    setEvent(found.data);
    const [r, s] = await Promise.all([
      api(`/inventory/organizer/events/${eventId}/report`),
      api(`/inventory/organizer/events/${eventId}/staff`),
    ]);
    if (r.ok) setReport(r.data);
    if (s.ok) setStaff(s.data);
  };
  useEffect(() => { load(); }, [eventId]);

  const act = async (path, confirmText) => {
    if (confirmText && !window.confirm(confirmText)) return;
    const response = await api(`/inventory/organizer/events/${eventId}/${path}`, { method: 'POST' });
    setMessage(response.ok ? '' : response.message);
    load();
  };

  const addStaff = async (submit) => {
    submit.preventDefault();
    const response = await api(`/inventory/organizer/events/${eventId}/staff`, { method: 'POST', body: { username } });
    if (response.ok) { setStaff(response.data); setUsername(''); setMessage(''); } else setMessage(response.message);
  };

  const removeStaff = async (member) => {
    const response = await api(`/inventory/organizer/events/${eventId}/staff/${member.userId}`, { method: 'DELETE' });
    if (response.ok) setStaff(response.data);
  };

  if (!event) return message ? <p className="intro">{message} <a href="#/organizer">Back to your events.</a></p> : null;

  return (
    <section>
      <a className="back" href="#/organizer">Your events</a>
      <h1 className="page-title">{event.title}</h1>
      <p className="intro">{event.venueName}, {event.city}. {when(event.startsAt)}. Status: {event.status.toLowerCase()}.</p>
      {message && <p className="error" role="alert">{message}</p>}
      <div className="organizer-actions">
        {event.status === 'DRAFT' && <button className="pill" onClick={() => act('publish')}>Publish</button>}
        {event.status !== 'CANCELLED' && <button className="pill ghost" onClick={() => act('cancel', 'Cancel this event? Every ticket is cancelled and every queue is closed.')}>Cancel event</button>}
      </div>

      {report && (
        <>
          <table className="numbers">
            <thead><tr><th>Area</th><th>Booked</th><th>In queue</th><th>Checked in</th></tr></thead>
            <tbody>
              {report.areas.map((area) => (
                <tr key={area.areaId}>
                  <td>{area.name}</td><td><span className="meter small" aria-hidden="true"><span style={{ width: `${Math.round(area.booked / area.total * 100)}%` }} /></span>{area.booked} of {area.total}</td><td>{area.queueLength}</td><td>{area.checkedIn}</td>
                </tr>
              ))}
            </tbody>
          </table>

          <h2 className="section">Gate staff</h2>
          <ul className="rows compact">
            {staff.map((member) => (
              <li key={member.userId}><span>{member.username}</span><button className="link" onClick={() => removeStaff(member)}>Remove</button></li>
            ))}
          </ul>
          <form className="inline" onSubmit={addStaff}>
            <input required placeholder="Username" value={username} onChange={(e) => setUsername(e.target.value)} aria-label="Staff username" />
            <button className="pill ghost" type="submit">Add to staff</button>
          </form>

          <h2 className="section">Attendees ({report.attendees.length})</h2>
          <table className="numbers">
            <thead><tr><th>Name</th><th>Seat</th><th>Booked by</th><th>Status</th></tr></thead>
            <tbody>
              {report.attendees.map((a, i) => (
                <tr key={i}><td>{a.attendeeName}</td><td>{a.areaName} {a.rowLabel}{a.number}</td><td>{a.bookedBy}</td><td>{a.status === 'USED' ? 'Checked in' : a.status === 'HELD' ? 'Awaiting payment' : 'Booked'}</td></tr>
              ))}
            </tbody>
          </table>
        </>
      )}
    </section>
  );
}
