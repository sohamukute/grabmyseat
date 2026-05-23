import { useCallback, useEffect, useRef, useState } from 'react';
import { gateway } from '../../api/client.js';

/** @typedef {import('../../api/types.js').AdminUserPage} AdminUserPage */
/** @typedef {import('../../api/types.js').AdminUserSummary} AdminUserSummary */
/** @typedef {{ users(filters?: { query?: string, page?: number }, options?: { signal?: AbortSignal }): Promise<import('../../api/types.js').ApiResult<AdminUserPage>> }} UserDirectoryGateway */

const emptyPage = { content: [], number: 0, totalPages: 0, totalElements: 0 };
const stateFor = (result) => result?.ok
  ? { phase: result.data.content.length ? 'ready' : 'empty', page: result.data, message: '' }
  : { phase: 'error', page: emptyPage, message: result?.error?.message ?? 'The user directory is unavailable right now.' };

/**
 * @param {{ client?: UserDirectoryGateway, selectedUserId?: number | null, onSelect?: (user: AdminUserSummary | null) => void, initialResult?: import('../../api/types.js').ApiResult<AdminUserPage> }} props
 */
export function UserDirectory({ client = gateway?.admin, selectedUserId = null, onSelect = () => {}, initialResult }) {
  const [query, setQuery] = useState('');
  const [state, setState] = useState(() => initialResult
    ? stateFor(initialResult)
    : { phase: 'loading', page: emptyPage, message: 'Loading users…' });
  const appliedQuery = useRef('');
  const requestSequence = useRef(0);

  const load = useCallback(async (search, page, signal) => {
    const requestId = ++requestSequence.current;
    setState((current) => ({ ...current, phase: 'loading', message: 'Loading users…' }));
    const result = await client.users({ query: search, page }, { signal });
    if (requestId !== requestSequence.current || signal?.aborted || result?.error?.kind === 'aborted') return;
    setState(stateFor(result));
  }, [client]);

  useEffect(() => {
    const controller = new AbortController();
    load('', 0, controller.signal);
    return () => {
      controller.abort();
      requestSequence.current += 1;
    };
  }, [load]);

  const search = (event) => {
    event.preventDefault();
    appliedQuery.current = query;
    onSelect(null);
    load(appliedQuery.current, 0);
  };

  const changePage = (nextPage) => {
    onSelect(null);
    load(appliedQuery.current, nextPage);
  };

  const page = state.page;
  const status = state.phase === 'loading' ? 'Loading users…'
    : state.phase === 'empty' ? 'No users match this search.'
      : state.phase === 'error' ? state.message : '';

  return <section className="admin-directory" aria-labelledby="admin-directory-heading">
    <h2 id="admin-directory-heading">User directory</h2>
    <form className="admin-user-search" role="search" onSubmit={search}>
      <label htmlFor="admin-user-query">Search users</label>
      <div>
        <input id="admin-user-query" type="search" value={query} onChange={(event) => setQuery(event.target.value)} placeholder="Name, phone or email" />
        <button type="submit">Search</button>
      </div>
    </form>

    {status && <p className="profile-panel__message" role={state.phase === 'error' ? 'alert' : 'status'}>{status}</p>}
    {(state.phase === 'ready' || state.phase === 'empty') && <div className="admin-user-table-wrap">
      <table className="admin-user-table">
        <caption>{page.totalElements} user{page.totalElements === 1 ? '' : 's'}</caption>
        <thead><tr><th scope="col">User</th><th scope="col">Contact</th><th scope="col">Roles</th><th scope="col"><span className="visually-hidden">Select</span></th></tr></thead>
        <tbody>{page.content.map((user) => <tr key={user.id} className={selectedUserId === user.id ? 'selected' : ''}>
          <th scope="row">{user.displayName}</th>
          <td><span>{user.phone || 'No phone'}</span><small>{user.email || 'No email'}</small></td>
          <td>{user.roles.map((role) => <span className="admin-role" key={role}>{role.replace('ROLE_', '')}</span>)}</td>
          <td><button type="button" aria-label={`Select ${user.displayName} for wallet credit`} aria-pressed={selectedUserId === user.id} onClick={() => onSelect(user)}>Select</button></td>
        </tr>)}</tbody>
      </table>
      <nav className="admin-pagination" aria-label="User directory pages">
        <button type="button" disabled={page.number === 0 || state.phase === 'loading'} onClick={() => changePage(page.number - 1)}>Previous</button>
        <span>Page {page.totalPages ? page.number + 1 : 0} of {page.totalPages}</span>
        <button type="button" disabled={page.number + 1 >= page.totalPages || state.phase === 'loading'} onClick={() => changePage(page.number + 1)}>Next</button>
      </nav>
    </div>}
  </section>;
}
