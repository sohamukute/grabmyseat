import { useEffect, useState } from 'react';
import { createRoot } from 'react-dom/client';
import { api } from './api.js';
import { Cities, City } from './screens/Cities.jsx';
import { EventPage } from './screens/EventPage.jsx';
import { Tickets } from './screens/Tickets.jsx';
import { Organizer, OrganizerEvent } from './screens/Organizer.jsx';
import { Staff } from './screens/Staff.jsx';
import { SignIn } from './screens/SignIn.jsx';
import { Footer } from './screens/Footer.jsx';
import { Forgot, Reset, Verify } from './screens/Password.jsx';
import './style.css';

const parse = () => window.location.hash.replace(/^#\/?/, '').split('/').map(decodeURIComponent);

function App() {
  const [route, setRoute] = useState(parse);
  const [user, setUser] = useState(undefined);
  const [hidden, setHidden] = useState(false);
  const [resent, setResent] = useState('');

  useEffect(() => {
    let last = window.scrollY;
    const onScroll = () => {
      const y = window.scrollY;
      setHidden(y > 120 && y > last);
      last = y;
    };
    window.addEventListener('scroll', onScroll, { passive: true });
    return () => window.removeEventListener('scroll', onScroll);
  }, []);

  useEffect(() => {
    const onChange = () => { setRoute(parse()); window.scrollTo(0, 0); };
    window.addEventListener('hashchange', onChange);
    return () => window.removeEventListener('hashchange', onChange);
  }, []);

  useEffect(() => {
    api('/auth/me').then((response) => setUser(response.ok ? response.data : null));
  }, []);

  const signOut = async () => {
    await api('/auth/logout', { method: 'POST' });
    setUser(null);
    window.location.hash = '#/';
  };

  if (user === undefined) return null;

  const organizer = user?.roles.includes('ROLE_ORGANIZER');
  const [page, id] = route;
  const needsUser = ['tickets', 'organizer', 'staff'].includes(page);

  let screen;
  if (needsUser && !user) screen = <SignIn onSignedIn={setUser} />;
  else if (page === 'signin') screen = <SignIn onSignedIn={setUser} />;
  else if (page === 'forgot') screen = <Forgot />;
  else if (page === 'reset') screen = <Reset token={id} />;
  else if (page === 'verify') screen = <Verify token={id} onConfirmed={() => api('/auth/me').then((response) => response.ok && setUser(response.data))} />;
  else if (page === 'city') screen = <City city={id} />;
  else if (page === 'event') screen = <EventPage eventId={Number(id)} user={user} />;
  else if (page === 'tickets') screen = <Tickets />;
  else if (page === 'organizer' && id) screen = <OrganizerEvent eventId={Number(id)} />;
  else if (page === 'organizer') screen = <Organizer organizer={organizer} />;
  else if (page === 'staff') screen = <Staff />;
  else screen = <Cities />;

  if (page === 'staff' && user) return screen;

  return (
    <>
      <header className="nav" data-hidden={hidden}>
        <a className="nav-mark" href="#/" aria-label="GrabMySeat home">
          <svg viewBox="0 0 28 20" aria-hidden="true"><rect x="1" y="3" width="11" height="11" rx="3" /><rect x="16" y="3" width="11" height="11" rx="3" /><path d="M1 18h26" /></svg>
          <span>GrabMySeat</span>
        </a>
        <nav className="nav-links">
          <a href="#/" aria-current={!page || page === 'city' || page === 'event' ? 'page' : undefined}>Shows</a>
          {user && <a href="#/tickets" aria-current={page === 'tickets' ? 'page' : undefined}>My tickets</a>}
          {organizer && <a href="#/organizer" aria-current={page === 'organizer' ? 'page' : undefined}>Organizer</a>}
          {user && <a href="#/staff">Gate</a>}
        </nav>
        {user
          ? <button className="nav-user" onClick={signOut} title="Sign out"><span aria-hidden="true">{user.username[0].toUpperCase()}</span>Sign out</button>
          : <a className="nav-cta" href="#/signin">Sign in</a>}
      </header>
      {user && !user.emailVerified && page !== 'verify' && (
        <div className="verify-bar" role="status">
          <span>Confirm your email {user.email && <b>{user.email}</b>} to book seats. Use the link we sent you.</span>
          {resent
            ? <span className="quiet">{resent}</span>
            : <button className="link" onClick={async () => {
                const response = await api('/auth/verify/resend', { method: 'POST' });
                setResent(response.ok ? 'Sent. Check your inbox.' : response.message);
              }}>Send the link again</button>}
        </div>
      )}
      <main className="page">{screen}</main>
      <Footer full={!page || page === 'city'} />
    </>
  );
}

createRoot(document.getElementById('root')).render(<App />);
