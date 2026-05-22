import { useEffect, useMemo, useState } from 'react';
import { TicketPanel } from '../tickets/ticket.jsx';
import { hasLiveHold, recoverFromExpiredHold, requiresFreshBooking } from './booking-shell-state.js';
import { canBookDirectly, reservationBody, shouldJoinQueue } from './booking-request.js';

export const BOOKING_STEPS = Object.freeze({
  DETAIL: 'DETAIL',
  QUEUE: 'QUEUE',
  PERMIT: 'PERMIT',
  SELECT: 'SELECT',
  HOLD: 'HOLD',
  CONFIRMING: 'CONFIRMING',
  TICKET: 'TICKET',
  ERROR: 'ERROR',
});

const safeMessage = 'We could not complete your booking. Please try again.';
const expiredMessage = 'Your ticket hold expired. Please select your tickets again.';


const dataFor = async (request) => {
  const result = await request;
  if (result?.ok === false) {
    const error = new Error(result.error?.message || safeMessage);
    error.status = result.error?.status;
    error.kind = result.error?.kind;
    throw error;
  }
  return result?.ok === true ? result.data : result;
};

const permitFrom = (value) => value?.permitToken ?? value?.token ?? null;
const tokenFrom = (value) => value?.token ?? value?.reservationToken ?? null;
const isAbort = (error) => error?.name === 'AbortError' || error?.kind === 'aborted';

/**
 * @param {{ eventId: number, zone: { id: number, type: string }, quantity?: number, attendees?: Array<{id: number, name: string, age: number, mobile: string, email: string}>, saleAccess?: { saleType: string, status: string, canJoinQueue: boolean }, actions: { joinQueue: Function, queuePosition: Function, queuePermit: Function, createReservation: Function, confirmBooking: Function, bookingStatus: Function, getTicket: Function }, pollInterval?: number, renderSelection?: Function, onStateChange?: Function, onReturnHome?: Function }} props
 */
