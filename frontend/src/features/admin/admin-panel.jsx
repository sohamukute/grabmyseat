import { useEffect, useRef, useState } from 'react';
import { gateway } from '../../api/client.js';
import { createCreditConfirmation, creditRequest } from './admin-credit.js';
import { UserDirectory } from './user-directory.jsx';

const message = (result) => result?.ok ? '' : result?.error?.message ?? 'This operation is unavailable right now.';
const money = (value) => {
  const [whole, fraction = ''] = String(value).split('.');
  return `₹${BigInt(whole).toLocaleString('en-IN')}.${fraction.padEnd(2, '0')}`;
};

export function AdminPanel() {
  const [selected, setSelected] = useState(null);
  const [amount, setAmount] = useState('');
  const [amountError, setAmountError] = useState('');
  const [confirmation, setConfirmation] = useState(null);
  const [creditStatus, setCreditStatus] = useState('');
  const [creditResult, setCreditResult] = useState(null);
  const [submitting, setSubmitting] = useState(false);
  const dialogRef = useRef(null);
  const cancelRef = useRef(null);
  const reviewRef = useRef(null);
  const submittingRef = useRef(false);
  submittingRef.current = submitting;

  useEffect(() => {
    if (!confirmation || !dialogRef.current) return undefined;
    const dialog = dialogRef.current;
    const backdrop = dialog.parentElement;
    const workspace = backdrop.parentElement;
    const background = [...workspace.children].filter((child) => child !== backdrop);
    background.forEach((child) => {
      child.inert = true;
      child.setAttribute('aria-hidden', 'true');
    });
    cancelRef.current?.focus();
    const containFocus = (event) => {
      if (event.key === 'Escape' && !submittingRef.current) {
        event.preventDefault();
        setConfirmation(null);
        setCreditStatus('');
        return;
      }
      if (event.key !== 'Tab') return;
      const focusable = [...dialog.querySelectorAll('button:not(:disabled), input:not(:disabled), [tabindex]:not([tabindex="-1"])')];
      if (!focusable.length) {
        event.preventDefault();
        return;
      }
      const first = focusable[0];
      const last = focusable[focusable.length - 1];
      if (event.shiftKey && document.activeElement === first) {
        event.preventDefault();
        last.focus();
      } else if (!event.shiftKey && document.activeElement === last) {
        event.preventDefault();
        first.focus();
      }
    };
    dialog.addEventListener('keydown', containFocus);
    return () => {
      dialog.removeEventListener('keydown', containFocus);
      background.forEach((child) => {
        child.inert = false;
        child.removeAttribute('aria-hidden');
      });
      reviewRef.current?.focus();
    };
  }, [confirmation]);

  const selectUser = (user) => {
    setSelected(user);
    setAmount('');
    setAmountError('');
    setConfirmation(null);
    setCreditResult(null);
    setCreditStatus('');
  };

  const prepareCredit = (event) => {
    event.preventDefault();
    const prepared = createCreditConfirmation(selected, amount, () => `admin-credit-${crypto.randomUUID()}`);
    if (!prepared) {
      setAmountError('Enter a positive amount with no more than four decimal places.');
      return;
    }
    setAmountError('');
    setCreditStatus('');
    setCreditResult(null);
    setConfirmation(prepared);
  };

  const closeConfirmation = () => {
    if (submitting) return;
    setConfirmation(null);
    setCreditStatus('');
  };

  const confirmCredit = async () => {
    setSubmitting(true);
    setCreditStatus('Crediting wallet…');
    const result = await gateway.wallet.credit(creditRequest(confirmation));
    setSubmitting(false);
    if (!result.ok) {
      setAmountError(result.error?.fields?.amount ?? '');
      setCreditStatus(`${message(result)} Confirm again to safely retry the same request.`);
      return;
    }
    setCreditResult({ ...result.data, displayName: confirmation.user.displayName });
    setCreditStatus('Wallet credited.');
    setConfirmation(null);
    setAmount('');
  };

  return <section className="profile-panel admin-workspace" aria-labelledby="admin-heading">
    <p className="profile-panel__eyebrow">ADMIN WORKSPACE</p>
    <h1 id="admin-heading">Account operations</h1>
    <p className="admin-workspace__intro">Find an account, review its identity, then issue a traceable wallet credit.</p>

    <div className="admin-directory-layout">
      <UserDirectory selectedUserId={selected?.id} onSelect={selectUser} />

      <aside className="admin-credit-drawer" aria-labelledby="wallet-credit-heading">
        <h2 id="wallet-credit-heading">Wallet credit</h2>
        {!selected && <p>Select a user from the directory. Account IDs are never entered by hand.</p>}
        {selected && <>
          <dl className="admin-user-detail">
            <div><dt>User</dt><dd>{selected.displayName}</dd></div>
            <div><dt>Phone</dt><dd>{selected.phone || 'Not provided'}</dd></div>
            <div><dt>Email</dt><dd>{selected.email || 'Not provided'}</dd></div>
            <div><dt>Roles</dt><dd>{selected.roles.map((role) => role.replace('ROLE_', '')).join(', ')}</dd></div>
          </dl>
          <form className="admin-credit-form" onSubmit={prepareCredit} noValidate>
            <label htmlFor="admin-credit-amount">Amount (INR) for {selected.displayName}</label>
            <input id="admin-credit-amount" required type="number" min="0.01" max="999999999999999.9999" step="0.0001" inputMode="decimal" value={amount} aria-invalid={Boolean(amountError)} aria-describedby={amountError ? 'admin-credit-amount-error' : undefined} onChange={(event) => { setAmount(event.target.value); setAmountError(''); }} />
            {amountError && <small id="admin-credit-amount-error" className="field-error" role="alert">{amountError}</small>}
            <button ref={reviewRef} type="submit">Review credit for {selected.displayName}</button>
          </form>
        </>}
        {creditStatus && <p className="profile-panel__message" role="status">{creditStatus}</p>}
        {creditResult && <section className="admin-credit-receipt" aria-labelledby="credit-result-heading">
          <p>Credit recorded</p><h3 id="credit-result-heading">{money(creditResult.amount)} to {creditResult.displayName}</h3>
          <dl><div><dt>New balance</dt><dd>{money(creditResult.balance)}</dd></div><div><dt>Ledger entry</dt><dd><code>{creditResult.ledgerEntryId}</code></dd></div></dl>
        </section>}
      </aside>
    </div>


    {confirmation && <div className="admin-confirm-backdrop" role="presentation">
      <section ref={dialogRef} className="admin-confirm-dialog" role="dialog" aria-modal="true" aria-labelledby="confirm-credit-heading">
        <p className="profile-panel__eyebrow">FINAL CHECK</p>
        <h2 id="confirm-credit-heading">Credit {money(confirmation.amount)} to {confirmation.user.displayName}?</h2>
        <p>This increases the customer balance immediately and records the administrator, amount, balances, time and reference.</p>
        {creditStatus && <p className="profile-panel__message" role="status">{creditStatus}</p>}
        <div><button ref={cancelRef} type="button" disabled={submitting} onClick={closeConfirmation}>Cancel</button><button type="button" disabled={submitting} onClick={confirmCredit}>{submitting ? 'Crediting…' : `Confirm credit to ${confirmation.user.displayName}`}</button></div>
      </section>
    </div>}
  </section>;
}
