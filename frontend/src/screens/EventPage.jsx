import { useEffect, useState } from 'react';
import { api, countdown, freshRequestId, native, requestIdFor, when } from '../api.js';
import { WaitingRoom } from './Waiting.jsx';
import { pay, rupees } from '../pay.js';
import { SeatMap } from './SeatMap.jsx';
import { eventPhoto } from '../photos.js';

const clock = (date) => new Intl.DateTimeFormat(undefined, { hour: 'numeric', minute: '2-digit' }).format(date);

const stamp = (date) => date.toISOString().replace(/[-:]|\.\d+/g, '');

const hours = (minutes) => {
  const h = Math.floor(minutes / 60);
  const m = minutes % 60;
  return [h && `${h} hr`, m && `${m} min`].filter(Boolean).join(' ');
};


const lone = (seats, taken) => new Set(seats.filter((seat) => !taken.has(seat.id)).filter((seat) => [-1, 1].every((step) => {
  const next = seats.find((other) => other.rowRank === seat.rowRank && other.number === seat.number + step);
  return !next || taken.has(next.id);
})).map((seat) => seat.id));

const leavesLoneSeat = (seats, picked) => {
  const taken = new Set(seats.filter((seat) => seat.taken).map((seat) => seat.id));
  if ((seats.length - taken.size - picked.length) * 10 <= seats.length) return false;
  const before = lone(seats, taken);
  picked.forEach((id) => taken.add(id));
  return [...lone(seats, taken)].some((id) => !before.has(id));
};

const fit = (area, size) => {
  if (area.longestRun >= size) return { label: `Fits ${size} together`, kind: 'good' };
  if (area.free >= size) return { label: 'Only split seats left', kind: 'split' };
  return { label: 'Full, join the line', kind: 'full' };
};

