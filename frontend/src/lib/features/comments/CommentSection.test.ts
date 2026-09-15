import { render, screen } from '@testing-library/svelte';
import { describe, expect, it } from 'vitest';
import CommentSection from './CommentSection.svelte';
import type { MovieComment } from '$lib/server/types';

function comment(over: Partial<MovieComment>): MovieComment {
  return {
    id: 'c1',
    authorDisplayName: 'Alice',
    text: 'Great movie!',
    createdAt: '2026-03-05T14:30:00Z',
    ...over
  };
}

describe('CommentSection', () => {
  it('shows the empty-state message when there are no comments', () => {
    render(CommentSection, { props: { comments: [] } });
    expect(screen.getByText(/No comments yet/)).toBeInTheDocument();
  });

  it('supports a custom empty-state message', () => {
    render(CommentSection, { props: { comments: [], emptyMessage: 'Nothing here.' } });
    expect(screen.getByText('Nothing here.')).toBeInTheDocument();
  });

  it('renders each comment with author and text', () => {
    render(CommentSection, {
      props: { comments: [comment({ id: 'c1', authorDisplayName: 'Alice', text: 'Loved it' })] }
    });
    expect(screen.getByText('Alice')).toBeInTheDocument();
    expect(screen.getByText('Loved it')).toBeInTheDocument();
  });

  it('preserves the given comment order rather than re-sorting (server already orders reverse-chronologically)', () => {
    render(CommentSection, {
      props: {
        comments: [
          comment({ id: 'c1', authorDisplayName: 'Newest' }),
          comment({ id: 'c2', authorDisplayName: 'Oldest' })
        ]
      }
    });
    const names = screen.getAllByText(/^(Newest|Oldest)$/).map((el) => el.textContent);
    expect(names).toEqual(['Newest', 'Oldest']);
  });
});
