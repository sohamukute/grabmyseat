import { useCallback, useEffect, useRef, useState } from 'react';
import { gateway } from '../../api/client.js';
import { EventEditor } from './event-editor.jsx';
import { StaffApplications } from './staff-applications.jsx';

const stateFor = (result) => result?.ok
  ? { phase: result.data.length ? 'ready' : 'empty', events: result.data, message: '' }
  : { phase: 'error', events: [], message: result?.error?.message ?? 'Your events are unavailable right now.' };

export function OrganizerPanel() {
  const [state, setState] = useState({ phase: 'loading', events: [], message: 'Loading your events…' });
  const requestSequence = useRef(0);

  const load = useCallback(async (signal) => {
    const requestId = ++requestSequence.current;
    setState((current) => ({ ...current, phase: 'loading', message: 'Loading your events…' }));
    const result = await gateway.organizer.events({ signal });
    if (requestId !== requestSequence.current || signal?.aborted || result?.error?.kind === 'aborted') return;
    setState(stateFor(result));
  }, []);

  useEffect(() => {
    const controller = new AbortController();
    load(controller.signal);
    return () => {
      controller.abort();
      requestSequence.current += 1;
    };
  }, [load]);

  const createEvent = async (request) => {
    const result = await gateway.organizer.submitEvent(request);
    if (result.ok) load();
    return result;
  };

  return <section className="profile-panel organizer-panel" aria-labelledby="organizer-heading">
    <p className="profile-panel__eyebrow">ORGANIZER WORKSPACE</p>
    <h1 id="organizer-heading">Your events</h1>

    <section className="organizer-event-list" aria-labelledby="organizer-visible-events-heading">
      <h2 id="organizer-visible-events-heading">Visible records</h2>
      {state.phase === 'loading' && <p className="profile-panel__message" role="status">Loading your events…</p>}
      {state.phase === 'error' && <p className="profile-panel__message" role="alert">{state.message}</p>}
      {state.phase === 'empty' && <p className="profile-panel__message" role="status">No events yet. Submit your first event below.</p>}
      {state.phase === 'ready' && <ul>{state.events.map((event) => <li key={event.id}>
        <strong>{event.name}</strong>
        <span>{event.publicationStatus ?? 'Status unavailable'}</span>
        {event.rejectionReason && <small>Review note: {event.rejectionReason}</small>}
      </li>)}</ul>}
    </section>

    <div className="organizer-dashboard-grid">
      <EventEditor onCreate={createEvent} />
      <StaffApplications events={state.events} />
    </div>
  </section>;
}
