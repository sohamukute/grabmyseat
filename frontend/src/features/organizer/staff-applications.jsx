import { useCallback, useEffect, useState } from 'react';
import { gateway } from '../../api/client.js';

/** @typedef {import('../../api/types.js').OrganizerEventSummary} OrganizerEventSummary */
/** @typedef {import('../../api/types.js').StaffApplicationSummary} StaffApplicationSummary */
/** @typedef {{ staffApplications(eventId: number, options?: { signal?: AbortSignal }): Promise<import('../../api/types.js').ApiResult<StaffApplicationSummary[]>>, inviteStaff(eventId: number, username: string): Promise<import('../../api/types.js').ApiResult<unknown>>, approveStaffApplication(eventId: number, userId: number): Promise<import('../../api/types.js').ApiResult<unknown>>, revokeStaff(eventId: number, userId: number): Promise<import('../../api/types.js').ApiResult<unknown>> }} StaffApplicationsGateway */

const tabs = ['applications', 'assigned'];
const stateFor = (result) => result?.ok
  ? { phase: 'ready', applications: result.data, message: '' }
  : { phase: 'error', applications: [], message: result?.error?.message ?? 'Staff applications are unavailable right now.' };
const formatEventTime = (value) => value
  ? new Intl.DateTimeFormat(undefined, { dateStyle: 'medium', timeStyle: 'short' }).format(new Date(value))
  : 'Schedule unavailable';
const stateLabel = (status) => {
  if (status === 'PENDING') return 'Awaiting decision';
  if (status === 'ACTIVE') return 'Active';
  if (status === 'REVOKED') return 'Revoked';
  return status.charAt(0) + status.slice(1).toLowerCase();
};

export const inviteUsernameError = (result) => result?.ok ? '' : result?.error?.fields?.username ?? '';

/**
 * @param {{ events: OrganizerEventSummary[], client?: StaffApplicationsGateway, onResult?: (message: string) => void, initialEventId?: string, initialTab?: 'applications' | 'assigned', initialResult?: import('../../api/types.js').ApiResult<StaffApplicationSummary[]> }} props
 */
