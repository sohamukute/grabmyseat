/**
 * @typedef {{ accessToken: string, refreshToken: string, tokenType: string, expiresInSeconds: number }} SessionTokens
 * @typedef {{ ok: true, status: number, data: unknown } | { ok: false, error: { kind: 'aborted' | 'unauthorized' | 'conflict' | 'invalid-response' | 'request-failed', message: string, status?: number, fields?: Record<string, string> } }} ApiResult
 */

const SESSION_KEY = 'grabmyseat.session';

const errorFor = (status) => {
  if (status === 401) return { kind: 'unauthorized', message: 'Your session has expired. Please sign in again.', status };
  if (status === 409) return { kind: 'conflict', message: 'That ticket selection is no longer available. Please choose again.', status };
  return { kind: 'request-failed', message: 'We could not complete that request. Please try again.', status };
};

const parseSession = (storage) => {
  try {
    const value = storage.getItem(SESSION_KEY);
    return value ? JSON.parse(value) : null;
  } catch {
    storage.removeItem(SESSION_KEY);
    return null;
  }
};

/** @param {{ baseUrl?: string, fetch?: typeof fetch, storage?: Storage }} options */
export function createGatewayClient({ baseUrl = import.meta.env?.VITE_API_URL ?? '/api', fetch: fetchImpl = globalThis.fetch, storage = globalThis.sessionStorage } = {}) {
  let tokens = parseSession(storage);

  const save = (nextTokens) => {
    tokens = nextTokens;
    storage.setItem(SESSION_KEY, JSON.stringify(nextTokens));
  };

  /** @param {string} path @param {{ method?: string, body?: unknown, signal?: AbortSignal, auth?: boolean }} [options] @returns {Promise<ApiResult>} */
  const request = async (path, { method = 'GET', body, signal, auth = true } = {}) => {
    const headers = { Accept: 'application/json' };
    const multipart = typeof FormData !== 'undefined' && body instanceof FormData;
    if (body !== undefined && !multipart) headers['Content-Type'] = 'application/json';
    if (auth && tokens?.accessToken) headers.Authorization = `Bearer ${tokens.accessToken}`;

    try {
      const response = await fetchImpl(`${baseUrl}${path}`, {
        method,
        headers,
        body: body === undefined ? undefined : multipart ? body : JSON.stringify(body),
        signal,
      });
      if (!response.ok) {
        const error = errorFor(response.status);
        try {
          const payload = await response.json();
          if (typeof payload?.message === 'string') error.message = payload.message;
          if (payload?.fieldErrors && typeof payload.fieldErrors === 'object') {
            error.fields = Object.fromEntries(Object.entries(payload.fieldErrors).filter(([, message]) => typeof message === 'string'));
          }
        } catch {
          // Keep the status-specific fallback when the gateway error is not JSON.
        }
        return { ok: false, error };
      }
      if (response.status === 204) return { ok: true, status: response.status, data: null };
      try {
        return { ok: true, status: response.status, data: await response.json() };
      } catch {
        return { ok: false, error: { kind: 'invalid-response', message: 'The service returned an unexpected response.', status: response.status } };
      }
    } catch (error) {
      if (error?.name === 'AbortError') return { ok: false, error: { kind: 'aborted', message: 'The request was cancelled.' } };
      return { ok: false, error: { kind: 'request-failed', message: 'We could not reach the service. Please try again.' } };
    }
  };

  const session = {
    accessToken: () => tokens?.accessToken ?? null,
    login: async (username, password) => {
      const result = await request('/auth/login', { method: 'POST', body: { username, password }, auth: false });
      if (result.ok) save(/** @type {SessionTokens} */ (result.data));
      return result;
    },
    refresh: async () => {
      if (!tokens?.refreshToken) return { ok: false, error: { kind: 'unauthorized', message: 'Your session has expired. Please sign in again.', status: 401 } };
      const result = await request('/auth/refresh', { method: 'POST', body: { refreshToken: tokens.refreshToken }, auth: false });
      if (result.ok) save(/** @type {SessionTokens} */ (result.data));
      return result;
    },
    clear: () => {
      tokens = null;
      storage.removeItem(SESSION_KEY);
    },
  };

  const query = (values) => new URLSearchParams(Object.entries(values).filter(([, value]) => value != null).map(([key, value]) => [key, String(value)])).toString();
  const withSignal = (options) => ({ signal: options?.signal });

  return {
    request,
    session,
    events: {
      list: (options) => request('/inventory/events', withSignal(options)),
      get: (eventId, options) => request(`/inventory/events/${eventId}`, withSignal(options)),
      saleAccess: (eventId, options) => request(`/inventory/events/${eventId}/sale-access`, withSignal(options)),
      zone: (eventId, zoneId, options) => request(`/inventory/events/${eventId}/zones/${zoneId}`, withSignal(options)),
    },
    organizer: {
      events: (options) => request('/inventory/events/organizer/me', withSignal(options)),
      submitEvent: (body, options) => request('/inventory/events', { method: 'POST', body, ...withSignal(options) }),
      inviteStaff: (eventId, username, options) => request(`/inventory/events/${eventId}/staff`, { method: 'POST', body: { username }, ...withSignal(options) }),
      uploadPoster: (file, options) => {
        const body = new FormData();
        body.append('file', file);
        return request('/inventory/posters', { method: 'POST', body, ...withSignal(options) });
      },
    },
    wallet: { balance: (options) => request('/wallet/me/balance', withSignal(options)) },
    waitingRoom: {
      join: (eventId, options) => request(`/waiting-room/events/${eventId}/join`, { method: 'POST', ...withSignal(options) }),
      position: (eventId, token, options) => request(`/waiting-room/events/${eventId}/position?${query({ token })}`, withSignal(options)),
      leave: (eventId, token, options) => request(`/waiting-room/events/${eventId}/leave?${query({ token })}`, { method: 'POST', ...withSignal(options) }),
      permit: (eventId, token, options) => request(`/waiting-room/events/${eventId}/permit?${query({ token })}`, withSignal(options)),
      joinWaitlist: (eventId, zoneId, options) => request(`/waiting-room/events/${eventId}/zones/${zoneId}/waitlist`, { method: 'POST', ...withSignal(options) }),
      waitlistStatus: (token, options) => request(`/waiting-room/waitlist/${token}/status`, withSignal(options)),
      acceptWaitlist: (token, options) => request(`/waiting-room/waitlist/${token}/accept`, { method: 'POST', ...withSignal(options) }),
    },
    reservations: {
      create: (reservation, options) => request('/inventory/reservations', { method: 'POST', body: reservation, ...withSignal(options) }),
      get: (token, options) => request(`/inventory/reservations/${token}`, withSignal(options)),
      confirm: (token, options) => request(`/inventory/reservations/${token}/confirm`, { method: 'POST', ...withSignal(options) }),
      cancel: (token, options) => request(`/inventory/reservations/${token}/cancel`, { method: 'POST', ...withSignal(options) }),
    },
    saga: {
      confirm: (token, options) => request(`/saga/bookings/${token}/confirm`, { method: 'POST', ...withSignal(options) }),
      cancel: (token, options) => request(`/saga/bookings/${token}/cancel`, { method: 'POST', ...withSignal(options) }),
      status: (token, options) => request(`/saga/bookings/${token}/status`, withSignal(options)),
    },
    tickets: {
      mine: (options) => request('/ticketing/tickets/mine', withSignal(options)),
      get: (token, options) => request(`/ticketing/tickets/${token}`, withSignal(options)),
      regenerate: (token, options) => request(`/ticketing/tickets/${token}/regenerate`, { method: 'POST', ...withSignal(options) }),
      validate: (token, options) => request(`/ticketing/tickets/${token}/validate`, { method: 'POST', ...withSignal(options) }),
      checkIn: (token, attendees, options) => request(`/ticketing/tickets/${token}/check-in`, { method: 'POST', body: attendees, ...withSignal(options) }),
    },
  };
}

export const gateway = typeof window === 'undefined' ? null : createGatewayClient();
