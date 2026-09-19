import { get } from 'svelte/store';
import { beforeEach, describe, expect, it } from 'vitest';
import { previousPageUrl, recordNavigation, resetNavigationHistory } from './navigation';

const ORIGIN = 'http://localhost';
const u = (path: string) => new URL(path, ORIGIN);

/** Walk a whole browsing session, the way `afterNavigate` feeds it. */
function visit(...pages: string[]): void {
  for (let i = 1; i < pages.length; i += 1) {
    recordNavigation(u(pages[i - 1]), u(pages[i]));
  }
}

const back = () => get(previousPageUrl);

describe('in-app back stack (V2.8-06)', () => {
  beforeEach(() => resetNavigationHistory());

  it('has nothing to go back to on a direct link, bookmark or hard refresh', () => {
    recordNavigation(null, u('/movies/movie-1'));
    expect(back()).toBeNull();
  });

  it('goes back to the list the user came from', () => {
    visit('/movies', '/movies/movie-1');
    expect(back()).toBe('/movies');
  });

  it('keeps the filters the user had applied', () => {
    visit('/movies?genre=HORROR&year=1999', '/movies/movie-1');
    expect(back()).toBe('/movies?genre=HORROR&year=1999');
  });

  it('keeps list view rather than resetting to cluster view', () => {
    visit('/movies?view=list', '/movies/movie-1');
    expect(back()).toBe('/movies?view=list');
  });

  it('keeps the alphabet position on the people list', () => {
    visit('/people?letter=S&offset=40', '/people/person-1');
    expect(back()).toBe('/people?letter=S&offset=40');
  });

  it('goes back to the movie a credit was clicked from', () => {
    visit('/movies', '/movies/movie-1', '/people/person-1');
    expect(back()).toBe('/movies/movie-1');
  });

  it('goes back to the person a credit was clicked from', () => {
    visit('/people', '/people/person-1', '/movies/movie-1');
    expect(back()).toBe('/people/person-1');
  });

  it('collapses repeated filtering on one list into the state actually left', () => {
    // Each filter change is its own navigation, but they are one destination
    // from the user's point of view - back should not step through every
    // intermediate filter.
    visit('/movies', '/movies?genre=HORROR', '/movies?genre=DRAMA', '/movies/movie-1');
    expect(back()).toBe('/movies?genre=DRAMA');

    recordNavigation(u('/movies/movie-1'), u('/movies?genre=DRAMA'));
    expect(back()).toBeNull();
  });

  it('pops instead of ping-ponging: back from a detail page walks out to the list', () => {
    // The regression this replaced: a one-slot "previous page" pointer recorded
    // the edit page as the detail page's previous page the moment you went back
    // to it, so "back" led into the edit form and the list was unreachable.
    visit('/movies', '/movies/movie-1', '/movies/movie-1/edit');
    expect(back()).toBe('/movies/movie-1');

    recordNavigation(u('/movies/movie-1/edit'), u('/movies/movie-1'));
    expect(back()).toBe('/movies');

    recordNavigation(u('/movies/movie-1'), u('/movies'));
    expect(back()).toBeNull();
  });

  it('pops for the browser back button too, not just the back link', () => {
    visit('/people', '/people/person-1', '/movies/movie-1');
    recordNavigation(u('/movies/movie-1'), u('/people/person-1'));
    expect(back()).toBe('/people');
  });

  it('ignores a reload or invalidation, which is not a navigation', () => {
    visit('/movies?genre=HORROR', '/movies/movie-1');
    recordNavigation(u('/movies/movie-1'), u('/movies/movie-1'));
    expect(back()).toBe('/movies?genre=HORROR');
  });

  it('does not grow without bound over a long session', () => {
    const pages = Array.from({ length: 200 }, (_, i) => `/movies/movie-${i}`);
    visit(...pages);
    // Still points at the immediately preceding page after 200 hops.
    expect(back()).toBe('/movies/movie-198');

    // And unwinds without ever resurfacing a page it should have dropped.
    for (let i = 198; i > 150; i -= 1) {
      recordNavigation(u(`/movies/movie-${i + 1}`), u(`/movies/movie-${i}`));
    }
    expect(back()).toBe('/movies/movie-150');
  });
});
