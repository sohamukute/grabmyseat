import http from 'k6/http';
import { check, sleep } from 'k6';
import { Trend } from 'k6/metrics';
import { uuidv4 } from 'https://jslib.k6.io/k6-utils/1.4.0/index.js';

const BASE = __ENV.BASE || 'http://localhost:8080';
const EVENT = Number(__ENV.EVENT);
const USERS = Number(__ENV.USERS || 1000);
const turnWait = new Trend('turn_wait_seconds');

http.setResponseCallback(http.expectedStatuses(200, 201, 204, 400, 409, 429));

export const options = {
  scenarios: {
    rush: { executor: 'per-vu-iterations', vus: USERS, iterations: 1, maxDuration: '8m' },
  },
  thresholds: {
    http_req_failed: ['rate==0'],
    'http_req_duration{name:book}': ['p(95)<3000'],
    checks: ['rate==1'],
  },
};

export function setup() {
  const counts = http.get(`${BASE}/api/inventory/events/${EVENT}/counts`).json();
  const event = http.get(`${BASE}/api/inventory/events/${EVENT}`).json();
  return { areas: counts.map((area) => area.areaId), start: Date.parse(event.bookingOpensAt), room: event.waitingRoom };
}

const token = () => http.cookieJar().cookiesForURL(BASE)['XSRF-TOKEN'][0];

const post = (path, body, name) => http.post(`${BASE}/api${path}`, JSON.stringify(body), {
  headers: { 'Content-Type': 'application/json', 'X-XSRF-TOKEN': token() },
  tags: { name },
});

const request = (areas, size, splitOk = false, requestId = uuidv4()) => ({
  requestId, eventId: EVENT, areaId: areas[Math.floor(Math.random() * areas.length)], groupSize: size, splitOk,
  attendeeNames: Array.from({ length: size }, (_, i) => `guest ${i + 1}`),
});

export default function ({ areas, start, room }) {
  http.get(`${BASE}/api/inventory/venues`);
  const login = http.post(`${BASE}/api/auth/login`, { username: `k6user${__VU}`, password: 'Seats2026' },
    { headers: { 'X-XSRF-TOKEN': token() }, tags: { name: 'login' } });
  check(login, { 'logged in': (r) => r.status === 200 });
  if (room) post(`/waiting-room/${EVENT}`, {}, 'room');
  sleep(Math.max(0, (start - Date.now()) / 1000));
  if (room) {
    const waitStart = Date.now();
    let state = '';
    while (state !== 'TURN' && state !== 'SOLD_OUT' && Date.now() - waitStart < 420000) {
      sleep(1 + Math.random());
      state = http.get(`${BASE}/api/waiting-room/${EVENT}`, { tags: { name: 'room_status' } }).json('state');
    }
    turnWait.add((Date.now() - waitStart) / 1000);
    if (!check(state, { 'got a turn or told sold out': (s) => s === 'TURN' || s === 'SOLD_OUT' }) || state === 'SOLD_OUT') return;
  }

  const roll = __VU % 20;
  if (roll < 8) {
    const body = request(areas, 1 + Math.floor(Math.random() * 4));
    const res = post('/bookings', body, 'book');
    check(res, { 'booked or told why': (r) => r.status === 201 || r.status === 409 });
    if (res.status === 409 && res.json('message').startsWith('Not enough')) post('/queue', body, 'queue');
  } else if (roll < 11) {
    const body = request(areas, 1 + Math.floor(Math.random() * 3));
    const [a, b] = http.batch([
      ['POST', `${BASE}/api/bookings`, JSON.stringify(body), { headers: { 'Content-Type': 'application/json', 'X-XSRF-TOKEN': token() }, tags: { name: 'book' } }],
      ['POST', `${BASE}/api/bookings`, JSON.stringify(body), { headers: { 'Content-Type': 'application/json', 'X-XSRF-TOKEN': token() }, tags: { name: 'book' } }],
    ]);
    check([a, b], { 'double tap gives one booking': ([x, y]) => x.status !== 201 || y.status !== 201 || x.json('bookingId') === y.json('bookingId') });
  } else if (roll < 13) {
    check(post('/bookings', request(areas, 4, true), 'book'), { 'split group answered': (r) => r.status === 201 || r.status === 409 });
  } else if (roll < 15) {
    if (post('/bookings', request(areas, 3), 'book').status === 201) {
      check(post('/bookings', request(areas, 3), 'book'), { 'over limit refused': (r) => r.status === 409 });
    }
  } else if (roll < 17) {
    const res = post('/bookings', request(areas, 2), 'book');
    if (res.status === 201) {
      sleep(1 + Math.random() * 3);
      check(post(`/tickets/${res.json('seats.0.ticketId')}/cancel`, {}, 'cancel'), { 'cancelled': (r) => r.status === 204 });
    }
  } else if (roll < 18) {
    const body = request(areas, 2);
    body.attendeeNames = ['only one'];
    check(post('/bookings', body, 'book'), { 'bad names refused': (r) => r.status === 400 });
  } else {
    const res = post('/queue', request(areas, 1 + Math.floor(Math.random() * 4)), 'queue');
    check(res, { 'line answered': (r) => r.status === 201 || r.status === 409 });
    if (roll === 19 && res.status === 201 && res.json('queue')) post(`/queue/${res.json('queue.id')}/leave`, {}, 'leave');
  }
}
