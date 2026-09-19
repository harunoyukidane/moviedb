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
  <!-- svelte-ignore a11y-click-events-have-key-events -- dismiss-on-click backdrop; Escape is handled by the window keydown listener above, not this element. -->
  <!-- svelte-ignore a11y-no-static-element-interactions -- role="presentation" backdrop is intentionally non-interactive to assistive tech; the click only dismisses, it carries no content or focusable behavior of its own. -->
  <!-- svelte-ignore a11y-no-noninteractive-element-interactions -- same backdrop-dismiss pattern; role="presentation" removes it from the accessibility tree by design. -->
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

