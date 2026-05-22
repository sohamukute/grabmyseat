import { createSession } from '../features/auth/session.js';

/** @typedef {import('./types.js').ApiResult} ApiResult */

const errorFor = (status) => {
  if (status === 401) return { kind: 'unauthorized', message: 'Your session has expired. Please sign in again.', status };
  if (status === 409) return { kind: 'conflict', message: 'That action conflicts with the current state. Please refresh and try again.', status };
  return { kind: 'request-failed', message: 'We could not complete that request. Please try again.', status };
};

/** @param {{ baseUrl?: string, fetch?: typeof fetch, storage?: Storage }} options */
export function createGatewayClient({ baseUrl = import.meta.env?.VITE_API_URL ?? '/api', fetch: fetchImpl = globalThis.fetch, storage = globalThis.sessionStorage } = {}) {
  const session = createSession(storage);

  /** @param {string} path @param {{ method?: string, body?: unknown, signal?: AbortSignal, auth?: boolean, headers?: Record<string, string> }} [options] @returns {Promise<ApiResult>} */
  const request = async (path, { method = 'GET', body, signal, auth = true, headers: extraHeaders = {} } = {}) => {
    const headers = { Accept: 'application/json', ...extraHeaders };
    const multipart = typeof FormData !== 'undefined' && body instanceof FormData;
    if (body !== undefined && !multipart) headers['Content-Type'] = 'application/json';
    const accessToken = session.accessToken();
    if (auth && accessToken) headers.Authorization = `Bearer ${accessToken}`;
    try {
      const response = await fetchImpl(`${baseUrl}${path}`, {
        method,
        headers,
        body: body === undefined ? undefined : multipart ? body : JSON.stringify(body),
        signal,
      });
      if (!response.ok) {
        const fallback = errorFor(response.status);
        if (response.status === 401) return { ok: false, error: fallback };
        try {
          const payload = await response.json();
          return {
            ok: false,
            error: {
              ...fallback,
              message: typeof payload?.message === 'string' ? payload.message : fallback.message,
              ...(payload?.fieldErrors && typeof payload.fieldErrors === 'object' ? { fields: payload.fieldErrors } : {}),
            },
          };
        } catch {
          return { ok: false, error: fallback };
        }
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

  const auth = {
    login: async (username, password) => {
      const result = await request('/auth/login', { method: 'POST', body: { username, password }, auth: false });
      if (result.ok) session.save(result.data);
      return result;
    },
    requestOtp: (phone) => request('/auth/otp/request', { method: 'POST', body: { phone }, auth: false }),
    verifyOtp: async (phone, code) => {
      const result = await request('/auth/otp/verify', { method: 'POST', body: { phone, code }, auth: false });
      if (result.ok) session.save(result.data);
      return result;
    },
    refresh: async () => {
      const refreshToken = session.refreshToken();
      if (!refreshToken) return { ok: false, error: errorFor(401) };
      const result = await request('/auth/refresh', { method: 'POST', body: { refreshToken }, auth: false });
      if (result.ok) session.save(result.data);
      return result;
    },
    clear: session.clear,
    accessToken: session.accessToken,
    roles: session.roles,
  };
  const query = (values) => new URLSearchParams(Object.entries(values).filter(([, value]) => value != null).map(([key, value]) => [key, String(value)])).toString();
  const signal = (options) => ({ signal: options?.signal });

  return {
    request,
    session: auth,
    events: {
      list: (options) => request('/inventory/events', signal(options)),
      get: (eventId, options) => request(`/inventory/events/${eventId}`, signal(options)),
      saleAccess: (eventId, options) => request(`/inventory/events/${eventId}/sale-access`, signal(options)),
      zone: (eventId, zoneId, options) => request(`/inventory/events/${eventId}/zones/${zoneId}`, signal(options)),
      interest: (eventId, options) => request(`/inventory/events/${eventId}/interest`, { method: 'POST', ...signal(options) }),
      removeInterest: (eventId, options) => request(`/inventory/events/${eventId}/interest`, { method: 'DELETE', ...signal(options) }),
    },
    wallet: {
      balance: (options) => request('/wallet/me/balance', signal(options)),
      demoTopUp: (body, options) => request('/wallet/me/demo-topups', { method: 'POST', body, ...signal(options) }),
      credit: (body, options) => request('/wallet/admin/topups', { method: 'POST', body, ...signal(options) }),
    },
    attendees: {
      list: (options) => request('/inventory/attendees', signal(options)),
      create: (body, options) => request('/inventory/attendees', { method: 'POST', body, ...signal(options) }),
      update: (id, body, options) => request(`/inventory/attendees/${id}`, { method: 'PUT', body, ...signal(options) }),
      remove: (id, options) => request(`/inventory/attendees/${id}`, { method: 'DELETE', ...signal(options) }),
    },
    queue: {
      join: (eventId, options) => request(`/waiting-room/events/${eventId}/join`, { method: 'POST', ...signal(options) }),
      position: (eventId, token, options) => request(`/waiting-room/events/${eventId}/position?${query({ token })}`, signal(options)),
      leave: (eventId, token, options) => request(`/waiting-room/events/${eventId}/leave?${query({ token })}`, { method: 'POST', ...signal(options) }),
      permit: (eventId, token, options) => request(`/waiting-room/events/${eventId}/permit?${query({ token })}`, signal(options)),
      joinWaitlist: (eventId, zoneId, options) => request(`/waiting-room/events/${eventId}/zones/${zoneId}/waitlist`, { method: 'POST', ...signal(options) }),
      waitlistStatus: (token, options) => request(`/waiting-room/waitlist/${token}/status`, signal(options)),
      acceptWaitlist: (token, options) => request(`/waiting-room/waitlist/${token}/accept`, { method: 'POST', ...signal(options) }),
    },
    reservations: {
      create: (body, options) => request('/inventory/reservations', { method: 'POST', body, headers: options?.permitToken ? { 'X-Queue-Permit': options.permitToken } : {}, ...signal(options) }),
      get: (token, options) => request(`/inventory/reservations/${token}`, signal(options)),
      confirm: (token, options) => request(`/inventory/reservations/${token}/confirm`, { method: 'POST', ...signal(options) }),
      cancel: (token, options) => request(`/inventory/reservations/${token}/cancel`, { method: 'POST', ...signal(options) }),
    },
    saga: {
      confirm: (token, options) => request(`/saga/bookings/${token}/confirm`, { method: 'POST', ...signal(options) }),
      cancel: (token, options) => request(`/saga/bookings/${token}/cancel`, { method: 'POST', ...signal(options) }),
      status: (token, options) => request(`/saga/bookings/${token}/status`, signal(options)),
    },
    tickets: {
      mine: (options) => request('/ticketing/tickets/mine', signal(options)),
      get: (token, options) => request(`/ticketing/tickets/${token}`, signal(options)),
      regenerate: (token, options) => request(`/ticketing/tickets/${token}/regenerate`, { method: 'POST', ...signal(options) }),
      validate: (token, options) => request(`/ticketing/tickets/${token}/validate`, { method: 'POST', ...signal(options) }),
      checkIn: (token, body, options) => request(`/ticketing/tickets/${token}/check-in`, { method: 'POST', body, ...signal(options) }),
    },
    admin: {
      users: (filters = {}, options) => request(`/auth/admin/users?${query({ query: filters.query ?? '', page: filters.page ?? 0 })}`, signal(options)),
    },
    organizer: {
      events: (options) => request('/inventory/events/organizer/me', signal(options)),
      submitEvent: (body, options) => request('/inventory/events', { method: 'POST', body, ...signal(options) }),
      uploadPoster: (file, options) => {
        const body = new FormData();
        body.append('file', file);
        return request('/inventory/posters', { method: 'POST', body, ...signal(options) });
      },
      updateEvent: (eventId, body, options) => request(`/inventory/events/${eventId}`, { method: 'PUT', body, ...signal(options) }),
      staffApplications: (eventId, options) => request(`/inventory/events/${eventId}/staff`, signal(options)),
      inviteStaff: (eventId, username, options) => request(`/inventory/events/${eventId}/staff`, { method: 'POST', body: { username }, ...signal(options) }),
      approveStaffApplication: (eventId, userId, options) => request(`/inventory/events/${eventId}/staff/${userId}/approve`, { method: 'POST', ...signal(options) }),
      revokeStaff: (eventId, userId, options) => request(`/inventory/events/${eventId}/staff/${userId}/revoke`, { method: 'POST', ...signal(options) }),
    },
    staff: {
      events: (options) => request('/inventory/events', signal(options)),
      validateTicket: (token, options) => request(`/ticketing/tickets/${token}/validate`, { method: 'POST', ...signal(options) }),
      checkInTicket: (token, body, options) => request(`/ticketing/tickets/${token}/check-in`, { method: 'POST', body, ...signal(options) }),
    },
  };
}

export const gateway = typeof window === 'undefined' ? null : createGatewayClient();
