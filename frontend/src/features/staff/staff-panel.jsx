import { useEffect, useState } from 'react';
import { gateway } from '../../api/client.js';
import { CameraScanner } from './camera-scanner.jsx';

const tabs = [
  ['available', 'Published events'],
  ['gate', 'Gate check-in'],
];
const messageFor = (result) => result?.error?.message ?? 'This operation is unavailable right now.';
const eventTime = (value) => value
  ? new Intl.DateTimeFormat(undefined, { dateStyle: 'medium', timeStyle: 'short' }).format(new Date(value))
  : 'Schedule unavailable';
const tabIds = tabs.map(([id]) => id);
const moveTabFocus = (event, current, select) => {
  if (!['ArrowLeft', 'ArrowRight', 'Home', 'End'].includes(event.key)) return;
  event.preventDefault();
  const currentIndex = tabIds.indexOf(current);
  const nextIndex = event.key === 'Home' ? 0
    : event.key === 'End' ? tabIds.length - 1
      : (currentIndex + (event.key === 'ArrowRight' ? 1 : -1) + tabIds.length) % tabIds.length;
  const next = tabIds[nextIndex];
  select(next);
  requestAnimationFrame(() => document.getElementById(`staff-tab-${next}`)?.focus());
};

function EventList({ events, empty, action }) {
  if (!events.length) return <p className="profile-panel__message">{empty}</p>;
  return <ul className="staff-event-list">
    {events.map((event) => <li key={event.eventId}>
      {event.artworkUrl && <img src={event.artworkUrl} alt="" />}
      <div>
        <strong>{event.eventName}</strong>
        <span>{event.venue} · {eventTime(event.startsAt)}</span>
      </div>
      {action?.(event)}
    </li>)}
  </ul>;
}

export function StaffPanel() {
  const [tab, setTab] = useState('available');
  const [events, setEvents] = useState([]);
  const [loading, setLoading] = useState(true);
  const [token, setToken] = useState('');
  const [attendeeNames, setAttendeeNames] = useState('');
  const [status, setStatus] = useState('');
  const [checkingIn, setCheckingIn] = useState(false);
  const [cameraOpen, setCameraOpen] = useState(false);

  const load = async () => {
    setLoading(true);
    const available = await gateway.staff.events();
    if (available.ok) setEvents(available.data.map((event) => ({
      ...event,
      eventId: event.id,
      eventName: event.name,
      status: 'AVAILABLE',
    })));
    setStatus(available.ok ? '' : messageFor(available));
    setLoading(false);
  };

  useEffect(() => { load(); }, []);


  const action = async (kind) => {
    setCheckingIn(true);
    const result = kind === 'validate'
      ? await gateway.staff.validateTicket(token)
      : await gateway.staff.checkInTicket(token, {
        attendeesPresent: attendeeNames.split(',').map((name) => name.trim()).filter(Boolean),
      });
    setCheckingIn(false);
    setStatus(result.ok
      ? kind === 'validate' ? 'Ticket is valid.' : 'Ticket checked in.'
      : messageFor(result));
  };


  return <section className="profile-panel staff-panel" aria-labelledby="staff-heading">
    <p className="profile-panel__eyebrow">STAFF WORKSPACE</p>
    <h2 id="staff-heading">Event operations</h2>
    {status && <p className="profile-panel__message" role="status" aria-live="polite">{status}</p>}

    <div className="workspace-tabs" role="tablist" aria-label="Staff workspace">
      {tabs.map(([id, label]) => <button
        key={id}
        id={`staff-tab-${id}`}
        type="button"
        role="tab"
        aria-selected={tab === id}
        aria-controls={`staff-panel-${id}`}
        tabIndex={tab === id ? 0 : -1}
        onClick={() => setTab(id)}
        onKeyDown={(event) => moveTabFocus(event, tab, setTab)}
      >{label}</button>)}
    </div>

    <div id={`staff-panel-${tab}`} role="tabpanel" aria-labelledby={`staff-tab-${tab}`} tabIndex="0">
      {loading && <p className="profile-panel__message" role="status">Loading staff events…</p>}
      {!loading && tab === 'available' && <EventList
        events={events}
        empty="There are no published events available right now."
      />}
      {!loading && tab === 'gate' && <section className="staff-check-in" aria-labelledby="staff-check-in-heading">
        <h3 id="staff-check-in-heading">Gate check-in</h3>
        <p className="profile-panel__message">Use the ticket token from a guest’s QR code. The service verifies your event assignment before admitting anyone.</p>
        <label htmlFor="staff-ticket-token">Ticket token</label>
        <input id="staff-ticket-token" required value={token} onChange={(event) => setToken(event.target.value)} />
        <button type="button" onClick={() => setCameraOpen(true)}>Scan ticket QR</button>
        {cameraOpen && <CameraScanner onToken={setToken} onClose={() => setCameraOpen(false)} />}
        <label htmlFor="staff-attendees">Attendees present</label>
        <input
          id="staff-attendees"
          value={attendeeNames}
          placeholder="Names separated by commas"
          onChange={(event) => setAttendeeNames(event.target.value)}
        />
        <div className="staff-check-in__actions">
          <button type="button" disabled={!token || checkingIn} onClick={() => action('validate')}>Validate ticket</button>
          <button type="button" disabled={!token || !attendeeNames.trim() || checkingIn} onClick={() => action('checkin')}>Check in</button>
        </div>
      </section>}
    </div>
  </section>;
}
