// Keystroke guard for whole-number-only fields (runtimeMinutes, billingOrder).
// Both already carry `min="0"`/`min="1"`, which stops the *form* from
// submitting an out-of-range value - but the field can still visibly hold a
// "-5" before that check ever runs, which is confusing on its own and is
// exactly the kind of thing better prevented at the input than corrected
// after a round trip to the server. Blocking the keystroke means a negative
// or fractional value can never appear in the field in the first place.
const BLOCKED_KEYS = new Set(['-', '+', 'e', 'E', '.']);

export function blockNonWholeNumberKeys(event: KeyboardEvent): void {
  if (BLOCKED_KEYS.has(event.key)) event.preventDefault();
}
