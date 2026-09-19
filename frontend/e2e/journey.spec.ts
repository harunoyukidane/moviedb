import { test, expect } from '@playwright/test';

// Critical end-to-end journey (§16, phase 8) exercised through the BFF UI:
//   create person -> create movie -> add credit -> upload artwork -> search ->
//   remove credit -> delete movie -> delete person.
// Runs against the composed stack + the SvelteKit BFF. It is intentionally the
// one high-value happy path; unit/component/integration tests cover the rest.
//
// A 1x1 PNG used for the artwork/photo upload step.
const PNG_1x1 = Buffer.from(
  'iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNk+M9QDwADhgGAWjR9awAAAABJRU5ErkJggg==',
  'base64'
);

test('full catalogue journey through the BFF UI', async ({ page }) => {
  const stamp = Date.now();
  const personName = `E2E Person ${stamp}`;
  const movieTitle = `E2E Movie ${stamp}`;

  // 1. create a person
  await page.goto('/people/new');
  await page.getByLabel('Name *').fill(personName);
  await page.getByRole('button', { name: /create person/i }).click();
  await expect(page.getByRole('heading', { name: personName })).toBeVisible();
  const personUrl = page.url();

  // 2. create a movie
  await page.goto('/movies/new');
  await page.getByLabel('Title *').fill(movieTitle);
  await page.getByRole('button', { name: /create movie/i }).click();
  await expect(page.getByRole('heading', { name: movieTitle })).toBeVisible();
  const movieUrl = page.url();

  // 3. add a cast credit via the accessible dialog on the editor
  await page.goto(`${movieUrl}/edit`);
  await page.getByRole('button', { name: 'Add credit' }).click();
  const dialog = page.getByRole('dialog');
  await dialog.getByLabel('Person').fill(personName);
  // The picker follows the ARIA combobox pattern: suggestions are non-focusable
  // <li role="option">, not buttons (keyboard selection is ArrowDown/Enter on the
  // input). Asserting on the option role is what keeps this step honest.
  await dialog.getByRole('option', { name: personName }).click();
  await dialog.getByLabel('Role').selectOption('ACTOR');
  await dialog.getByLabel('Character name *').fill('The Lead');
  await dialog.getByRole('button', { name: 'Add credit' }).click();

  // 4. upload artwork
  await page.setInputFiles('#artwork-file', { name: 'poster.png', mimeType: 'image/png', buffer: PNG_1x1 });
  await page.getByRole('button', { name: 'Upload' }).click();
  await expect(page.getByText(/Artwork uploaded/i)).toBeVisible();

  // 5. search finds the movie and the person
  await page.goto('/movies');
  await page.getByRole('searchbox').fill(movieTitle);
  await expect(page.getByTestId('search-results').getByText(movieTitle)).toBeVisible();

  // 6. remove the credit from the movie (on the editor; no confirmation needed — the
  //    credit can be re-added from the person's data at any time; never deletes the person)
  await page.goto(`${movieUrl}/edit`);
  await page.getByRole('button', { name: `Remove ${personName} from movie` }).click();
  await expect(page.getByText(/Credit removed from movie/i)).toBeVisible();

  // 7. delete the movie (danger zone on the editor)
  await page.getByRole('button', { name: 'Delete movie' }).first().click();
  await page.getByRole('dialog').getByRole('button', { name: 'Delete movie' }).click();
  await expect(page).toHaveURL(/\/movies$/);

  // 8. delete the person (danger zone on the person editor). Without this the
  //    journey leaves a person behind on every green run, so the catalogue
  //    slowly fills with E2E rows that nothing ever cleans up.
  await page.goto(`${personUrl}/edit`);
  await page.getByRole('button', { name: 'Delete person' }).first().click();
  await page.getByRole('dialog').getByRole('button', { name: 'Delete person' }).click();
  await expect(page).toHaveURL(/\/people$/);
});

// V2.8-01: a few hundred characters with no whitespace is legal input (the name
// cap is 300), and it used to widen the detail page past the viewport. jsdom
// runs no layout, so the vitest guard can only pin the CSS rules - this is the
// assertion that actually measures the page, which is why it lives here.
test('a 300-character unbroken name does not widen the person page', async ({ page }) => {
  const longName = 'W'.repeat(300);

  await page.goto('/people/new');
  await page.getByLabel('Name *').fill(longName);
  await page.getByRole('button', { name: /create person/i }).click();
  await expect(page.getByRole('heading', { name: longName })).toBeVisible();
  const personUrl = page.url();

  for (const viewport of [
    { width: 1280, height: 900 },
    { width: 375, height: 812 }
  ]) {
    await page.setViewportSize(viewport);
    await page.goto(personUrl);
    await expect(page.getByRole('heading', { name: longName })).toBeVisible();

    const overflow = await page.evaluate(() => {
      const doc = document.documentElement;
      return { scrollWidth: doc.scrollWidth, clientWidth: doc.clientWidth };
    });
    // 1px of tolerance for sub-pixel rounding; the bug overflowed by hundreds.
    expect(
      overflow.scrollWidth,
      `horizontal overflow at ${viewport.width}px: ${overflow.scrollWidth} > ${overflow.clientWidth}`
    ).toBeLessThanOrEqual(overflow.clientWidth + 1);
  }

  // Leave the catalogue as we found it (no credits were added, so this is allowed).
  await page.setViewportSize({ width: 1280, height: 900 });
  await page.goto(`${personUrl}/edit`);
  await page.getByRole('button', { name: 'Delete person' }).first().click();
  await page.getByRole('dialog').getByRole('button', { name: 'Delete person' }).click();
  await expect(page).toHaveURL(/\/people$/);
});