export function BookingFlow({ eventId, zone, quantity = 1, attendees = [], saleAccess, actions, pollInterval = 1500, renderSelection, onStateChange, onReturnHome }) {
  const [step, setStep] = useState(BOOKING_STEPS.DETAIL);
  const [queueToken, setQueueToken] = useState(null);
  const [permitToken, setPermitToken] = useState(null);
  const [hold, setHold] = useState(null);
  const [ticket, setTicket] = useState(null);
  const [position, setPosition] = useState(null);
  const [failure, setFailure] = useState(null);
  const [selectedAttendeeIds, setSelectedAttendeeIds] = useState([]);

  const selectedAttendees = useMemo(
    () => attendees.filter((attendee) => selectedAttendeeIds.includes(attendee.id)),
    [attendees, selectedAttendeeIds],
  );
  const transition = (next) => setStep(next);
  const fail = (retry, message = safeMessage) => {
    setFailure({ retry, message });
    transition(BOOKING_STEPS.ERROR);
  };
  const releaseHold = (token) => {
    if (token) void actions.cancelBooking?.(token).catch(() => {});
  };

  useEffect(() => { onStateChange?.({ step, hasHold: Boolean(hold) }); }, [hold, onStateChange, step]);

  const joinQueue = async () => {
    setFailure(null);
    try {
      const joined = await dataFor(actions.joinQueue(eventId));
      const token = tokenFrom(joined);
      if (!token) throw new Error(safeMessage);
      setQueueToken(token);
      setPosition(joined.position ?? null);
      transition(BOOKING_STEPS.QUEUE);
    } catch (error) {
      if (!isAbort(error)) fail(joinQueue, error.message || safeMessage);
    }
  };

  const createHold = async () => {
    if (!Number.isInteger(Number(quantity)) || Number(quantity) < 1) {
      fail(createHold, 'Choose at least one ticket before continuing.');
      return;
    }
    if (selectedAttendees.length !== Number(quantity)) {
      fail(createHold, 'Choose one saved attendee for each ticket.');
      return;
    }
    setFailure(null);
    try {
      const body = reservationBody({ eventId, zone, quantity, attendees: selectedAttendees });
      const reservation = await dataFor(actions.createReservation(body, { permitToken }));
      if (!tokenFrom(reservation)) throw new Error(safeMessage);
      setHold(reservation);
      transition(BOOKING_STEPS.HOLD);
    } catch (error) {
      if (!isAbort(error)) fail(createHold, error.message || safeMessage);
    }
  };

  const confirm = async () => {
    if (!hold) return;
    setFailure(null);
    try {
      await dataFor(actions.confirmBooking(tokenFrom(hold)));
      transition(BOOKING_STEPS.CONFIRMING);
    } catch (error) {
      if (!isAbort(error)) {
        releaseHold(tokenFrom(hold));
        fail(confirm, error.message || safeMessage);
      }
    }
  };

  useEffect(() => {
    if (step !== BOOKING_STEPS.QUEUE || !queueToken) return undefined;
    const controller = new AbortController();
    let timer;
    const poll = async () => {
      try {
        const queue = await dataFor(actions.queuePosition(eventId, queueToken, { signal: controller.signal }));
        if (queue.status === 'UNKNOWN' || Number(queue.position) < 0) throw new Error('Your queue entry is no longer available. Please join again.');
        setPosition(queue.position ?? null);
        try {
          const permit = await dataFor(actions.queuePermit(eventId, queueToken, { signal: controller.signal }));
          const token = permitFrom(permit);
          if (token) {
            setPermitToken(token);
            transition(BOOKING_STEPS.PERMIT);
            return;
          }
        } catch (error) {
          if (error.status !== 403 && !isAbort(error)) throw error;
        }
        timer = window.setTimeout(poll, pollInterval);
      } catch (error) {
        if (!isAbort(error)) fail(joinQueue, error.message || safeMessage);
      }
    };
    poll();
    return () => { controller.abort(); window.clearTimeout(timer); };
  }, [actions, eventId, pollInterval, queueToken, step]);

  useEffect(() => {
    if (step !== BOOKING_STEPS.CONFIRMING || !hold) return undefined;
    const controller = new AbortController();
    let timer;
    const poll = async () => {
      try {
        const status = await dataFor(actions.bookingStatus(tokenFrom(hold), { signal: controller.signal }));
        if (status.status === 'CONFIRMED') {
          const issuedTicket = await dataFor(actions.getTicket(tokenFrom(hold), { signal: controller.signal }));
          setTicket(issuedTicket);
          transition(BOOKING_STEPS.TICKET);
          return;
        }
        if (requiresFreshBooking(status.status)) {
          const retry = recoverFromExpiredHold(setHold, setPermitToken, setQueueToken, joinQueue);
          fail(retry, 'Your booking could not be completed. Start a new booking attempt.');
          return;
        }
        timer = window.setTimeout(poll, pollInterval);
      } catch (error) {
        if (!isAbort(error)) fail(confirm, error.message || safeMessage);
      }
    };
    poll();
    return () => { controller.abort(); window.clearTimeout(timer); };
  }, [actions, hold, pollInterval, step]);

  useEffect(() => {
    if (!hasLiveHold(hold, ticket) || !hold.expiresAt) return undefined;
    const delay = new Date(hold.expiresAt).getTime() - Date.now();
    if (!Number.isFinite(delay)) return undefined;
    const timer = window.setTimeout(() => {
      releaseHold(tokenFrom(hold));
      fail(recoverFromExpiredHold(setHold, setPermitToken, setQueueToken, joinQueue), expiredMessage);
    }, Math.max(delay, 0));
    return () => window.clearTimeout(timer);
  }, [hold, ticket]);

  if (step === BOOKING_STEPS.DETAIL) return <section aria-live="polite">{canBookDirectly(saleAccess) ? <button type="button" onClick={() => transition(BOOKING_STEPS.SELECT)}>Choose attendees</button> : shouldJoinQueue(saleAccess) ? <button type="button" onClick={joinQueue}>Join queue</button> : <p role="status">Ticket sales are not available right now.</p>}</section>;
  if (step === BOOKING_STEPS.QUEUE) return <section aria-live="polite"><p role="status">Waiting for fair-access admission{position != null ? ` (position ${position})` : ''}…</p></section>;
  if (step === BOOKING_STEPS.PERMIT) return <section><p role="status">You are admitted.</p><button type="button" onClick={() => transition(BOOKING_STEPS.SELECT)}>Select tickets</button></section>;
  if (step === BOOKING_STEPS.SELECT) return <section>{renderSelection?.({ eventId, zone, quantity, createHold, permitToken })}<fieldset><legend>Choose one saved attendee for each ticket</legend>{attendees.length === 0 ? <p role="status">Add an attendee in your profile before continuing.</p> : attendees.map((attendee) => { const selected = selectedAttendeeIds.includes(attendee.id); return <button type="button" key={attendee.id} aria-pressed={selected} onClick={() => setSelectedAttendeeIds((current) => selected ? current.filter((id) => id !== attendee.id) : current.length < Number(quantity) ? [...current, attendee.id] : current)}>{attendee.name}</button>; })}</fieldset><button type="button" disabled={selectedAttendees.length !== Number(quantity)} onClick={createHold}>Hold selected tickets</button></section>;
  if (step === BOOKING_STEPS.HOLD) return <section><p role="status">Tickets held until {new Date(hold.expiresAt).toLocaleTimeString()}.</p><button type="button" onClick={confirm}>Confirm booking</button></section>;
  if (step === BOOKING_STEPS.CONFIRMING) return <section aria-live="polite"><p role="status">Confirming your booking…</p></section>;
  if (step === BOOKING_STEPS.TICKET) return <section><p role="status">Your ticket is ready.</p><TicketPanel ticket={ticket} onReturnHome={onReturnHome} /></section>;
  return <section role="alert"><p>{failure?.message || safeMessage}</p><button type="button" onClick={failure?.retry}>Try again</button></section>;
}