export function StaffApplications({ events, client = gateway?.organizer, onResult = () => {}, initialEventId = '', initialTab = 'applications', initialResult }) {
  const [eventId, setEventId] = useState(initialEventId || String(events[0]?.id ?? ''));
  const [tab, setTab] = useState(initialTab);
  const [state, setState] = useState(() => initialResult
    ? stateFor(initialResult)
    : { phase: eventId ? 'loading' : 'empty', applications: [], message: eventId ? 'Loading staff applications…' : 'Choose an event to manage staff.' });
  const [notice, setNotice] = useState('');
  const [username, setUsername] = useState('');
  const [usernameError, setUsernameError] = useState('');
  const [refresh, setRefresh] = useState(0);
  const [submittingId, setSubmittingId] = useState(null);

  useEffect(() => {
    const nextEventId = events.some((event) => String(event.id) === eventId)
      ? eventId
      : String(events[0]?.id ?? '');
    if (nextEventId !== eventId) {
      setEventId(nextEventId);
      setNotice('');
      setUsernameError('');
    }
  }, [eventId, events]);

  const load = useCallback(async (selectedEventId, signal) => {
    if (!selectedEventId) {
      setState({ phase: 'empty', applications: [], message: events.length ? 'Choose an event to manage staff.' : 'No events are available for staffing yet.' });
      return;
    }
    setState((current) => ({ ...current, phase: 'loading', message: 'Loading staff applications…' }));
    const result = await client.staffApplications(Number(selectedEventId), { signal });
    if (signal?.aborted || result?.error?.kind === 'aborted') return;
    setState(stateFor(result));
  }, [client, events.length]);

  useEffect(() => {
    const controller = new AbortController();
    load(eventId, controller.signal);
    return () => controller.abort();
  }, [eventId, load, refresh]);

  const selectedEvent = events.find((event) => String(event.id) === eventId);
  const pending = state.applications.filter((application) => application.status !== 'ACTIVE');
  const assigned = state.applications.filter((application) => application.status === 'ACTIVE');

  const selectTab = (next) => {
    setTab(next);
    requestAnimationFrame(() => document.getElementById(`organizer-tab-${next}`)?.focus());
  };
  const moveFocus = (event) => {
    if (!['ArrowLeft', 'ArrowRight', 'Home', 'End'].includes(event.key)) return;
    event.preventDefault();
    const currentIndex = tabs.indexOf(tab);
    const nextIndex = event.key === 'Home' ? 0
      : event.key === 'End' ? tabs.length - 1
        : (currentIndex + (event.key === 'ArrowRight' ? 1 : -1) + tabs.length) % tabs.length;
    selectTab(tabs[nextIndex]);
  };

  const decide = async (application, decision) => {
    if (submittingId !== null || !selectedEvent) return;
    setSubmittingId(application.userId);
    const result = decision === 'approve'
      ? await client.approveStaffApplication(Number(eventId), application.userId)
      : await client.revokeStaff(Number(eventId), application.userId);
    setSubmittingId(null);
    const usernameLabel = application.username ?? 'Unknown user';
    const message = result.ok
      ? `${usernameLabel}'s application ${decision === 'approve' ? 'approved' : 'rejected'}.`
      : result.error?.message ?? 'The staff application could not be updated.';
    setNotice(message);
    onResult(message);
    if (result.ok) setRefresh((current) => current + 1);
  };

  const invite = async (event) => {
    event.preventDefault();
    if (!selectedEvent) return;
    const result = await client.inviteStaff(Number(eventId), username.trim());
    const message = result.ok ? `${username.trim()} invited to ${selectedEvent.name}.` : result.error?.message ?? 'The invitation could not be sent.';
    setUsernameError(inviteUsernameError(result));
    setNotice(message);
    onResult(message);
    if (result.ok) {
      setUsername('');
      setRefresh((current) => current + 1);
    }
  };

  const revoke = async (application) => {
    if (submittingId !== null || !selectedEvent) return;
    setSubmittingId(application.userId);
    const result = await client.revokeStaff(Number(eventId), application.userId);
    setSubmittingId(null);
    const usernameLabel = application.username ?? 'Unknown user';
    const message = result.ok
      ? `${usernameLabel}'s assignment revoked.`
      : result.error?.message ?? 'The staff assignment could not be revoked.';
    setNotice(message);
    onResult(message);
    if (result.ok) setRefresh((current) => current + 1);
  };

  return <section className="organizer-staff" aria-labelledby="organizer-staff-heading">
    <h3 id="organizer-staff-heading">Event staff</h3>
    <label htmlFor="organizer-staff-event">Event</label>
    <select id="organizer-staff-event" required value={eventId} onChange={(event) => { setEventId(event.target.value); setNotice(''); setUsernameError(''); }}>
      <option value="">Choose an event</option>
      {events.map((event) => <option key={event.id} value={event.id}>{event.name}</option>)}
    </select>

    <div className="workspace-tabs" role="tablist" aria-label="Staffing views">
      {tabs.map((name) => <button
        key={name}
        id={`organizer-tab-${name}`}
        type="button"
        role="tab"
        aria-selected={tab === name}
        aria-controls={`organizer-panel-${name}`}
        tabIndex={tab === name ? 0 : -1}
        onClick={() => selectTab(name)}
        onKeyDown={moveFocus}
      >{name === 'applications' ? 'Applications' : 'Assigned Staff'}</button>)}
    </div>

    {state.phase === 'loading' && <p className="profile-panel__message" role="status">Loading staff applications…</p>}
    {state.phase === 'error' && <p className="profile-panel__message" role="alert">{state.message}</p>}
    {notice && <p className="profile-panel__message" role="status">{notice}</p>}
    {state.phase === 'empty' && <p className="profile-panel__message" role="status">{state.message}</p>}

    {state.phase === 'ready' && <div id={`organizer-panel-${tab}`} role="tabpanel" aria-labelledby={`organizer-tab-${tab}`} tabIndex="0">
      {tab === 'applications' && <ul className="staff-application-list">
        {pending.map((application) => <li key={application.userId}>
          <div><strong>{application.username ?? 'Unknown user'}</strong><span>{stateLabel(application.status)} · Applied {formatEventTime(application.invitedAt)}</span></div>
          {application.status === 'PENDING' && <div className="staff-application-list__actions">
            <button type="button" aria-label={`Approve ${application.username ?? 'Unknown user'} for ${selectedEvent?.name}`} disabled={submittingId !== null || !selectedEvent} onClick={() => decide(application, 'approve')}>Approve</button>
            <button type="button" aria-label={`Reject ${application.username ?? 'Unknown user'} for ${selectedEvent?.name}`} disabled={submittingId !== null || !selectedEvent} onClick={() => decide(application, 'reject')}>Reject</button>
          </div>}
        </li>)}
        {!pending.length && <li className="profile-panel__message">No applications need a decision.</li>}
      </ul>}
      {tab === 'assigned' && <ul className="staff-application-list">
        {assigned.map((application) => <li key={application.userId}><div><strong>{application.username ?? 'Unknown user'}</strong><span>Assigned · {selectedEvent?.name}</span></div><div className="staff-application-list__actions"><button type="button" aria-label={`Revoke ${application.username ?? 'Unknown user'} from ${selectedEvent?.name}`} disabled={submittingId !== null || !selectedEvent} onClick={() => revoke(application)}>Revoke</button></div></li>)}
        {!assigned.length && <li className="profile-panel__message">No staff are assigned to this event.</li>}
      </ul>}
    </div>}

    <h4>Invite staff directly</h4>
    <form onSubmit={invite}>
      <label htmlFor="organizer-staff-username">Staff username</label>
      <input id="organizer-staff-username" required value={username} aria-invalid={Boolean(usernameError)} aria-describedby={usernameError ? 'organizer-staff-username-error' : undefined} onChange={(event) => { setUsername(event.target.value); setUsernameError(''); }} />
      {usernameError && <small id="organizer-staff-username-error" className="field-error" role="alert">{usernameError}</small>}
      <button type="submit" disabled={!selectedEvent || !username.trim()}>Invite staff</button>
    </form>
  </section>;
}
