import { useEffect, useRef, useState } from 'react';
import { gateway } from '../../api/client.js';
import { AttendeesPanel } from './attendees.jsx';
import { TicketsPanel } from './tickets.jsx';
import { formatIndianCurrency } from './format.js';
import './profile.css';

const defaultLoadBalance = (options) => gateway.wallet.balance(options);
const unavailableMessage = 'Your wallet balance is unavailable right now. Please try again shortly.';

export function ProfilePanel({ authenticated, loadBalance = defaultLoadBalance }) {
  const [wallet, setWallet] = useState({ status: 'idle', balance: null });
  const [addMoneyOpen, setAddMoneyOpen] = useState(false);
  const [topUp, setTopUp] = useState({ amount: '', status: '' });
  const dialogRef = useRef(null);

  useEffect(() => {
    if (!authenticated) {
      setWallet({ status: 'idle', balance: null });
      return undefined;
    }

    let cancelled = false;
    const controller = new AbortController();
    setWallet({ status: 'loading', balance: null });

    loadBalance({ signal: controller.signal }).then((result) => {
      if (cancelled) return;
      if (result?.ok && Number.isFinite(Number(result.data?.balance))) {
        setWallet({ status: 'ready', balance: result.data.balance });
        return;
      }
      setWallet({
        status: 'error',
        message: result?.error?.kind === 'unauthorized'
          ? 'Your session has expired. Please sign in again.'
          : unavailableMessage,
      });
    }).catch(() => {
      if (!cancelled) setWallet({ status: 'error', message: unavailableMessage });
    });

    return () => {
      cancelled = true;
      controller.abort();
    };
  }, [authenticated, loadBalance]);

  useEffect(() => {
    if (!addMoneyOpen) return undefined;
    const dialog = dialogRef.current;
    if (!dialog.open) dialog.showModal();
    return () => {
      if (dialog.open) dialog.close();
    };
  }, [addMoneyOpen]);

  const addDemoCredit = async (event) => {
    event.preventDefault();
    const result = await gateway.wallet.demoTopUp({ amount: Number(topUp.amount), idempotencyKey: `demo-topup-${crypto.randomUUID()}` });
    setTopUp({ amount: '', status: result.ok ? 'Demo credits added.' : result.error?.message ?? 'Demo top-up is unavailable right now.' });
    if (result.ok) setWallet({ status: 'idle', balance: null });
  };

  return <section className="profile-panel" aria-labelledby="profile-heading">
    <p className="profile-panel__eyebrow">YOUR PROFILE</p>
    <h2 id="profile-heading">Wallet</h2>
    {!authenticated && <p className="profile-panel__message">Sign in to view your demo wallet balance.</p>}
    {authenticated && wallet.status === 'loading' && <p className="profile-panel__message" role="status">Loading your wallet balance…</p>}
    {authenticated && wallet.status === 'error' && <p className="profile-panel__message profile-panel__message--error" role="alert">{wallet.message}</p>}
    {authenticated && wallet.status === 'ready' && <p className="profile-panel__balance" aria-label={`Wallet balance ${formatIndianCurrency(wallet.balance)}`}>{formatIndianCurrency(wallet.balance)}</p>}
    <button className="profile-panel__add-money" type="button" onClick={() => setAddMoneyOpen(true)}>Add money</button>

    <dialog ref={dialogRef} className="profile-panel__dialog" aria-labelledby="add-money-title" onClose={() => setAddMoneyOpen(false)}>
      <h3 id="add-money-title">Demo wallet credits</h3>
      <form onSubmit={addDemoCredit}><label>Amount<input required type="number" min="0.01" step="0.01" value={topUp.amount} onChange={(event) => setTopUp({ ...topUp, amount: event.target.value })} /></label><button type="submit">Add demo credits</button></form>
      {topUp.status && <p className="profile-panel__message" role="status">{topUp.status}</p>}
      <button type="button" onClick={() => dialogRef.current?.close()}>Close</button>
    </dialog>
    {authenticated && <><TicketsPanel /><AttendeesPanel /></>}
  </section>;
}
