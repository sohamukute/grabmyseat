import { useState } from 'react';
import { api } from '../api.js';
import { PasswordRules, strong } from './Password.jsx';

export function SignIn({ onSignedIn }) {
  const [creating, setCreating] = useState(false);
  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');
  const [email, setEmail] = useState('');
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(false);

  const submit = async (event) => {
    event.preventDefault();
    setError('');
    setBusy(true);
    if (creating) {
      const created = await api('/auth/register', { method: 'POST', body: { username, password, email } });
      if (!created.ok) {
        setBusy(false);
        setError(Object.values(created.fields)[0] ?? created.message);
        return;
      }
    }
    const login = await api('/auth/login', { method: 'POST', form: { username, password } });
    if (!login.ok) {
      setBusy(false);
      setError('Wrong username or password. Try again.');
      return;
    }
    const me = await api('/auth/me');
    setBusy(false);
    if (me.ok) {
      onSignedIn(me.data);
      if (window.location.hash.startsWith('#/signin')) window.location.hash = '#/';
    }
  };

  return (
    <section className="narrow">
      <h1 className="page-title">{creating ? 'Make an account' : 'Sign in'}</h1>
      <form className="form" onSubmit={submit}>
        <label>Username
          <input required autoComplete="username" value={username} onChange={(e) => setUsername(e.target.value)} />
        </label>
        {creating && (
          <label>Email
            <input required type="email" autoComplete="email" value={email} onChange={(e) => setEmail(e.target.value)} />
            <small className="quiet">We send your ticket codes and reminders here.</small>
          </label>
        )}
        <label>Password
          <input required type="password"
                 autoComplete={creating ? 'new-password' : 'current-password'}
                 value={password} onChange={(e) => setPassword(e.target.value)} />
        </label>
        {creating && <PasswordRules password={password} />}
        {error && <p className="error" role="alert">{error}</p>}
        <button className="pill" type="submit" disabled={busy || (creating && !strong(password))}>{creating ? 'Make account' : 'Sign in'}</button>
      </form>
      {!creating && <a className="link" href="#/forgot">Forgot your password?</a>}
      <button className="link" onClick={() => { setCreating(!creating); setError(''); }}>
        {creating ? 'I already have an account' : 'New here? Make an account'}
      </button>
    </section>
  );
}
