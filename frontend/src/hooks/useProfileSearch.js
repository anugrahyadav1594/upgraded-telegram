import { useQuery } from '@tanstack/react-query';

import { usersApi } from '../api/users.js';
import { useDebouncedValue } from './useDebouncedValue.js';
import { queryKeys } from './queryKeys.js';

/**
 * Username search.
 *
 * Two characters minimum mirrors the server rule, and the debounce keeps the 60/min search budget for
 * actual searching rather than for eagerness. Previous results stay on screen while the next query is in
 * flight, so the list does not flicker as the user types.
 */
export function useProfileSearch(term) {
  const debounced = useDebouncedValue(term?.trim() || '', 250);
  const enabled = debounced.length >= 2;

  const query = useQuery({
    queryKey: queryKeys.profiles.search(debounced),
    queryFn: () => usersApi.search(debounced, undefined, 20),
    enabled,
    staleTime: 30_000,
    placeholderData: (previous) => previous,
  });

  return { ...query, term: debounced, enabled };
}
