<!-- Shared placeholder for a person with no photo (or an unavailable People
     reference); used wherever a person's photo would otherwise appear. -->
<div class="person-photo-fallback" aria-hidden="true">👤</div>

<style>
  .person-photo-fallback {
    width: 100%;
    aspect-ratio: 1 / 1;
    border-radius: 50%;
    display: grid;
    place-items: center;
    font-size: 1.25rem;
    background: var(--surface-2);
  }
</style>
