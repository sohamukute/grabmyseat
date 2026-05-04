import { useEffect, useState } from 'react';
import { api } from '../api.js';

const rules = [
  ['At least 8 characters', (p) => p.length >= 8],
  ['One capital letter', (p) => /[A-Z]/.test(p)],
  ['One number', (p) => /[0-9]/.test(p)],
  ['One symbol, like ! or @', (p) => /[^A-Za-z0-9]/.test(p)],
];

export const strong = (password) => rules.every(([, test]) => test(password));

export function PasswordRules({ password }) {
  return (
    <ul className="rules" aria-label="Password rules">
      {rules.map(([label, test]) => <li key={label} data-ok={test(password)}>{label}</li>)}
    </ul>
  );
}

export function Forgot() {
  const [email, setEmail] = useState('');
  const [sent, setSent] = useState(false);
  const [error, setError] = useState('');

  const submit = async (event) => {
    event.preventDefault();
    const response = await api('/auth/forgot', { method: 'POST', body: { email } });
    if (response.ok) setSent(true);
    else setError(response.message);
  };

  return (
    <section className="narrow">
      <h1 className="page-title">Forgot your password?</h1>
      {sent ? (
        <p className="intro">If that email has an account, a reset link is on its way. It works for 30 minutes.</p>
      ) : (
        <form className="form" onSubmit={submit}>
          <label>Email on your account
            <input required type="email" autoComplete="email" value={email} onChange={(e) => setEmail(e.target.value)} />
          </label>
          {error && <p className="error" role="alert">{error}</p>}
          <button className="pill" type="submit">Send reset link</button>
        </form>
      )}
      <a className="link" href="#/signin">Back to sign in</a>
    </section>
  );
}

export function Reset({ token }) {
  const [password, setPassword] = useState('');
  const [again, setAgain] = useState('');
  const [done, setDone] = useState(false);
  const [error, setError] = useState('');

  const submit = async (event) => {
    event.preventDefault();
    if (password !== again) {
      setError('The two passwords do not match. Type them again.');
      return;
    }
    const response = await api('/auth/reset', { method: 'POST', body: { token, password } });
    if (response.ok) setDone(true);
    else setError(Object.values(response.fields)[0] ?? response.message);
  };

  if (done) {
    return (
      <section className="narrow">
        <h1 className="page-title">Password changed</h1>
        <p className="intro">You are signed out everywhere. Sign in with your new password.</p>
        <a className="pill" href="#/signin">Sign in</a>
      </section>
    );
  }

  return (
    <section className="narrow">
      <h1 className="page-title">Choose a new password</h1>
      <form className="form" onSubmit={submit}>
        <label>New password
          <input required type="password" autoComplete="new-password" value={password} onChange={(e) => setPassword(e.target.value)} />
        </label>
        <PasswordRules password={password} />
        <label>Type it again
          <input required type="password" autoComplete="new-password" value={again} onChange={(e) => setAgain(e.target.value)} />
        </label>
        {error && <p className="error" role="alert">{error}</p>}
        <button className="pill" type="submit" disabled={!strong(password)}>Save new password</button>
      </form>
    </section>
  );
}

export function Verify({ token, onConfirmed }) {
  const [state, setState] = useState('working');
  const [error, setError] = useState('');

  useEffect(() => {
    api('/auth/verify', { method: 'POST', body: { token } }).then((response) => {
      if (response.ok) {
        setState('done');
        onConfirmed();
      } else {
        setState('failed');
        setError(response.message);
      }
    });
  }, [token]);

  return (
    <section className="narrow">
      <h1 className="page-title">{state === 'done' ? 'Email confirmed' : state === 'failed' ? 'Link not valid' : 'Confirming'}</h1>
      {state === 'done' && <p className="intro">You can book seats now. <a href="#/">Find a show.</a></p>}
      {state === 'failed' && <p className="intro">{error}</p>}
    </section>
  );
}
