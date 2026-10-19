import { useQuery } from '@tanstack/react-query'
import { queryKeys } from '../api/queryKeys'
import { parseSummonerSpells, summonerSpellDataUrl } from '../lib/lol'

/**
 * Numeric summoner spell key → icon file, from Data Dragon's summoner.json for this version. Fetched once per
 * version and kept for the session. If it cannot be loaded the match rows simply show no spell icons.
 */
export function useSummonerSpells(version: string | null): Record<number, string> | null {
  const query = useQuery({
    queryKey: queryKeys.ddragonSummonerSpells(version ?? ''),
    queryFn: async () => {
      const response = await fetch(summonerSpellDataUrl(version!))
      if (!response.ok) return {}
      return parseSummonerSpells(await response.json())
    },
    enabled: version != null,
    staleTime: Infinity,
    gcTime: Infinity,
    retry: 1,
  })
  return query.data ?? null
}
