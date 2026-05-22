import React, { useEffect, useMemo, useRef, useState } from 'react';
import * as ReactDOM from 'react-dom/client';
import { ChevronLeft, ChevronRight, MapPin, Pause, Play, Search } from 'lucide-react';
import { gateway } from './api/client.js';
import { BookingShell } from './features/booking/booking-shell.jsx';
import { PhoneAuth } from './features/auth/phone-auth.jsx';
import { Workspace } from './features/profile/workspace.jsx';
import { signOutWorkspace } from './features/profile/workspace-state.js';
import { canAutoAdvance, cancelFeaturedMotion, displayedFeatured, nextFeaturedIndex, settleFeaturedMotion, slotFor } from './catalogue/carousel.js';
import './style.css';

const date = (value) => new Intl.DateTimeFormat('en-IN', { weekday: 'short', day: 'numeric', month: 'short' }).format(new Date(value));
const price = (event) => Math.min(...event.zones.map((zone) => Number(zone.price)));

function EventCard({ event, onOpen, featured = false }) {
  return <button className={featured ? 'featured-card' : 'event-card'} onClick={() => onOpen(event)}>
    <img src={event.artworkUrl} alt={event.name} onError={(element) => { element.currentTarget.style.display = 'none'; }} />
    <span className="cover-shade" />
    <span className="cover-copy">{featured && <small>FEATURED · ORGANISER PICK</small>}<b>{event.name}</b><em>{date(event.startsAt)} · From ₹{price(event).toLocaleString('en-IN')}</em></span>
  </button>;
}



