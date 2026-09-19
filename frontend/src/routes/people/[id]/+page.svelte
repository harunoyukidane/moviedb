<script lang="ts">
  import type { PageData, ActionData } from './$types';
  import StateBanner from '$lib/components/StateBanner.svelte';
  import IconLink from '$lib/components/IconLink.svelte';
  import BackLink from '$lib/components/BackLink.svelte';

  export let data: PageData;
  export let form: ActionData;

  $: person = data.person;
  let photoError = false;
  // A person with no photo yet 404s on the initial <img>, latching photoError.
  // A photo upload/delete elsewhere changes data.photoUrl (it's version-busted);
  // without this, the image would stay hidden forever after navigating back here.
  $: data.photoUrl, (photoError = false);

  $: birthPlace = [person.placeOfBirth, person.birthCountry?.name].filter(Boolean).join(' — ');
</script>

<svelte:head><title>{person.name} · MovieDB</title></svelte:head>

<BackLink fallbackHref="/people">← Back</BackLink>

<div class="detail">
  <section class="photo-section" aria-label="Photo">
    {#if !photoError}
      <img
        class="detail-media"
        src={data.photoUrl}
        alt={`Photo of ${person.name}`}
        fetchpriority="high"
        on:error={() => (photoError = true)}
      />
    {:else}
      <div class="detail-media detail-fallback" aria-hidden="true">👤</div>
    {/if}
    <a class="edit-link" href={`/people/${person.id}/edit#photo`}>Manage photo</a>
  </section>

  <section class="info">
    <div class="detail-title-row">
      <h1>{person.name}</h1>
      <IconLink icon="edit" href={`/people/${person.id}/edit`} label="Edit person" />
    </div>

    {#if form?.message}
      <StateBanner variant="error">{form.message}</StateBanner>
    {/if}

    <dl class="detail-meta">
      {#if person.birthDate}<dt>Born</dt><dd>{person.birthDate}{birthPlace ? ` · ${birthPlace}` : ''}</dd>{/if}
      {#if person.deathDate}<dt>Died</dt><dd>{person.deathDate}</dd>{/if}
    </dl>

    {#if person.biography}<p class="bio">{person.biography}</p>{/if}

    <h2>Filmography</h2>
    {#if person.credits.length === 0}
      <StateBanner variant="info">No credits yet.</StateBanner>
    {:else}
      <ul class="credits">
        {#each person.credits as c (c.movieId + c.role.code + (c.characterName ?? ''))}
          <li>
            <a href={`/movies/${c.movieId}`}>{c.movieTitle}</a>
            — {c.category === 'CAST' ? `as ${c.characterName}` : c.role.title}
          </li>
        {/each}
      </ul>
    {/if}
  </section>
</div>

<style>
  .detail { --detail-col-width: 220px; }
  .detail-media { aspect-ratio: 1; }
  .edit-link { display: inline-block; margin-top: var(--sp-1); }
  .credits { list-style: none; padding: 0; }
  .credits li { padding: var(--sp-1) 0; border-bottom: 1px solid var(--border); }
</style>
