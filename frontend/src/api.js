const fallback = (status) => {
  if (status === 401) return 'Sign in to continue.';
  if (status === 404) return 'We could not find that. Go back and pick again.';
  return 'Something went wrong. Try again.';
};

const csrfToken = () => {
  const match = document.cookie.match(/(?:^|; )XSRF-TOKEN=([^;]*)/);
  return match ? decodeURIComponent(match[1]) : null;
};

export async function api(path, { method = 'GET', body, form } = {}) {
  const headers = { Accept: 'application/json' };
  if (method !== 'GET' && csrfToken()) headers['X-XSRF-TOKEN'] = csrfToken();
  let payload;
  if (form) {
    payload = new URLSearchParams(form);
  } else if (body !== undefined) {
    headers['Content-Type'] = 'application/json';
    payload = JSON.stringify(body);
  }
  let response;
  try {
    response = await fetch(`/api${path}`, { method, headers, body: payload, credentials: 'same-origin' });
  } catch {
    return { ok: false, status: 0, message: 'Could not reach GrabMySeat. Check your connection and try again.' };
  }
  const text = await response.text();
  let data = null;
  try {
    data = text ? JSON.parse(text) : null;
  } catch {
    data = null;
  }
  if (!response.ok) {
    return { ok: false, status: response.status, message: data?.message ?? fallback(response.status), fields: data?.fieldErrors ?? {} };
  }
  return { ok: true, status: response.status, data };
}

export const when = (value) => new Intl.DateTimeFormat(undefined, {
  weekday: 'short', day: 'numeric', month: 'short', hour: 'numeric', minute: '2-digit',
}).format(new Date(value));

export const requestIdFor = (key) => {
  const saved = sessionStorage.getItem(key);
  if (saved) return saved;
  const created = crypto.randomUUID();
  sessionStorage.setItem(key, created);
  return created;
};

export const freshRequestId = (key) => {
  sessionStorage.removeItem(key);
  return requestIdFor(key);
};

export const native = { Mumbai: 'मुंबई', Delhi: 'दिल्ली', Bengaluru: 'ಬೆಂಗಳೂರು', Pune: 'पुणे' };

export const countdown = (ms) => {
  const total = Math.max(0, Math.ceil(ms / 1000));
  const h = Math.floor(total / 3600);
  const m = Math.floor((total % 3600) / 60);
  const sec = String(total % 60).padStart(2, '0');
  return h ? `${h}:${String(m).padStart(2, '0')}:${sec}` : `${m}:${sec}`;
};
