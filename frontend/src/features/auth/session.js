const SESSION_KEY = 'grabmyseat.session';

const validTokens = (value) => value && typeof value.accessToken === 'string' && typeof value.refreshToken === 'string' ? value : null;

const rolesFromToken = (accessToken) => {
  try {
    const roles = JSON.parse(atob(accessToken.split('.')[1].replace(/-/g, '+').replace(/_/g, '/'))).roles;
    return Array.isArray(roles) && roles.every((role) => typeof role === 'string') ? roles : [];
  } catch {
    return [];
  }
};

/** @param {Storage} storage */
export function createSession(storage = globalThis.sessionStorage) {
  let tokens;
  try {
    tokens = validTokens(JSON.parse(storage.getItem(SESSION_KEY) ?? 'null'));
  } catch {
    storage.removeItem(SESSION_KEY);
    tokens = null;
  }

  return {
    accessToken: () => tokens?.accessToken ?? null,
    refreshToken: () => tokens?.refreshToken ?? null,
    roles: () => rolesFromToken(tokens?.accessToken ?? ''),
    save: (nextTokens) => {
      tokens = validTokens(nextTokens);
      if (tokens) storage.setItem(SESSION_KEY, JSON.stringify(tokens));
      else storage.removeItem(SESSION_KEY);
    },
    clear: () => {
      tokens = null;
      storage.removeItem(SESSION_KEY);
    },
  };
}