function App() {
  const [events, setEvents] = useState([]); const [query, setQuery] = useState(''); const [category, setCategory] = useState('All'); const [active, setActive] = useState(0); const [paused, setPaused] = useState(false); const [selected, setSelected] = useState(null); const [error, setError] = useState(''); const [signInOpen, setSignInOpen] = useState(false); const [workspaceOpen, setWorkspaceOpen] = useState(false); const [authenticated, setAuthenticated] = useState(Boolean(gateway.session.accessToken())); const [reducedMotion, setReducedMotion] = useState(() => window.matchMedia('(prefers-reduced-motion: reduce)').matches); const [transition, setTransition] = useState(null); const [entering, setEntering] = useState(null); const transitionRef = useRef(null); const enteringRef = useRef(null);
  useEffect(() => { const media = window.matchMedia('(prefers-reduced-motion: reduce)'); const update = () => setReducedMotion(media.matches); media.addEventListener('change', update); return () => media.removeEventListener('change', update); }, []);
  useEffect(() => { const controller = new AbortController(); gateway.events.list({ signal: controller.signal }).then((result) => { if (result.ok) setEvents(result.data); else if (result.error.kind !== 'aborted') setError(result.error.message); }); return () => controller.abort(); }, []);
  const featured = events.filter((event) => event.featuredPlacement).slice(0, 7);
  const categories = ['All', ...new Set(events.map((event) => event.category).filter(Boolean))];
  const catalogue = useMemo(() => events.filter((event) => !featured.some((item) => item.id === event.id)).filter((event) => category === 'All' || event.category === category).filter((event) => `${event.name} ${event.venue}`.toLowerCase().includes(query.toLowerCase())), [category, events, featured, query]);
  const advance = (direction) => { if (featured.length < 2 || transitionRef.current || enteringRef.current) return; if (reducedMotion) { setActive((current) => nextFeaturedIndex(current, direction, featured.length)); return; } transitionRef.current = direction; setTransition(direction); };
  useEffect(() => { if (!canAutoAdvance({ paused, reducedMotion, length: featured.length, eventSelected: Boolean(selected) })) return; const id = setInterval(() => advance(1), 3000); return () => clearInterval(id); }, [paused, reducedMotion, featured.length, selected]);
  const completeTransition = () => { const direction = transitionRef.current; if (!direction) return; transitionRef.current = null; enteringRef.current = direction; setActive((current) => nextFeaturedIndex(current, direction, featured.length)); setTransition(null); setEntering(direction); };
  const completeEntrance = () => { enteringRef.current = null; setEntering(null); };
  const settleMotion = () => { const settled = settleFeaturedMotion(active, transitionRef.current, enteringRef.current, featured.length); transitionRef.current = null; enteringRef.current = null; setActive(settled.active); setTransition(settled.transition); setEntering(settled.entering); };
  const cancelMotion = () => { const cancelled = cancelFeaturedMotion(active); transitionRef.current = null; enteringRef.current = null; setActive(cancelled.active); setTransition(cancelled.transition); setEntering(cancelled.entering); };
  const onAnimationCancel = (animation) => { if (animation.target === animation.currentTarget) cancelMotion(); };
  const openEvent = (event) => { if (authenticated) setSelected(event); else setSignInOpen(true); };
  const openFeaturedEvent = (event) => { cancelMotion(); openEvent(event); };
  const signOut = () => { gateway.session.clear(); const state = signOutWorkspace(); setAuthenticated(state.authenticated); setWorkspaceOpen(state.workspaceOpen); };
  useEffect(() => { if (reducedMotion && (transitionRef.current || enteringRef.current)) settleMotion(); }, [reducedMotion]);
  const visibleFeatured = displayedFeatured(featured);
  if (selected) return <BookingShell event={selected} authenticated={authenticated} onBack={() => setSelected(null)} />;
  if (workspaceOpen) return <main className="app-shell workspace-page"><header><b>GRABMYSEAT</b><span><MapPin size={18} /> India <button className="account-button" onClick={() => setWorkspaceOpen(false)}>Back to events</button><button className="account-button" onClick={signOut}>Sign out</button></span></header><Workspace authenticated={authenticated} roles={gateway.session.roles()} /></main>;
  return <main className="app-shell"><header><b>GRABMYSEAT</b><label><Search size={19} /><input value={query} onChange={(event) => setQuery(event.target.value)} placeholder="Search artists, events or venues" aria-label="Search artists, events or venues" /></label><span><MapPin size={18} /> India {authenticated && <button className="account-button" onClick={() => setWorkspaceOpen((open) => !open)}>{workspaceOpen ? 'Close workspace' : 'Workspace'}</button>}<button className="account-button" onClick={() => authenticated ? gateway.session.clear() || setAuthenticated(false) : setSignInOpen(true)}>{authenticated ? 'Sign out' : 'Sign in'}</button></span></header>{workspaceOpen && <Workspace authenticated={authenticated} roles={gateway.session.roles()} />}<section className="intro"><p>LIVE EVENTS ACROSS INDIA</p><h1>There is always<br />something next.</h1></section>{error ? <p className="status">{error}</p> : <><section className="feature"><div className="deck">{visibleFeatured.map((event, index) => { const cardIndex = index; const classes = ['deck-item', `slot-${slotFor(cardIndex, active, featured.length)}`, transition === 1 && cardIndex === active ? 'exit-left' : '', transition === -1 && cardIndex === active ? 'exit-right' : '', entering === 1 && cardIndex === active ? 'enter-right' : '', entering === -1 && cardIndex === active ? 'enter-left' : ''].filter(Boolean).join(' '); return <div className={classes} key={event.id} onAnimationEnd={transition ? completeTransition : completeEntrance} onAnimationCancel={onAnimationCancel}><EventCard event={event} featured onOpen={openFeaturedEvent} /></div>; })}</div><div className="feature-controls"><button onClick={() => advance(-1)} aria-label="Previous featured event">Previous featured event</button><button onClick={() => setPaused((current) => !current)}>{paused ? 'Play' : 'Pause'} featured events</button><button onClick={() => advance(1)} aria-label="Next featured event">Next featured event</button></div></section><section className="catalogue"><div className="catalogue__top"><h2>All events</h2><span>{catalogue.length} listings</span></div><div className="category-row">{categories.map((item) => <button key={item} className={category === item ? 'active' : ''} onClick={() => setCategory(item)}>{item}</button>)}</div><div className="event-grid">{catalogue.map((event) => <EventCard key={event.id} event={event} onOpen={openEvent} />)}</div></section></>}{signInOpen && <PhoneAuth onClose={() => setSignInOpen(false)} onSuccess={() => { setAuthenticated(true); setSignInOpen(false); }} session={gateway.session} />}</main>;
}
ReactDOM.createRoot(document.getElementById('root')).render(<App />);
