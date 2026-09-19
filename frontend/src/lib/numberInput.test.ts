import { describe, expect, it, vi } from 'vitest';
import { blockNonWholeNumberKeys } from './numberInput';

function keyEvent(key: string, modifiers: Partial<KeyboardEvent> = {}): KeyboardEvent {
  return { key, preventDefault: vi.fn(), ...modifiers } as unknown as KeyboardEvent;
}

describe('blockNonWholeNumberKeys', () => {
  it('blocks the sign, exponent, and decimal-point keys', () => {
    for (const key of ['-', '+', 'e', 'E', '.']) {
      const event = keyEvent(key);
      blockNonWholeNumberKeys(event);
      expect(event.preventDefault).toHaveBeenCalled();
    }
  });

  it('leaves digits and navigation keys alone', () => {
    for (const key of ['0', '5', 'Backspace', 'ArrowLeft', 'Tab']) {
      const event = keyEvent(key);
      blockNonWholeNumberKeys(event);
      expect(event.preventDefault).not.toHaveBeenCalled();
    }
  });

  it('lets Ctrl/Cmd/Alt shortcuts through even on an otherwise-blocked key (V2.7-06)', () => {
    for (const modifiers of [{ ctrlKey: true }, { metaKey: true }, { altKey: true }]) {
      const event = keyEvent('e', modifiers);
      blockNonWholeNumberKeys(event);
      expect(event.preventDefault).not.toHaveBeenCalled();
    }
  });
});
