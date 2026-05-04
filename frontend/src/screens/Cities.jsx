import { useEffect, useState } from 'react';
import { api, native, when } from '../api.js';
import { eventPhoto } from '../photos.js';

function EventCard({ event }) {
  const photo = eventPhoto(event);
  return (
    <a className="card" href={`#/event/${event.id}`}>
      <div className="card-photo">
        <img src={photo.url} alt="" loading="lazy" />
        <span className="card-category">{event.category}</span>
        <span className="card-native" aria-hidden="true">{native[event.city]}</span>
        {new Date(event.bookingOpensAt) > new Date() && <span className="card-opens">Opens {when(event.bookingOpensAt)}</span>}
      </div>
      <span className="card-date">{when(event.startsAt)}</span>
      <span className="card-title">{event.title}</span>
      <span className="card-tagline">{event.tagline}</span>
      <span className="card-meta">{event.venueName}, {event.city}</span>
    </a>
  );
}

function Wall({ events }) {
  const columns = Array.from({ length: 8 }, (_, c) => Array.from({ length: 4 }, (_, k) => events[(c * 5 + k * 3) % events.length]));
  return (
    <section className="wall" aria-label="Every show, moving">
      {columns.map((column, c) => (
        <div className="wall-col" key={c}>
          <div className="wall-track" style={{ '--speed': `${44 + c * 7}s` }}>
            {[...column, ...column].map((event, i) => (
              <a className="poster" key={i} href={`#/event/${event.id}`} tabIndex={i < column.length ? 0 : -1} aria-hidden={i >= column.length}>
                <img src={eventPhoto(event).small} alt="" loading="lazy" />
                <span><small>{event.category}, {event.city}</small><b>{event.title}</b></span>
              </a>
            ))}
          </div>
        </div>
      ))}
    </section>
  );
}

function Events({ initialCity }) {
  const [events, setEvents] = useState(null);
  const [city, setCity] = useState(initialCity ?? 'All');
  const [category, setCategory] = useState('All');
  const [query, setQuery] = useState('');

  useEffect(() => {
    api('/inventory/events').then((response) => setEvents(response.ok ? response.data : []));
  }, []);

  if (!events) return null;
  const words = query.trim().toLowerCase();
  const cities = [...new Set(events.map((event) => event.city))].sort();
  const categories = [...new Set(events.map((event) => event.category))].sort();
  const shown = events
    .filter((event) => city === 'All' || event.city === city)
    .filter((event) => category === 'All' || event.category === category)
    .filter((event) => `${event.title} ${event.city} ${event.venueName} ${event.category} ${event.tagline} ${event.presentedBy}`.toLowerCase().includes(words));

  return (
    <>
      {events.length > 0 && <Wall events={events} />}

      <section className="welcome">
        <h1 className="welcome-title">Pick a show. Bring everyone.</h1>
        <p className="welcome-sub">{events.length} shows in {cities.length} cities. Tell us how many of you are coming and we seat you together.</p>
        <label className="search">
          <span className="visually-hidden">Search events</span>
          <svg viewBox="0 0 20 20" aria-hidden="true"><circle cx="9" cy="9" r="6" /><path d="M14 14l4 4" /></svg>
          <input value={query} onChange={(e) => setQuery(e.target.value)} placeholder="Search shows, artists, halls" />
        </label>
        <div className="filters">
          <div className="chips" role="group" aria-label="Kind of show">
            {['All', ...categories].map((name) => (
              <button key={name} className="chip" aria-pressed={category === name} onClick={() => setCategory(name)}>{name === 'All' ? 'Everything' : name}</button>
            ))}
          </div>
          <div className="chips" role="group" aria-label="City">
            {['All', ...cities].map((name) => (
              <button key={name} className="chip quiet" aria-pressed={city === name} onClick={() => setCity(name)}>
                {name === 'All' ? 'Every city' : name}{native[name] && <span aria-hidden="true"> {native[name]}</span>}
              </button>
            ))}
          </div>
        </div>
        <button className="cue link" onClick={() => document.getElementById('events').scrollIntoView()}>See {shown.length === events.length ? 'every show' : `${shown.length} matching ${shown.length === 1 ? 'show' : 'shows'}`}</button>
      </section>

      <section className="listing" id="events">
        <div className="listing-head">
          <h2 className="section-title">On stage soon</h2>
          <p className="quiet">{shown.length} of {events.length} shows</p>
        </div>
        {shown.length === 0
          ? <p className="empty">No shows match that yet. Try another city or clear the search.</p>
          : <div className="cards">{shown.map((event) => <EventCard key={event.id} event={event} />)}</div>}
      </section>

      <section className="steps">
        <h2 className="section-title">How booking works</h2>
        <ol>
          <li><b>Say how many</b><span>Pick the area you like and the size of your group. You never hunt for seats.</span></li>
          <li><b>We seat you together</b><span>Your group gets the best run of seats in that area, without a single empty seat left beside you.</span></li>
          <li><b>Or hold your place</b><span>If the area is full, join its line. Returned seats go straight to the first group they fit.</span></li>
        </ol>
      </section>
    </>
  );
}

export function Cities() {
  return <Events />;
}

export function City({ city }) {
  return <Events initialCity={city} />;
}
