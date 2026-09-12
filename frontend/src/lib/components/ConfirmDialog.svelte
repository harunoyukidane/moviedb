<script lang="ts">
  import { createEventDispatcher, onMount, tick } from 'svelte';

  export let open = false;
  export let title: string;
  export let confirmLabel = 'Confirm';
  export let cancelLabel = 'Cancel';
  export let danger = false;

  const dispatch = createEventDispatcher<{ confirm: void; cancel: void }>();

  let dialogEl: HTMLDivElement | null = null;
  let confirmBtn: HTMLButtonElement | null = null;
  let previouslyFocused: HTMLElement | null = null;

  $: if (open) void focusDialog();

  async function focusDialog() {
    previouslyFocused = document.activeElement as HTMLElement | null;
    await tick();
    confirmBtn?.focus();
  }

  function close(kind: 'confirm' | 'cancel') {
    open = false;
    // restore focus to the element that opened the dialog (keyboard continuity)
    previouslyFocused?.focus();
    dispatch(kind);
  }

  function onKeydown(e: KeyboardEvent) {
    if (!open) return;
    if (e.key === 'Escape') {
      e.preventDefault();
      close('cancel');
    } else if (e.key === 'Tab' && dialogEl) {
      // simple focus trap
      const focusables = dialogEl.querySelectorAll<HTMLElement>('button, [href], input, select, textarea');
      if (focusables.length === 0) return;
      const first = focusables[0];
      const last = focusables[focusables.length - 1];
      if (e.shiftKey && document.activeElement === first) {
        e.preventDefault();
        last.focus();
      } else if (!e.shiftKey && document.activeElement === last) {
        e.preventDefault();
        first.focus();
      }
    }
  }
</script>

<svelte:window on:keydown={onKeydown} />

{#if open}
  <!-- svelte-ignore a11y-click-events-have-key-events a11y-no-static-element-interactions a11y-no-noninteractive-element-interactions -->
  <div class="overlay" on:click={() => close('cancel')} role="presentation">
    <div
      class="dialog"
      bind:this={dialogEl}
      role="dialog"
      aria-modal="true"
      aria-labelledby="confirm-title"
      on:click|stopPropagation
      on:keydown={onKeydown}
    >
      <h2 id="confirm-title">{title}</h2>
      <div class="body"><slot /></div>
      <div class="actions">
        <button type="button" on:click={() => close('cancel')}>{cancelLabel}</button>
        <button
          type="button"
          class={danger ? 'danger' : 'primary'}
          bind:this={confirmBtn}
          on:click={() => close('confirm')}
        >
          {confirmLabel}
        </button>
      </div>
    </div>
  </div>
{/if}

<style>
  .overlay {
    position: fixed; inset: 0; background: rgba(0, 0, 0, 0.6);
    display: grid; place-items: center; z-index: 50; padding: var(--sp-2);
  }
  .dialog {
    background: var(--surface); border: 1px solid var(--border); border-radius: var(--radius);
    padding: var(--sp-3); max-width: 480px; width: 100%;
  }
  .dialog h2 { margin-top: 0; }
  .actions { display: flex; gap: var(--sp-1); justify-content: flex-end; margin-top: var(--sp-3); }
</style>
