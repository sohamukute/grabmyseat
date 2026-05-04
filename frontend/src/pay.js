import { api } from './api.js';

export const rupees = (paise) => new Intl.NumberFormat('en-IN', { style: 'currency', currency: 'INR', maximumFractionDigits: 0 }).format(paise / 100);

const checkout = () => new Promise((resolve, reject) => {
  if (window.Razorpay) return resolve(window.Razorpay);
  const script = document.createElement('script');
  script.src = 'https://checkout.razorpay.com/v1/checkout.js';
  script.onload = () => resolve(window.Razorpay);
  script.onerror = () => reject(new Error('Could not load the payment window. Check your connection and try again.'));
  document.body.appendChild(script);
});

export async function pay(bookingId, title) {
  const order = await api(`/bookings/${bookingId}/order`, { method: 'POST' });
  if (!order.ok) throw new Error(order.message);
  const Razorpay = await checkout();
  return new Promise((resolve, reject) => {
    const window = new Razorpay({
      key: order.data.keyId,
      amount: order.data.amountPaise,
      currency: 'INR',
      order_id: order.data.orderId,
      name: 'GrabMySeat',
      description: title,
      prefill: { email: order.data.email ?? '' },
      theme: { color: '#b8325a' },
      handler: async (paid) => {
        const confirmed = await api(`/bookings/${bookingId}/confirm`, {
          method: 'POST',
          body: { orderId: paid.razorpay_order_id, paymentId: paid.razorpay_payment_id, signature: paid.razorpay_signature },
        });
        if (confirmed.ok) resolve(confirmed.data);
        else reject(new Error(confirmed.message));
      },
      modal: { ondismiss: () => reject(new Error('Payment window closed. Your seats stay held until the timer runs out.')) },
    });
    window.on('payment.failed', (failed) => reject(new Error(failed.error?.description ?? 'The payment did not go through. Try again.')));
    window.open();
  });
}
