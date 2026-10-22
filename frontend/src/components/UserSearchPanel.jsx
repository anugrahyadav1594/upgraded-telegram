import { useState } from 'react';

import { Avatar } from './Avatar.jsx';
import { EmptyState } from './EmptyState.jsx';
import { LoadingBlock } from './Spinner.jsx';
import { Icon } from './Icon.jsx';
import { useProfileSearch } from '../hooks/useProfileSearch.js';

/**
 * "New chat" search.
 *
 * Selecting a result starts the conversation *by user id* and then hands the caller the conversation id
 * the server returned — the client never invents one. Results exclude people who blocked the searcher
 * or hid from search, because that filtering is done server-side and the client cannot know better.
 */
export function UserSearchPanel({ onStartConversation, busy }) {
  const [term, setTerm] = useState('');
  const { data, isFetching, error, enabled } = useProfileSearch(term);
  const results = data?.items || [];

  return (
    <div className="stack">
      <div className="field">
        <label className="field__label" htmlFor="user-search">
          Find someone by username
        </label>
        <div className="row" style={{ gap: 8 }}>
          <Icon name="search" size={18} />
          <input
            id="user-search"
            type="search"
            autoComplete="off"
            placeholder="Try @developer"
            value={term}
            onChange={(event) => setTerm(event.target.value)}
            aria-describedby="user-search-hint"
          />
        </div>
        <span className="field__hint" id="user-search-hint">
          Type at least two characters. Search works on public usernames and display names.
        </span>
      </div>

      {!enabled ? (
        <p className="muted text-sm">Start typing to search.</p>
      ) : isFetching && results.length === 0 ? (
        <LoadingBlock label="Searching…" />
      ) : error ? (
        <p className="alert alert--error" role="alert">
          {error.message}
        </p>
      ) : results.length === 0 ? (
        <EmptyState
          icon="search"
          title="No matches"
          description="Check the spelling, or try a different username."
        />
      ) : (
        <ul className="search-results">
          {results.map((profile) => (
            <li key={profile.id}>
              <button
                type="button"
                className="search-result"
                disabled={busy}
                onClick={() => onStartConversation(profile)}
              >
                <Avatar user={profile} size={40} />
                <span>
                  <span className="conversation-item__name">{profile.displayName}</span>
                  <br />
                  <span className="text-xs muted">@{profile.username}</span>
                </span>
              </button>
            </li>
          ))}
        </ul>
      )}
    </div>
  );
}

export default UserSearchPanel;
