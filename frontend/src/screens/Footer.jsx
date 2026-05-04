import { useEffect, useRef, useState } from 'react';
import { api, native } from '../api.js';
import { photos } from '../photos.js';

const half = Math.ceil(photos.length / 2);

function Strip({ list, reverse }) {
  return (
    <div className="strip" data-reverse={reverse}>
      <div className="strip-track">
        {[...list, ...list].map((photo, i) => <img key={i} src={photo.small} alt="" loading="lazy" />)}
      </div>
    </div>
  );
}

export function Footer({ full }) {
  const [shows, setShows] = useState([]);
  const [shown, setShown] = useState(false);
  const mark = useRef(null);

  useEffect(() => {
    if (full) api('/inventory/events').then((response) => response.ok && setShows(response.data));
  }, [full]);

  useEffect(() => {
    if (!full) return undefined;
    const watcher = new IntersectionObserver(([entry]) => entry.isIntersecting && setShown(true), { threshold: 0.4 });
    watcher.observe(mark.current);
    return () => watcher.disconnect();
  }, [full]);

  const ticker = shows.map((event) => `${event.title} ${native[event.city] ?? event.city}`);

  return (
    <footer className="foot" data-full={full}>
      {full && <div className="foot-strips" aria-hidden="true">
        <Strip list={photos.slice(0, half)} />
        <Strip list={photos.slice(half)} reverse />
      </div>}

      {full && ticker.length > 0 && (
        <div className="ticker" aria-hidden="true">
          <div className="ticker-track">
            {[...ticker, ...ticker].map((text, i) => <span key={i}>{text}</span>)}
          </div>
        </div>
      )}

      <div className="foot-inner">
        <div className="foot-cols">
          <p className="foot-line">Good seats, side by side, for everyone you bring.</p>
          <nav aria-label="Cities">
            <b>Find a show</b>
            {Object.keys(native).map((city) => <a key={city} href={`#/city/${encodeURIComponent(city)}`}>{city}</a>)}
          </nav>
          <nav aria-label="Your seats">
            <b>Your seats</b>
            <a href="#/tickets">My tickets</a>
            <a href="#/signin">Sign in</a>
          </nav>
          <nav aria-label="Organisers">
            <b>Running a show</b>
            <a href="#/organizer">List your event</a>
            <a href="#/staff">Gate check in</a>
          </nav>
        </div>

        <p className="foot-mark" ref={mark} data-shown={shown || !full} aria-label="GrabMySeat">
          {'GrabMySeat'.split('').map((letter, i) => <span key={i} aria-hidden="true" style={{ '--i': i }}>{letter}</span>)}
        </p>

        {full && <p className="credits">Photos by {[...new Set(photos.map((photo) => photo.credit))].join(', ')}. Found on cosmos.so and credited to their creators. Used here for educational purposes only.</p>}
      </div>
    </footer>
  );
}
