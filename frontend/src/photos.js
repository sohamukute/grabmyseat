const cdn = (id, width) => `https://cdn.cosmos.so/${id}?format=jpeg&w=${width}`;

const shows = {
  'Ray Classics Screening': { id: 'aca89c1b-fe25-4169-ac93-c0a2b0988b72', credit: 'Emma Hartvig' },
  'Monsoon Poetry Evening': { id: '230d0e71-5baa-4ad1-bdd4-13f1c8b97e4f', credit: 'David Leventi' },
  'Startup Stories Talk': { id: '12885d51-f97d-4e3f-b53b-f9bdfc39601f', credit: 'Cindy Greene, Auditorium Giovanni Arvedi' },
  'College Drama Fest': { id: 'a037b758-21d2-47a1-940c-b49ed54b26ca', credit: 'via Pinterest' },
  'Raag at Dusk': { id: '425fa8e4-2262-47ce-9dda-4222934944c0', credit: 'via Pinterest' },
  'Open Mic Comedy Night': { id: 'c0396d4c-6869-43cd-8c92-2ae63ec101e1', credit: 'Swill, The Caterpillar Club' },
  'Qawwali Under the Stars': { id: 'c9b360fb-d974-44b8-bdbc-b600bd8cd24b', credit: 'Sheeba Chadha' },
  'Bodies in Motion': { id: 'a98ef7c7-41cf-4cca-8aba-8b1896f66858', credit: 'Pina Bausch, Orpheus und Eurydike, via Pinterest' },
  'Brass and Breath': { id: '967024e7-ba6b-404f-9b5d-82a0ba18a839', credit: 'Francesco Gioia' },
  'Jazz on Church Street': { id: '377c89c9-0619-4147-9ec9-7c203060b96e', credit: 'C. Reign, Jazz Standard' },
  'Eclipse Live': { id: '0d34a704-2fe6-4e8f-9201-39fa242399b7', credit: 'Simon Leloup, Erased Studio' },
  'Colours in Motion': { id: 'ccc7c6d8-cae9-4678-98f7-71a88fd37a04', credit: 'S. Held' },
  'Stand Up in Marathi': { id: 'e80b943e-01fa-42a4-a827-fd9f7cc2ba2a', credit: 'All That Jazz Seoul, via Yatzer' },
  'Raunak Sen Live': { id: 'db4255ac-4aeb-41da-8df4-f4566a72d5fa', credit: 'Saturate Designs' },
  'Gurnoor Unplugged': { id: 'fa6ce9db-57ff-421b-800a-cc64f9eeb3b2', credit: 'Sam Cor' },
  'Kabir Mathur Acoustic Night': { id: '3868a2bd-125c-4408-8d23-58364207ca41', credit: 'Lia Hansen' },
  'Ilan Varma Piano Sessions': { id: '186aab4f-25ab-42e2-839c-a29dbc264175', credit: 'Rufus Engelhard' },
  'Tara Raghavan Strings Live': { id: '355981cc-08ec-4132-8af9-06ad6189c2f0', credit: 'Anders Heberling' },
};

const extra = [
  { id: 'ac306356-5b1c-4881-b09c-55fa0e2530d5', credit: 'Studio Raito' },
  { id: 'cb18d5cb-7b9c-4b49-9112-4a27f0aae1bd', credit: 'Yoshi Omori' },
  { id: 'a4c0831b-2e8a-4cbd-9d8b-0b3248e10e1f', credit: 'via Cadence' },
  { id: '41ab2919-f558-4cbb-b497-6da253783650', credit: 'Yulia Savchenko' },
  { id: '47c33faa-1654-4620-a242-3533407fc00d', credit: 'JZ Club Shanghai' },
];

const withUrls = (photo) => ({ ...photo, url: cdn(photo.id, 1200), small: cdn(photo.id, 400) });

export const photos = [...Object.values(shows), ...extra].map(withUrls);

export const eventPhoto = (event) => withUrls(shows[event.title] ?? extra[event.id % extra.length]);
