<script lang="ts">
  // Live character counter for every bounded text field (V2.2-12). Counts with
  // JS `.length` (UTF-16 code units) - the same unit the server enforces and
  // the same unit a native `maxlength` stops at - so this is never a different
  // number than what the field will actually accept.
  // Either pass the field's current value (the count is derived from it), or
  // - when the field itself stays an uncontrolled input for other reasons -
  // pass a pre-computed `count` directly (e.g. tracked via `on:input`).
  export let value: string = '';
  export let max: number;
  export let count: number | undefined = undefined;

  $: displayCount = count ?? value.length;
  $: atLimit = displayCount >= max;
  $: nearLimit = !atLimit && max > 0 && displayCount / max >= 0.9;
</script>

<p
  class="char-counter"
  class:warning={nearLimit}
  class:at-limit={atLimit}
  aria-hidden="true"
  title="Some characters, like emoji, count as more than one."
>
  {displayCount.toLocaleString()} / {max.toLocaleString()}
</p>
<p class="visually-hidden" aria-live="polite">{atLimit ? 'Character limit reached' : ''}</p>

<style>
  .char-counter {
    font-size: 0.8rem;
    color: var(--text-muted);
    margin: 2px 0 0;
    text-align: right;
  }
  .char-counter.warning {
    color: var(--accent);
  }
  .char-counter.at-limit {
    color: var(--danger);
    font-weight: 600;
  }
  .visually-hidden {
    position: absolute;
    width: 1px;
    height: 1px;
    overflow: hidden;
    clip: rect(0 0 0 0);
  }
</style>
