// The single boundary translating stable GraphQL/media `extensions.code` values
// (§8.2) into friendly, human messages. Every screen uses this — never ad hoc
// per-component copy. Pure (no server imports) so it is usable on both sides.

export type ErrorCode =
  | 'BAD_USER_INPUT'
  | 'NOT_FOUND'
  | 'CONFLICT'
  | 'PERSON_IN_USE'
  | 'PERSON_DATE_CONFLICTS_CREDIT'
  | 'PAYLOAD_TOO_LARGE'
  | 'UNSUPPORTED_MEDIA_TYPE'
  | 'STORAGE_UNAVAILABLE'
  | 'DEPENDENCY_UNAVAILABLE'
  | 'INTERNAL_ERROR';

const MESSAGES: Record<ErrorCode, string> = {
  BAD_USER_INPUT: 'Please correct the highlighted field and try again.',
  NOT_FOUND: "We couldn't find what you were looking for.",
  CONFLICT:
    'Someone else changed this while you were editing. What you entered is still here — reload to see their version.',
  PERSON_IN_USE:
    'This person is still credited on one or more movies. Remove those credits first, then delete the person.',
  PERSON_DATE_CONFLICTS_CREDIT:
    "That date conflicts with a movie this person is already credited on. See the highlighted field for details.",
  PAYLOAD_TOO_LARGE: 'That image is too large. Please choose a file up to 5 MB.',
  UNSUPPORTED_MEDIA_TYPE: 'That file is not a supported image. Please upload a JPEG, PNG, or WebP.',
  STORAGE_UNAVAILABLE:
    'Image storage is temporarily unavailable. Please try again in a moment.',
  DEPENDENCY_UNAVAILABLE:
    'A required service is temporarily unavailable. Please try again in a moment.',
  INTERNAL_ERROR: 'Something went wrong on our end. Please try again.'
};

/** True when [code] is one of the known stable codes. */
export function isErrorCode(code: string): code is ErrorCode {
  return code in MESSAGES;
}

/** Map a stable code to a friendly message, defaulting to the internal-error copy. */
export function messageForCode(code: string | undefined | null): string {
  if (code && isErrorCode(code)) return MESSAGES[code];
  return MESSAGES.INTERNAL_ERROR;
}

/** Whether a code represents a user-fixable validation problem (surfaced inline). */
export function isValidationError(code: string | undefined | null): boolean {
  return code === 'BAD_USER_INPUT';
}

/**
 * The message to show for a validation failure: the backend's own curated
 * message when one is present (it already names the offending value and rule),
 * rewritten into plain language for display, falling back to the generic
 * copy otherwise.
 */
export function messageForValidation(
  code: string | undefined | null,
  serverMessage: string | undefined | null
): string {
  if (code === 'BAD_USER_INPUT' && serverMessage) return humanizeValidationMessage(serverMessage);
  return messageForCode(code);
}

// Human labels for the raw GraphQL/form field names the backend embeds in its
// validation messages (e.g. "authorDisplayName"). Every field a form on this
// site can submit should have an entry here — a missing one just falls back
// to the raw key, which is the bug this map exists to avoid.
const FIELD_LABELS: Record<string, string> = {
  title: 'Title',
  originalTitle: 'Original title',
  synopsis: 'Synopsis',
  releaseDate: 'Release date',
  runtimeMinutes: 'Runtime',
  originalLanguage: 'Original language',
  genreCodes: 'Genres',
  characterName: 'Character name',
  sourceRoleName: 'Role name',
  billingOrder: 'Billing order',
  roleCode: 'Role',
  authorDisplayName: 'Your name',
  text: 'Comment',
  name: 'Name',
  biography: 'Biography',
  placeOfBirth: 'Place of birth',
  birthCountryCode: 'Country',
  birthDate: 'Birth date',
  deathDate: 'Death date',
  query: 'Search',
  letter: 'Letter',
  releaseYear: 'Release year',
  expectedVersion: 'Form'
};

function labelFor(field: string): string {
  return FIELD_LABELS[field] ?? field;
}

