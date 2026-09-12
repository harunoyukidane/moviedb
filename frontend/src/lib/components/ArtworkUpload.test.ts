import { render, screen } from '@testing-library/svelte';
import userEvent from '@testing-library/user-event';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import ArtworkUpload from './ArtworkUpload.svelte';

// A minimal fake XHR that lets us drive the upload progress + load events.
class FakeXHR {
  static instances: FakeXHR[] = [];
  upload = { listeners: {} as Record<string, (e: any) => void>, addEventListener(t: string, cb: (e: any) => void) { this.listeners[t] = cb; } };
  listeners: Record<string, () => void> = {};
  status = 200;
  responseText = '';
  constructor() { FakeXHR.instances.push(this); }
  open() {}
  addEventListener(t: string, cb: () => void) { this.listeners[t] = cb; }
  send() {
    // simulate progress then completion
    this.upload.listeners['progress']?.({ lengthComputable: true, loaded: 50, total: 100 });
    this.listeners['load']?.();
  }
}

describe('ArtworkUpload', () => {
  beforeEach(() => {
    FakeXHR.instances = [];
    vi.stubGlobal('XMLHttpRequest', FakeXHR as unknown as typeof XMLHttpRequest);
  });
  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it('shows upload progress feedback during upload', async () => {
    const user = userEvent.setup();
    const { container } = render(ArtworkUpload, { props: { action: '?/uploadArtwork' } });

    const file = new File([new Uint8Array([1, 2, 3])], 'poster.png', { type: 'image/png' });
    const input = container.querySelector('input[type="file"]') as HTMLInputElement;
    await user.upload(input, file);

    await user.click(screen.getByRole('button', { name: 'Upload' }));

    // progressbar rendered with the reported value; testid shows the percentage
    const bar = screen.getByRole('progressbar');
    expect(bar).toBeInTheDocument();
    expect(screen.getByTestId('upload-progress')).toBeInTheDocument();
  });
});
