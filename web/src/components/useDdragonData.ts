import { useQuery } from '@tanstack/react-query'
import { queryKeys } from '../api/queryKeys'
import { locale } from '../lib/i18n'
import { championDataUrl, ddragonLanguage, itemDataUrl, runesDataUrl } from '../lib/lol'
import { parseChampionKeys, parseItems, parseRunes, type ItemInfo, type RuneInfo } from '../lib/lolDeep'

/**
 * One Data Dragon JSON file for this version, parsed once and kept for the session (like useSummonerSpells).
 * Pictures and names are extras: when the file cannot be loaded the tables show ids or initials instead.
 */
function useDdragonFile<T>(version: string | null, file: string, url: (version: string) => string, parse: (json: unknown) => T): T | null {
  const query = useQuery({
    queryKey: queryKeys.ddragonData(version ?? '', file),
    queryFn: async () => {
      const response = await fetch(url(version!))
      return parse(response.ok ? await response.json() : null)
    },
    enabled: version != null,
    staleTime: Infinity,
    gcTime: Infinity,
    retry: 1,
  })
  return query.data ?? null
}

const language = ddragonLanguage(locale)

export const useItemData = (version: string | null): Record<number, ItemInfo> | null =>
  useDdragonFile(version, `item-${language}`, (v) => itemDataUrl(v, language), parseItems)

export const useRuneData = (version: string | null): Record<number, RuneInfo> | null =>
  useDdragonFile(version, `runes-${language}`, (v) => runesDataUrl(v, language), parseRunes)

/** Numeric champion key → icon id, for bans */
export const useChampionKeys = (version: string | null): Record<number, string> | null =>
  useDdragonFile(version, 'champion', championDataUrl, parseChampionKeys)
