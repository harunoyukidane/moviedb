import { writable } from 'svelte/store';

/**
 * In-app back navigation (V2.8-06).
 *
 * `BackLink` returns the user to the page they actually came from — a filtered
 * list, the alphabet page they were on, or the record they clicked a credit
 * from — rather than a fixed "all movies"/"all people" destination, which the
 * top-level nav already provides.
 *
 * This is a stack, not a single "previous page" slot. A one-slot pointer
 * ping-pongs: going back from A to B immediately records A as B's previous
 * page, so back on B returns to A and the user can never walk further out
 * than one step. Going back has to *pop*.
 *
 * Filters, cluster/list view, offset and the alphabet jump all live in the
 * query string and are navigated with plain links, so storing
 * `pathname + search` is what keeps "back" landing on the state the user
 * left rather than a reset list.
 *
 * Browser-only: `afterNavigate` does not run during SSR, so the module-level
 * stack is never written on the server and cannot leak between requests.
 * `BackLink` renders its fallback into the SSR output and upgrades as soon as
 * the first client navigation is recorded.
 */

/** A long browsing session must not grow this without bound. */
const MAX_DEPTH = 50;

let stack: string[] = [];

/** Top of the stack: where `BackLink` points, or null to use its fallback. */
export const previousPageUrl = writable<string | null>(null);

const keyFor = (url: URL): string => url.pathname + url.search;

const pathOf = (entry: string): string => {
  const q = entry.indexOf('?');
  return q === -1 ? entry : entry.slice(0, q);
};

const top = (): string | null => (stack.length > 0 ? stack[stack.length - 1] : null);

/**
 * A form page you have just left is not somewhere to go "back" to: after
 * creating a person, back would otherwise land on the blank "new person" form
 * you just submitted. Arriving at one still records the page you came *from*,
 * so the form's own back link is unaffected - this only stops the form itself
 * becoming a destination.
 */
const isFormPage = (path: string): boolean => path.endsWith('/new') || path.endsWith('/edit');

/**
 * Record one completed client-side navigation. Called from `afterNavigate` in
 * the root layout; [from] is null on the first load of a session (direct link,
 * bookmark, hard refresh), which correctly leaves nothing to go back to.
 */
export function recordNavigation(from: URL | null | undefined, to: URL | null | undefined): void {
  if (!from || !to) return;
  const fromKey = keyFor(from);
  const toKey = keyFor(to);
  // A reload or an `invalidate()` is not a navigation and must not move the stack.
  if (fromKey === toKey) return;

  if (toKey === top()) {
    // Returning to exactly where we came from — via BackLink, the browser's own
    // back button, or a link that happens to lead back. Pop it instead of
    // recording the page being left, which is what makes repeated backs walk
    // outwards instead of oscillating between two pages.
    stack.pop();
  } else if (isFormPage(from.pathname)) {
    // Leave the stack untouched: the form drops out, and whatever the user was
    // on before it stays the back destination.
  } else if (top() !== null && from.pathname === pathOf(top() as string)) {
    // Same page, different query string: filtering, paging, switching
    // cluster/list view, alphabet jump, or typing in search. These are one
    // destination from the user's point of view, so keep only the most recent
    // — and keep its params, so back lands on the state they actually left.
    stack[stack.length - 1] = fromKey;
  } else {
    stack.push(fromKey);
    if (stack.length > MAX_DEPTH) stack = stack.slice(stack.length - MAX_DEPTH);
  }

  previousPageUrl.set(top());
}

/** Test-only: start a case from a fresh session. */
export function resetNavigationHistory(): void {
  stack = [];
  previousPageUrl.set(null);
}
