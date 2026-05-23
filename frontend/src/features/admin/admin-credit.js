const amountPattern = /^(?:0|[1-9]\d{0,14})(?:\.\d{1,4})?$/;

export function createCreditConfirmation(user, amount, createId = () => crypto.randomUUID()) {
  const normalized = String(amount).trim();
  if (!user || !amountPattern.test(normalized) || !/[1-9]/.test(normalized)) return null;
  return {
    user: { ...user, roles: [...user.roles] },
    amount: normalized,
    idempotencyKey: createId(),
  };
}

export function creditRequest(confirmation) {
  return {
    userId: confirmation.user.id,
    amount: confirmation.amount,
    idempotencyKey: confirmation.idempotencyKey,
  };
}
