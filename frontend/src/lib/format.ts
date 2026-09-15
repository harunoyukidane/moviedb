// Shared display formatting helpers. Pure (no server imports) so usable on both sides.

/** Format an ISO-8601 `DateTime` (e.g. a comment's `createdAt`) for display. */
export function formatDateTime(iso: string): string {
  return new Intl.DateTimeFormat(undefined, { dateStyle: 'medium', timeStyle: 'short' }).format(
    new Date(iso)
  );
}