// A handful of distinct backend checks (a stray NUL byte, an invisible
// character, a bidi-override character, a control character) all boil down
// to the same thing from a user's point of view: some character in what they
// typed or pasted can't be saved. Naming the technical character class (and,
// for the control-character case, its string index) doesn't help anyone fix
// it, so every shape collapses into one plain-language message.
const UNSTORABLE_CHARACTER_RE =
  /^(\w+) contains (?:a null character, which can't be stored|an invisible character, which isn't allowed|a text-direction override character, which isn't allowed|a control character at position \d+)\.$/;

interface ValidationRewrite {
  pattern: RegExp;
  render: (match: RegExpMatchArray) => string;
}

// Known shapes of backend ValidationException messages (see catalogue's and
// people's `domain/*Rules.kt`), rewritten from developer-oriented copy (raw
// field keys, implementation details) into plain sentences a non-technical
// user can act on. Order matters: earlier, more specific patterns win.
const VALIDATION_REWRITES: ValidationRewrite[] = [
  {
    pattern: /^(\w+) must not be blank$/,
    render: ([, field]) => `${labelFor(field)} is required.`
  },
  {
    pattern: /^(\w+) must be at most (\d+) characters — you entered (\d+)\.$/,
    render: ([, field, max, entered]) =>
      `${labelFor(field)} can be up to ${max} characters — you entered ${entered}.`
  },
  {
    pattern: /^(\w+) must be at most (\d+) characters$/,
    render: ([, field, max]) => `${labelFor(field)} can be up to ${max} characters.`
  },
  {
    pattern: UNSTORABLE_CHARACTER_RE,
    render: ([, field]) =>
      `${labelFor(field)} contains a character that can't be saved — this can happen when text is pasted from another app. Delete the affected part and retype it.`
  },
  {
    pattern: /^(\w+) can't contain emoji\.$/,
    render: ([, field]) => `${labelFor(field)} can't contain emoji.`
  },
  {
    pattern: /^(\w+) must be positive$/,
    render: ([, field]) => `${labelFor(field)} must be greater than zero.`
  },
  {
    pattern: /^(\w+) must be zero or positive$/,
    render: ([, field]) => `${labelFor(field)} can't be negative.`
  },
  {
    pattern: /^cast credits require a characterName$/,
    render: () => 'Cast credits need a character name.'
  },
  {
    pattern: /^crew credits must not have a characterName$/,
    render: () => "Crew credits can't have a character name."
  },
  {
    pattern: /^role category \w+ does not match credit category \w+$/,
    render: () => "That role doesn't match this credit type. Please choose another."
  },
  {
    pattern: /^(\w+) code '[^']+' does not exist$/,
    render: ([, kind]) => `That ${kind} is no longer available. Please choose another.`
  },
  {
    pattern: /^(\w+) code '[^']+' is inactive$/,
    render: ([, kind]) => `That ${kind} is no longer active. Please choose another.`
  },
  {
    // V2.8-03: a birth/death date contradicts a credited movie's release date.
    // The backend already names the movie(s) and dates - only the raw field
    // key needs the same friendly-label treatment every other message gets.
    pattern: /^(\w+) (\S+) is after the release date of (.+)\. Check the date and try again\.$/,
    render: ([, field, date, movies]) =>
      `${labelFor(field)} (${date}) is after the release date of ${movies}. Check the date and try again.`
  },
  {
    pattern: /^(\w+) (\S+) is more than (\d+) years? before the release date of (.+)\. Check the date and try again\.$/,
    render: ([, field, date, years, movies]) =>
      `${labelFor(field)} (${date}) is more than ${years} years before the release date of ${movies}. Check the date and try again.`
  },
  {
    pattern: /^releaseYear must be between (\d+) and (\d+)$/,
    render: ([, min, max]) => `${labelFor('releaseYear')} must be between ${min} and ${max}.`
  },
  {
    pattern: /^letter must be a single alphabetic character$/,
    render: () => 'Please choose a single letter.'
  },
  {
    pattern: /^invalid date '[^']+'; expected ISO yyyy-MM-dd$/,
    render: () => "That date isn't valid. Please use the date picker."
  },
  {
    pattern: /^(?:search )?query must not be blank$/,
    render: () => 'Please enter something to search for.'
  },
  {
    pattern: /^(?:search )?query must be at (least|most) (\d+) characters?$/,
    render: ([, bound, n]) => `Search text must be at ${bound} ${n} character${n === '1' ? '' : 's'}.`
  }
];

function capitalize(s: string): string {
  return s.length ? s[0].toUpperCase() + s.slice(1) : s;
}

/**
 * Rewrites a backend validation message into plain language for display.
 * The backend's messages are curated and specific (they're the whole reason
 * `messageForValidation` prefers them over the generic BAD_USER_INPUT copy),
 * but written for a developer: they can name a raw field key (`"authorDisplayName
 * must not be blank"`) or a low-level implementation detail (`"contains a
 * control character at position 12"`). This is the one place that gap gets
 * closed, so every screen shows the same rewritten copy rather than each
 * component re-deciding how to phrase it.
 *
 * Falls back to capitalizing the original message (and ensuring it ends with
 * punctuation) when no known shape matches, so an unanticipated backend
 * message still reads as a sentence instead of surfacing verbatim.
 */
export function humanizeValidationMessage(message: string): string {
  for (const { pattern, render } of VALIDATION_REWRITES) {
    const match = message.match(pattern);
    if (match) return render(match);
  }
  const withPunctuation = /[.!?]$/.test(message) ? message : `${message}.`;
  return capitalize(withPunctuation);
}
