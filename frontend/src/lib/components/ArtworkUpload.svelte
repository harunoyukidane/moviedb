<script lang="ts">
  import { invalidateAll } from '$app/navigation';
  import { messageForCode } from '$lib/errors';

  // Posts multipart to a SvelteKit form action (server relays to the media
  // endpoint) via XHR so we can show real upload progress. The browser never
  // calls the Catalogue media endpoint directly.
  export let action: string; // e.g. "?/uploadArtwork"
  export let label = 'Upload artwork';

  let file: File | null = null;
  let progress = 0;
  let uploading = false;
  let message: string | null = null;
  let messageVariant: 'info' | 'error' = 'info';

  function onFileChange(e: Event) {
    const input = e.target as HTMLInputElement;
    file = input.files?.[0] ?? null;
    message = null;
  }

  function upload() {
    if (!file || uploading) return;
    uploading = true;
    progress = 0;
    message = null;

    const body = new FormData();
    body.append('file', file);

    const xhr = new XMLHttpRequest();
    xhr.open('POST', action);
    xhr.upload.addEventListener('progress', (ev) => {
      if (ev.lengthComputable) progress = Math.round((ev.loaded / ev.total) * 100);
    });
    xhr.addEventListener('load', async () => {
      uploading = false;
      if (xhr.status >= 200 && xhr.status < 300) {
        progress = 100;
        message = 'Artwork uploaded.';
        messageVariant = 'info';
        file = null;
        await invalidateAll();
      } else {
        // SvelteKit action failures return a JSON envelope; extract our code/message
        let code = 'INTERNAL_ERROR';
        try {
          const parsed = JSON.parse(xhr.responseText);
          const data = parsed?.data ? JSON.parse(parsed.data) : parsed;
          if (typeof data?.message === 'string') {
            message = data.message;
            messageVariant = 'error';
          }
        } catch {
          message = messageForCode(code);
          messageVariant = 'error';
        }
        if (!message) message = messageForCode(code);
        messageVariant = 'error';
      }
    });
    xhr.addEventListener('error', () => {
      uploading = false;
      message = messageForCode('DEPENDENCY_UNAVAILABLE');
      messageVariant = 'error';
    });
    xhr.send(body);
  }
</script>

<div class="uploader">
  <label for="artwork-file">{label}</label>
  <input id="artwork-file" type="file" accept="image/jpeg,image/png,image/webp" on:change={onFileChange} />
  <button type="button" class="primary" on:click={upload} disabled={!file || uploading}>
    {uploading ? 'Uploading…' : 'Upload'}
  </button>

  {#if uploading || progress > 0}
    <div class="progress" role="progressbar" aria-valuemin="0" aria-valuemax="100" aria-valuenow={progress} aria-label="Upload progress">
      <div class="bar" style={`width:${progress}%`}></div>
    </div>
    <span class="pct" data-testid="upload-progress">{progress}%</span>
  {/if}

  {#if message}
    <p class={messageVariant === 'error' ? 'msg-error' : 'msg-info'} role={messageVariant === 'error' ? 'alert' : 'status'}>
      {message}
    </p>
  {/if}
</div>

<style>
  .uploader { display: grid; gap: var(--sp-1); }
  .progress { height: 8px; background: var(--surface-2); border-radius: 999px; overflow: hidden; }
  .bar { height: 100%; background: var(--accent); transition: width 0.15s ease; }
  .pct { color: var(--text-muted); font-size: 0.875rem; }
  .msg-error { color: var(--danger); }
  .msg-info { color: var(--text-muted); }
</style>
