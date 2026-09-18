import { render, screen } from '@testing-library/svelte';
import userEvent from '@testing-library/user-event';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import * as devalue from 'devalue';
import ArtworkUpload from './ArtworkUpload.svelte';

const invalidateAllMock = vi.fn();
vi.mock('$app/navigation', () => ({ invalidateAll: () => invalidateAllMock() }));

// Matches the JSON envelope SvelteKit's own form actions send over the wire
// (see @sveltejs/kit's `deserialize` in $app/forms) - always HTTP 200 at the
// XHR transport level, with the real outcome in `type`/`status`.
function actionResponseText(type: 'success' | 'failure', status: number, data?: unknown) {
  return JSON.stringify({ type, status, data: data === undefined ? undefined : devalue.stringify(data) });
}

// A minimal fake XHR that lets us drive the upload progress + load events.
class FakeXHR {
  static instances: FakeXHR[] = [];
  upload = { listeners: {} as Record<string, (e: any) => void>, addEventListener(t: string, cb: (e: any) => void) { this.listeners[t] = cb; } };
  listeners: Record<string, () => void> = {};
  status = 200;
  responseText = actionResponseText('success', 200, {});
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
    invalidateAllMock.mockReset();
    vi.stubGlobal('XMLHttpRequest', FakeXHR as unknown as typeof XMLHttpRequest);
  });
  afterEach(() => {
    vi.unstubAllGlobals();
  });

  async function uploadFile() {
    const user = userEvent.setup();
    const { container } = render(ArtworkUpload, { props: { action: '?/uploadArtwork' } });
    const file = new File([new Uint8Array([1, 2, 3])], 'poster.png', { type: 'image/png' });
    const input = container.querySelector('input[type="file"]') as HTMLInputElement;
    await user.upload(input, file);
    await user.click(screen.getByRole('button', { name: 'Upload' }));
  }

  it('shows upload progress feedback during upload', async () => {
    await uploadFile();

    // progressbar rendered with the reported value; testid shows the percentage
    const bar = screen.getByRole('progressbar');
    expect(bar).toBeInTheDocument();
    expect(screen.getByTestId('upload-progress')).toBeInTheDocument();
  });

  it('shows the real error and does not reload data when the server rejects the file (regression)', async () => {
    // Bug: SvelteKit form actions respond HTTP 200 for a fail() result too -
    // the real outcome lives in the response body, not xhr.status. Trusting
    // xhr.status alone showed "Artwork uploaded." for a rejected upload.
    FakeXHR.instances = [];
    class RejectingXHR extends FakeXHR {
      responseText = actionResponseText('failure', 415, {
        message: 'That file is not a supported image. Please upload a JPEG, PNG, or WebP.',
        section: 'photo'
      });
    }
    vi.stubGlobal('XMLHttpRequest', RejectingXHR as unknown as typeof XMLHttpRequest);

    await uploadFile();

    expect(
      await screen.findByText('That file is not a supported image. Please upload a JPEG, PNG, or WebP.')
    ).toBeInTheDocument();
    expect(screen.queryByText('Artwork uploaded.')).not.toBeInTheDocument();
    expect(invalidateAllMock).not.toHaveBeenCalled();
  });

  it('shows the success message and reloads data when the server accepts the file', async () => {
    await uploadFile();

    expect(await screen.findByText('Artwork uploaded.')).toBeInTheDocument();
    expect(invalidateAllMock).toHaveBeenCalledOnce();
  });
});
