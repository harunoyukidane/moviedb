// Keystroke guard for whole-number-only fields (runtimeMinutes, billingOrder).
// Both already carry `min="0"`/`min="1"`, which stops the *form* from
// submitting an out-of-range value - but the field can still visibly hold a
// "-5" before that check ever runs, which is confusing on its own and is
// exactly the kind of thing better prevented at the input than corrected
// after a round trip to the server. Blocking the keystroke means a negative
// or fractional value can never appear in the field in the first place.
const BLOCKED_KEYS = new Set(['-', '+', 'e', 'E', '.']);

export function blockNonWholeNumberKeys(event: KeyboardEvent): void {
  // Let Ctrl/Cmd/Alt shortcuts (e.g. Ctrl+E, Cmd+E) through undisturbed - this
  // guard is a convenience against stray keystrokes, not a validation
  // boundary (paste already bypasses it entirely; the server check is the
  // real defense) (V2.7-06).
  if (event.ctrlKey || event.metaKey || event.altKey) return;
  if (BLOCKED_KEYS.has(event.key)) event.preventDefault();
}
