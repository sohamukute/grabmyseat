import { useEffect, useRef, useState } from 'react';
import { api, when } from '../api.js';

export function Staff() {
  const [events, setEvents] = useState(null);
  const [eventId, setEventId] = useState(null);
  const [code, setCode] = useState('');
  const [result, setResult] = useState(null);
  const [busy, setBusy] = useState(false);
  const input = useRef(null);
  const next = useRef(null);

  useEffect(() => {
    api('/checkin/events').then((response) => setEvents(response.ok ? response.data : []));
  }, []);

  useEffect(() => {
    if (result) next.current?.focus();
    else input.current?.focus();
  }, [result, eventId]);

  if (!events) return null;
  const event = events.find((e) => e.id === eventId);

  if (!event) {
    return (
      <main className="gate pick">
        <a className="back" href="#/">Leave the gate</a>
        <h1 className="page-title">Which door are you on?</h1>
        {events.length === 0 && <p className="intro">You are not on the staff list for any event. Ask the organizer to add you.</p>}
        <ul className="rows">
          {events.map((e) => (
            <li key={e.id}><button className="row-button" onClick={() => setEventId(e.id)}><strong>{e.title}</strong><span>{e.city}, {when(e.startsAt)}</span></button></li>
          ))}
        </ul>
      </main>
    );
  }

  const check = async (submit) => {
    submit.preventDefault();
    setBusy(true);
    const response = await api('/checkin', { method: 'POST', body: { eventId, code } });
    setBusy(false);
    setResult(response.ok ? response.data : { admitted: false, message: response.message });
  };

  const reset = () => { setResult(null); setCode(''); };

  if (result) {
    return (
      <main className={`gate verdict ${result.admitted ? 'go' : 'stop'}`} role="alert">
        <p className="verdict-word">{result.admitted ? 'Let them in' : 'Stop'}</p>
        {result.attendeeName && <p className="verdict-name">{result.attendeeName}{result.seat && `, seat ${result.seat}`}</p>}
        <p className="verdict-reason">{result.admitted ? 'Ticket checked in.' : result.message}</p>
        {result.checkedInAt && <p className="verdict-reason">First scanned {when(result.checkedInAt)} by {result.checkedInBy}.</p>}
        <button ref={next} className="pill light" onClick={reset}>Next ticket</button>
      </main>
    );
  }

  return (
    <main className="gate">
      <button className="link" onClick={() => setEventId(null)}>{event.title}, change door</button>
      <form className="gate-form" onSubmit={check}>
        <label htmlFor="code">Ticket code</label>
        <input id="code" ref={input} value={code} autoComplete="off" autoCapitalize="characters" spellCheck={false}
               maxLength={12} onChange={(e) => setCode(e.target.value.toUpperCase())} />
        <button className="pill" type="submit" disabled={busy || code.trim().length === 0}>Check</button>
      </form>
    </main>
  );
}
