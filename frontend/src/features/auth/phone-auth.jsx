import { useEffect, useRef, useState } from 'react';

export function PhoneAuth({ onClose, onSuccess, session }) {
  const [phone, setPhone] = useState(''); const [code, setCode] = useState(''); const [requested, setRequested] = useState(false); const [mode, setMode] = useState('customer'); const [username, setUsername] = useState(''); const [password, setPassword] = useState(''); const [status, setStatus] = useState('');
  const dialogRef = useRef(null);
  useEffect(() => {
    const dialog = dialogRef.current;
    if (!dialog) return undefined;
    const backdrop = dialog.parentElement;
    const workspace = backdrop.parentElement;
    const background = [...workspace.children].filter((child) => child !== backdrop);
    background.forEach((child) => { child.inert = true; child.setAttribute('aria-hidden', 'true'); });
    const previouslyFocused = document.activeElement;
    (dialog.querySelector('input') ?? dialog.querySelector('button'))?.focus();
    const trapFocus = (event) => {
      if (event.key === 'Escape') { event.preventDefault(); onClose(); return; }
      if (event.key !== 'Tab') return;
      const focusable = [...dialog.querySelectorAll('button:not(:disabled), input:not(:disabled)')];
      if (!focusable.length) return;
      const first = focusable[0];
      const last = focusable[focusable.length - 1];
      if (event.shiftKey && document.activeElement === first) { event.preventDefault(); last.focus(); }
      else if (!event.shiftKey && document.activeElement === last) { event.preventDefault(); first.focus(); }
    };
    dialog.addEventListener('keydown', trapFocus);
    return () => {
      dialog.removeEventListener('keydown', trapFocus);
      background.forEach((child) => { child.inert = false; child.removeAttribute('aria-hidden'); });
      previouslyFocused?.focus?.();
    };
  }, [onClose]);
  const requestCode = async (event) => { event.preventDefault(); const result = await session.requestOtp(phone); setRequested(result.ok); setStatus(result.ok ? result.data.demoCode ? `Verification code sent. Demo code: ${result.data.demoCode}` : 'Verification code sent.' : result.error?.message ?? 'We could not send a verification code.'); };
  const verify = async (event) => { event.preventDefault(); const result = await session.verifyOtp(phone, code); if (result.ok) onSuccess(); else setStatus(result.error?.message ?? 'We could not verify that code.'); };
  const login = async (event) => { event.preventDefault(); const result = await session.login(username, password); if (result.ok) onSuccess(); else setStatus(result.error?.message ?? 'We could not sign you in.'); };
  const switchMode = () => { setMode((current) => current === 'customer' ? 'operator' : 'customer'); setStatus(''); setRequested(false); };
  const isCustomer = mode === 'customer';
  return <div className="dialog-backdrop"><form ref={dialogRef} className="dialog signin" role="dialog" aria-modal="true" aria-labelledby="signin-heading" onSubmit={isCustomer ? (requested ? verify : requestCode) : login}><button className="close" type="button" aria-label="Close sign in" onClick={onClose}>×</button><p>{isCustomer ? 'CUSTOMER SIGN-IN' : 'TEAM SIGN-IN'}</p><h2 id="signin-heading">{isCustomer ? 'Verify your phone' : 'Access your workspace'}</h2>{isCustomer ? <><label>Indian mobile number<input required type="tel" placeholder="+919999999999" value={phone} onChange={(event) => setPhone(event.target.value)} /></label>{requested && <label>Verification code<input required inputMode="numeric" value={code} onChange={(event) => setCode(event.target.value)} /></label>}</> : <><label>Username<input required autoComplete="username" value={username} onChange={(event) => setUsername(event.target.value)} /></label><label>Password<input required type="password" autoComplete="current-password" value={password} onChange={(event) => setPassword(event.target.value)} /></label></>}{status && <p role="status">{status}</p>}<button type="submit">{isCustomer ? (requested ? 'Verify and continue' : 'Send code') : 'Sign in'}</button><button type="button" className="text-button" onClick={switchMode}>{isCustomer ? 'Admin, organizer, or staff?' : 'Use mobile verification'}</button></form></div>;
}
