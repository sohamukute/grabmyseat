import { useEffect, useState } from 'react';
import { api, countdown, when } from '../api.js';

export function WaitingRoom({ eventId, user, onStatus }) {
  const [status, setStatus] = useState(null);
  const [now, setNow] = useState(Date.now());
  const [error, setError] = useState('');

  useEffect(() => {
    const timer = setInterval(() => setNow(Date.now()), 1000);
    return () => clearInterval(timer);
  }, []);

  useEffect(() => {
    if (!user) return undefined;
    const load = async () => {
      const response = await api(`/waiting-room/${eventId}`);
      if (response.ok) {
        setStatus(response.data);
        onStatus(response.data);
      }
    };
    load();
    const timer = setInterval(load, 4000);
    return () => clearInterval(timer);
  }, [eventId, user]);

  const join = async () => {
    const response = await api(`/waiting-room/${eventId}`, { method: 'POST' });
    if (response.ok) {
      setError('');
      setStatus(response.data);
      onStatus(response.data);
    } else setError(response.message);
  };

  if (!user) {
    return (
      <div className="room" role="status">
        <p className="room-title">This show sells through a waiting room</p>
        <p>Sign in first so you can take a place in line when it opens.</p>
        <a className="pill light" href="#/signin">Sign in</a>
      </div>
    );
  }
  if (!status) return null;

  const roomOpens = new Date(status.roomOpensAt).getTime();
  const saleOpens = new Date(status.saleOpensAt).getTime();
  const passEnds = status.passExpiresAt ? new Date(status.passExpiresAt).getTime() : 0;

  let body;
  if (status.state === 'TURN') {
    body = <><p className="room-clock">{countdown(passEnds - now)}</p><p><b>It is your turn.</b> Book before the clock runs out. Your seats are not held until you press Book.</p></>;
  } else if (status.state === 'EXPIRED') {
    body = <><p><b>Your 10 minutes ran out.</b> You can join again, at the back of the line.</p><button type="button" className="pill light" onClick={join}>Join again</button></>;
  } else if (status.state === 'SOLD_OUT') {
    body = <><p className="room-clock">{status.position}</p><p><b>Sold out for now.</b> Every seat is taken. Seats come back when people cancel, and you keep your place in line if they do.</p></>;
  } else if (status.state === 'WAITING' && status.position) {
    body = <><p className="room-clock">{status.position}</p><p><b>Your place in line.</b> {status.ahead} {status.ahead === 1 ? 'person is' : 'people are'} ahead of you. About 200 people shop at once, so keep this page open.</p></>;
  } else if (status.state === 'WAITING') {
    body = <><p className="room-clock">{countdown(saleOpens - now)}</p><p><b>You are in.</b> When booking opens {when(status.saleOpensAt)}, everyone here gets a random place in line, so there is no need to refresh.</p></>;
  } else if (now < roomOpens) {
    body = <><p className="room-clock">{countdown(roomOpens - now)}</p><p><b>The waiting room opens {when(status.roomOpensAt)}</b>, 15 minutes before booking. Join any time before the sale starts for an equal chance.</p></>;
  } else {
    body = <><p><b>The waiting room is open.</b> Everyone who joins before booking opens gets an equal, random chance. One place per account.</p><button type="button" className="pill light" onClick={join}>Join the waiting room</button></>;
  }

  return (
    <div className="room" role="status">
      {body}
      {error && <p className="error">{error}</p>}
    </div>
  );
}
