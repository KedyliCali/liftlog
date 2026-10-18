import { useQuery } from '@tanstack/react-query'
import { queryKeys } from '../api/queryKeys'
import { ddragonVersionsUrl } from '../lib/lol'

/**
 * Latest Data Dragon version, which picture URLs need. Images are Riot's own public CDN; if it cannot be
 * reached the app shows initials instead, so nothing depends on it.
 */
export function useDdragonVersion(): string | null {
  const query = useQuery({
    queryKey: queryKeys.ddragonVersion,
    queryFn: async () => {
      const response = await fetch(ddragonVersionsUrl)
      const versions = (await response.json()) as string[]
      return versions[0]
    },
    staleTime: 24 * 60 * 60_000,
    retry: 1,
  })
  return query.data ?? null
}
