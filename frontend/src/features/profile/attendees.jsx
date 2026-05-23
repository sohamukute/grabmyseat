import { useEffect, useState } from 'react';
import { gateway } from '../../api/client.js';

const blank = { name: '', age: '', mobile: '', email: '' };
export function AttendeesPanel() {
  const [attendees, setAttendees] = useState([]); const [draft, setDraft] = useState(blank); const [status, setStatus] = useState('Loading attendees…');
  const load = () => gateway.attendees.list().then((result) => { if (result.ok) { setAttendees(result.data); setStatus(''); } else setStatus(result.error?.message ?? 'Attendees are unavailable right now.'); });
  useEffect(() => { load(); }, []);
  const add = async (event) => { event.preventDefault(); const result = await gateway.attendees.create({ ...draft, age: Number(draft.age) }); setStatus(result.ok ? 'Attendee saved.' : result.error?.message ?? 'Attendee could not be saved.'); if (result.ok) { setDraft(blank); load(); } };
  const remove = async (id) => { const result = await gateway.attendees.remove(id); setStatus(result.ok ? 'Attendee removed.' : result.error?.message ?? 'Attendee could not be removed.'); if (result.ok) load(); };
  return <section className="profile-panel" aria-labelledby="attendees-heading"><p className="profile-panel__eyebrow">YOUR ATTENDEES</p><h2 id="attendees-heading">Attendees</h2>{status && <p className="profile-panel__message" role="status">{status}</p>}<ul>{attendees.map((attendee) => <li key={attendee.id}>{attendee.name} <button type="button" onClick={() => remove(attendee.id)}>Remove</button></li>)}</ul><form onSubmit={add}><label>Name<input required value={draft.name} onChange={(event) => setDraft({ ...draft, name: event.target.value })} /></label><label>Age<input required type="number" min="0" max="120" value={draft.age} onChange={(event) => setDraft({ ...draft, age: event.target.value })} /></label><label>Mobile<input required value={draft.mobile} onChange={(event) => setDraft({ ...draft, mobile: event.target.value })} /></label><label>Email<input required type="email" value={draft.email} onChange={(event) => setDraft({ ...draft, email: event.target.value })} /></label><button type="submit">Add attendee</button></form></section>;
}
