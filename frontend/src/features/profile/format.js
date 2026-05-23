const rupees = new Intl.NumberFormat('en-IN', {
  style: 'currency',
  currency: 'INR',
  minimumFractionDigits: 2,
  maximumFractionDigits: 2,
});

export function formatIndianCurrency(value) {
  const amount = Number(value);
  return rupees.format(Number.isFinite(amount) ? amount : 0);
}