export function EventPage({ eventId, user }) {
  const bookingKey = `grabmyseat.booking.${eventId}`;
  const queueKey = `grabmyseat.queue.${eventId}`;
  const [event, setEvent] = useState(null);
  const [missing, setMissing] = useState(false);
  const [areas, setAreas] = useState([]);
  const [areaId, setAreaId] = useState(null);
  const [seats, setSeats] = useState([]);
  const [names, setNames] = useState(['', '']);
  const [splitOk, setSplitOk] = useState(false);
  const [mode, setMode] = useState('best');
  const [turn, setTurn] = useState(null);
  const [picked, setPicked] = useState([]);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState(null);
  const [booking, setBooking] = useState(null);
  const [queue, setQueue] = useState(null);
  const [now, setNow] = useState(Date.now());

  useEffect(() => {
    api(`/inventory/events/${eventId}`).then((response) => {
      if (response.ok) setEvent(response.data);
      else setMissing(true);
    });
  }, [eventId]);

  useEffect(() => {
    const load = async () => {
      const response = await api(`/inventory/events/${eventId}/counts`);
      if (response.ok && response.data) setAreas(response.data);
    };
    load();
    const live = new EventSource(`/api/inventory/events/${eventId}/stream`);
    live.addEventListener('seats', load);
    return () => live.close();
  }, [eventId, booking?.bookingId]);

  const size = names.length;
  const recommended = areas.find((area) => area.longestRun >= size);

  useEffect(() => {
    if (!areaId && areas.length) setAreaId((recommended ?? areas[0]).areaId);
  }, [areas, areaId, recommended]);

  const selected = areas.find((area) => area.areaId === areaId);

  useEffect(() => {
    if (!areaId) return;
    api(`/inventory/events/${eventId}/areas/${areaId}/map`).then((response) => {
      if (!response.ok) return;
      setSeats(response.data);
      const free = new Set(response.data.filter((seat) => !seat.taken).map((seat) => seat.id));
      setPicked((current) => current.filter((id) => free.has(id)));
    });
  }, [eventId, areaId, selected?.version]);

  useEffect(() => setPicked([]), [areaId, names.length, mode]);

  useEffect(() => {
    if (queue?.status !== 'WAITING') return undefined;
    const timer = setInterval(async () => {
      const response = await api(`/queue/${queue.id}`);
      if (!response.ok) return;
      setQueue(response.data);
      if (response.data.bookingId) {
        const seated = await api(`/bookings/${response.data.bookingId}`);
        if (seated.ok) setBooking(seated.data);
      }
    }, 10000);
    return () => clearInterval(timer);
  }, [queue?.id, queue?.status]);

  const opensAt = event ? new Date(event.bookingOpensAt).getTime() : 0;
  const early = opensAt > now;
  const ticking = early || booking?.payStatus === 'HELD';

  useEffect(() => {
    if (!ticking) return undefined;
    const timer = setInterval(() => setNow(Date.now()), 1000);
    return () => clearInterval(timer);
  }, [ticking]);

  if (missing) return <p className="empty">This show is not open. <a href="#/">See what else is on.</a></p>;
  if (!event) return null;

  const photo = eventPhoto(event);
  const resize = (next) => setNames((current) => Array.from({ length: next }, (_, i) => current[i] ?? ''));
  const rename = (index, value) => setNames((current) => current.map((name, i) => (i === index ? value : name)));
  const picking = mode === 'pick';
  const waiting = event.waitingRoom && turn?.state !== 'TURN';
  const request = { eventId, areaId, groupSize: size, splitOk: !picking && splitOk, attendeeNames: names, seatIds: picking ? picked : null };
  const pick = (seat) => setPicked((current) => (current.includes(seat.id)
    ? current.filter((id) => id !== seat.id)
    : current.length < size ? [...current, seat.id] : current));
  const strands = picking && picked.length === size && leavesLoneSeat(seats, picked);

  const book = async (submit) => {
    submit.preventDefault();
    setBusy(true);
    setError(null);
    const response = await api('/bookings', { method: 'POST', body: { ...request, requestId: requestIdFor(bookingKey) } });
    setBusy(false);
    if (!response.ok) {
      setError({ message: response.message, canQueue: !picking && response.status === 409 && response.message.startsWith('Not enough seats') });
      return;
    }
    freshRequestId(bookingKey);
    setBooking(response.data);
  };

  const joinQueue = async () => {
    setBusy(true);
    const response = await api('/queue', { method: 'POST', body: { ...request, requestId: requestIdFor(queueKey) } });
    setBusy(false);
    if (!response.ok) {
      setError({ message: response.message, canQueue: false });
      return;
    }
    freshRequestId(queueKey);
    setError(null);
    if (response.data.booking) setBooking(response.data.booking);
    else setQueue(response.data.queue);
  };

  const leaveQueue = async () => {
    const response = await api(`/queue/${queue.id}/leave`, { method: 'POST' });
    if (response.ok) setQueue(null);
    else setError({ message: response.message, canQueue: false });
  };

  return (
    <>
      <div className="ambient" aria-hidden="true" style={{ backgroundImage: `url(${photo.small})` }} />
      <article className="show">
        <aside className="show-poster">
          <img src={photo.url} alt="" />
          <p className="photo-credit">Photo by {photo.credit}</p>
        </aside>

        <div className="show-main">
          <a className="back" href={`#/city/${encodeURIComponent(event.city)}`}>{event.city}</a>
          <p className="show-native" aria-hidden="true">{native[event.city]}</p>
          <p className="show-category">{event.category}<span>Presented by {event.presentedBy}</span></p>
          <h1 className="show-title">{event.title}</h1>
          <p className="show-tagline">{event.tagline}</p>
          <dl className="facts">
            <div><dt>When</dt><dd>{when(event.startsAt)}</dd></div>
            <div><dt>Doors</dt><dd>{clock(new Date(new Date(event.startsAt).getTime() - 30 * 60000))}</dd></div>
            <div><dt>Running time</dt><dd>{hours(event.durationMinutes)}</dd></div>
            <div><dt>Where</dt><dd>{event.venueName}, {event.city}</dd></div>
            <div><dt>Language</dt><dd>{event.language}</dd></div>
            <div><dt>Age</dt><dd>{event.ageGuidance}</dd></div>
            <div><dt>Entry</dt><dd>Up to {event.maxPerPerson} seats each</dd></div>
          </dl>

          {booking?.payStatus === 'HELD' ? (
            <section className="panel done">
              <p className="panel-kicker">Seats held for you</p>
              <h2 className="panel-title">Pay {rupees(booking.amountPaise)} to keep them.</h2>
              <ul className="seat-list">
                {booking.seats.map((seat) => <li key={seat.ticketId}><b>{seat.rowLabel}{seat.number}</b> {seat.attendeeName}</li>)}
              </ul>
              <p>{new Date(booking.heldUntil).getTime() > now
                ? <>Held for <b>{countdown(new Date(booking.heldUntil).getTime() - now)}</b>. After that they go to the next group in line.</>
                : 'Your hold ran out. If you paid, it will be confirmed or refunded automatically.'}</p>
              {error && <p className="error" role="alert">{error.message}</p>}
              <button className="pill wide" type="button" disabled={busy} onClick={async () => {
                setBusy(true);
                setError(null);
                try {
                  setBooking(await pay(booking.bookingId, event.title));
                } catch (failed) {
                  setError({ message: failed.message, canQueue: false });
                }
                setBusy(false);
              }}>{busy ? 'Opening payment' : `Pay ${rupees(booking.amountPaise)}`}</button>
              <p className="quiet">Test mode: use card 4111 1111 1111 1111, any future date and any CVV, or UPI success@razorpay.</p>
            </section>
          ) : booking ? (
            <section className="panel done">
              <p className="panel-kicker">Booked</p>
              <h2 className="panel-title">You are in, together.</h2>
              <ul className="seat-list">
                {booking.seats.map((seat) => <li key={seat.ticketId}><b>{seat.rowLabel}{seat.number}</b> {seat.attendeeName}</li>)}
              </ul>
              <SeatMap seats={seats} mine={booking.seats} />
              <div className="share">
                <a className="pill" href="#/tickets">Show my ticket codes</a>
                <a className="pill ghost" target="_blank" rel="noreferrer" href={`https://wa.me/?text=${encodeURIComponent(`${event.title}, ${when(event.startsAt)} at ${event.venueName}. Our seats: ${booking.seats.map((seat) => `${seat.rowLabel}${seat.number} ${seat.attendeeName}`).join(', ')}. Doors open 30 minutes before. ${window.location.href}`)}`}>Send to your group</a>
                <a className="pill ghost" download="show.ics" href={`data:text/calendar;charset=utf-8,${encodeURIComponent(['BEGIN:VCALENDAR', 'VERSION:2.0', 'BEGIN:VEVENT', `UID:grabmyseat-${event.id}`, `DTSTAMP:${stamp(new Date())}`, `DTSTART:${stamp(new Date(event.startsAt))}`, `DTEND:${stamp(new Date(event.endsAt))}`, `SUMMARY:${event.title}`, `LOCATION:${event.venueName}, ${event.city}`, 'BEGIN:VALARM', 'TRIGGER:-P1D', 'ACTION:DISPLAY', 'DESCRIPTION:Show tomorrow', 'END:VALARM', 'END:VEVENT', 'END:VCALENDAR'].join('\r\n'))}`}>Add to calendar</a>
              </div>
            </section>
          ) : queue ? (
            <section className="panel done">
              <p className="panel-kicker">In line for {selected?.name}</p>
              <h2 className="panel-title">You are number {queue.position}.</h2>
              <p>We hold your place for {queue.groupSize} seats. When seats come back and fit your group, they are booked for you and appear right here. This page checks every few seconds.</p>
              {queue.groupSize >= 5 && !queue.splitOk && <p className="hint">Groups of five or more rarely fit in one row. Leave and rejoin with split seating to be seated sooner.</p>}
              <button className="pill ghost wide" onClick={leaveQueue}>Leave the line</button>
            </section>
          ) : (
            <form className="flow" onSubmit={book} id="booking">
              {event.waitingRoom && <WaitingRoom eventId={eventId} user={user} onStatus={setTurn} />}
              {early && !event.waitingRoom && (
                <div className="opens" role="status">
                  <span className="opens-clock">{countdown(opensAt - now)}</span>
                  <p><b>Booking opens {when(event.bookingOpensAt)}.</b> Seats for shows like this go in minutes. Choose your group, area and names now, then press Book the moment the clock hits zero.</p>
                </div>
              )}
              <section className="step">
                <h2 className="step-title"><span>1</span>How many of you?</h2>
                <div className="sizes" role="group" aria-label="Group size">
                  {Array.from({ length: event.maxPerPerson }, (_, i) => i + 1).map((n) => (
                    <button type="button" key={n} className="size" aria-pressed={size === n} onClick={() => resize(n)}>{n}</button>
                  ))}
                </div>
              </section>

              <section className="step">
                <h2 className="step-title"><span>2</span>Where would you like to sit?</h2>
                <div className="modes" role="group" aria-label="How to choose seats">
                  <button type="button" className="chip" aria-pressed={!picking} onClick={() => setMode('best')}>Best seats together</button>
                  <button type="button" className="chip" aria-pressed={picking} onClick={() => setMode('pick')}>Pick on the map</button>
                </div>
                <ul className="area-list">
                  {areas.map((area) => {
                    const state = fit(area, size);
                    const full = 1 - area.free / area.total;
                    return (
                      <li key={area.areaId}>
                        <button type="button" className="area-card" aria-pressed={area.areaId === areaId} onClick={() => setAreaId(area.areaId)}>
                          <span className="area-top">
                            <span className="area-name">{area.name}</span>
                            {recommended?.areaId === area.areaId && <span className="tag">Best for your group</span>}
                          </span>
                          <span className="meter" aria-hidden="true"><span style={{ width: `${Math.round(full * 100)}%` }} /></span>
                          <span className="area-bottom">
                            <span>{area.pricePaise > 0 ? `${rupees(area.pricePaise)} a seat, ` : ''}{area.free} of {area.total} free</span>
                            <span className={`fit ${state.kind}`}>{state.label}</span>
                          </span>
                        </button>
                      </li>
                    );
                  })}
                </ul>
                <div className="map-panel">
                  {picking && <p className="pick-help">Tap {size} free {size === 1 ? 'seat' : 'seats'}. {picked.length} of {size} picked.</p>}
                  <SeatMap seats={seats} picked={picked} onPick={picking ? pick : undefined} />
                  {strands && <p className="hint">This leaves one empty seat on its own. Move your group by one seat so nobody has to sit alone.</p>}
                </div>
              </section>

              <section className="step">
                <h2 className="step-title"><span>3</span>Who is coming?</h2>
                {!user ? (
                  <a className="pill wide" href="#/signin">Sign in to book</a>
                ) : (
                  <div className="names">
                    {names.map((name, i) => (
                      <label key={i}><span className="visually-hidden">Guest {i + 1}</span>
                        <input required placeholder={i === 0 ? 'Your name' : `Guest ${i + 1}`} value={name} onChange={(e) => rename(i, e.target.value)} />
                      </label>
                    ))}
                    {!picking && (
                      <label className="check">
                        <input type="checkbox" checked={splitOk} onChange={(e) => setSplitOk(e.target.checked)} />
                        We are fine sitting in two groups if that is the only way
                      </label>
                    )}
                  </div>
                )}
              </section>

              {error && (
                <div className="notice" role="alert">
                  <p>{error.message}</p>
                  {error.canQueue && <button type="button" className="pill ghost" disabled={busy} onClick={joinQueue}>Join the line for {selected?.name}</button>}
                </div>
              )}

              {user && (
                <div className="book-bar">
                  <span className="book-summary">{size} {size === 1 ? 'seat' : 'seats'} in {selected?.name ?? 'an area'}</span>
                  <button className="pill" type="submit" disabled={busy || !areaId || early || waiting || (picking && (picked.length !== size || strands))}>{waiting ? 'Waiting for your turn' : early ? `Opens in ${countdown(opensAt - now)}` : busy ? 'Finding seats' : 'Book seats'}</button>
                </div>
              )}
            </form>
          )}

          <section className="about">
            <h2 className="about-title">About</h2>
            <p>{event.description}</p>
          </section>
          {event.performers.length > 0 && (
            <section className="about">
              <h2 className="about-title">Lineup</h2>
              <ul className="lineup">
                {event.performers.map((performer, i) => (
                  <li key={i}><b>{performer.name}</b><span>{performer.role}</span></li>
                ))}
              </ul>
            </section>
          )}
        </div>
      </article>
    </>
  );
}
