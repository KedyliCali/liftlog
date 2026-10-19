import type { CatalogFood } from '../api/types'

// Looking up packaged foods by barcode in Open Food Facts, a free and open product database.

/** What we keep from an Open Food Facts product: per-100 g values like the built-in catalog. */
export interface ScannedProduct {
  code: string
  name: string
  brand: string | null
  kcalPer100g: number
  proteinPer100g: number
  carbsPer100g: number
  fatPer100g: number
  /** Grams in one serving as printed on the pack; null when the pack does not say. */
  servingG: number | null
  servingLabel: string | null
}

export type LookupResult =
  | { status: 'found'; product: ScannedProduct }
  /** The product exists but has no usable energy value; its name can still prefill manual entry. */
  | { status: 'noNutrition'; name: string | null }
  | { status: 'notFound' }

const FIELDS = 'product_name,product_name_tr,brands,nutriments,serving_size,serving_quantity'
const KJ_PER_KCAL = 4.184
/** Pure fat is 900 kcal per 100 g; anything far above it is a data-entry error in the database. */
const MAX_KCAL_PER_100G = 950

export const offProductUrl = (code: string) => `https://world.openfoodfacts.org/api/v2/product/${code}.json?fields=${FIELDS}`

/** EAN-8, UPC-A (12), EAN-13 and ITF-14 are all 8 to 14 digits. */
export function isValidBarcode(code: string): boolean {
  return /^\d{8,14}$/.test(code)
}

/** Open Food Facts values are usually numbers but sometimes numeric strings; anything else is missing. */
function numberOrNull(value: unknown): number | null {
  const n = typeof value === 'number' ? value : typeof value === 'string' && value.trim() !== '' ? Number(value) : NaN
  return Number.isFinite(n) && n >= 0 ? n : null
}

const oneDecimal = (n: number) => Math.round(n * 10) / 10

function textOrNull(value: unknown): string | null {
  return typeof value === 'string' && value.trim() !== '' ? value.trim() : null
}

/**
 * Turns the API's JSON into our shape. kcal comes from energy-kcal_100g, or from kJ when only that is
 * filled in (older entries and many EU packs list kJ first). Missing macros count as 0 g, the same way a
 * pack with "0 g fat" would.
 */
export function mapOffProduct(code: string, json: unknown, preferTurkish: boolean): LookupResult {
  const product = (json as { product?: Record<string, unknown> } | null)?.product
  if (!product || typeof product !== 'object') return { status: 'notFound' }

  const turkishName = textOrNull(product.product_name_tr)
  const defaultName = textOrNull(product.product_name)
  const name = (preferTurkish ? (turkishName ?? defaultName) : (defaultName ?? turkishName))?.slice(0, 80) ?? null
  const brand = textOrNull(product.brands)?.split(',')[0].trim() || null

  const nutriments = (product.nutriments ?? {}) as Record<string, unknown>
  const kj = numberOrNull(nutriments['energy-kj_100g']) ?? numberOrNull(nutriments['energy_100g'])
  const kcal = numberOrNull(nutriments['energy-kcal_100g']) ?? (kj != null ? kj / KJ_PER_KCAL : null)
  if (kcal == null || kcal > MAX_KCAL_PER_100G) return { status: 'noNutrition', name }

  const servingQuantity = numberOrNull(product.serving_quantity)
  const servingG = servingQuantity != null && servingQuantity > 0 && servingQuantity <= 5000 ? oneDecimal(servingQuantity) : null
  return {
    status: 'found',
    product: {
      code,
      // A few entries have values but no name; the brand (or the code itself) still tells the user what it was
      name: name ?? brand ?? code,
      brand,
      kcalPer100g: oneDecimal(kcal),
      proteinPer100g: oneDecimal(numberOrNull(nutriments['proteins_100g']) ?? 0),
      carbsPer100g: oneDecimal(numberOrNull(nutriments['carbohydrates_100g']) ?? 0),
      fatPer100g: oneDecimal(numberOrNull(nutriments['fat_100g']) ?? 0),
      servingG,
      servingLabel: servingG != null ? (textOrNull(product.serving_size)?.slice(0, 40) ?? `${servingG} g`) : null,
    },
  }
}

/** Name to log: the brand is added unless the product name already carries it. */
export function displayName(product: ScannedProduct): string {
  if (!product.brand || product.name.toLocaleLowerCase('tr').includes(product.brand.toLocaleLowerCase('tr'))) return product.name
  return `${product.brand} ${product.name}`
}

/**
 * Lets the amount step treat a scanned product like a catalog food. Without a printed serving, one
 * "serving" is 100 g so the servings stepper still makes sense.
 */
export function productAsCatalogFood(product: ScannedProduct): CatalogFood {
  const name = displayName(product)
  const serving = product.servingLabel ?? '100 g'
  return {
    id: -1,
    category: 'scanned',
    nameTr: name,
    nameEn: name,
    kcalPer100g: product.kcalPer100g,
    proteinPer100g: product.proteinPer100g,
    carbsPer100g: product.carbsPer100g,
    fatPer100g: product.fatPer100g,
    servingG: product.servingG ?? 100,
    servingTr: serving,
    servingEn: serving,
  }
}

// ---------- Cache ----------

const CACHE_KEY = 'liftlog.barcodeProducts'
/** A few hundred products is years of groceries and still only tens of kilobytes. */
const MAX_CACHED = 300

type Cache = Record<string, ScannedProduct>

function readCache(storage: Storage | undefined): Cache {
  try {
    const raw = storage?.getItem(CACHE_KEY)
    return raw ? (JSON.parse(raw) as Cache) : {}
  } catch {
    return {}
  }
}

export function cachedProduct(code: string, storage: Storage | undefined = safeLocalStorage()): ScannedProduct | null {
  return readCache(storage)[code] ?? null
}

function cacheProduct(product: ScannedProduct, storage: Storage | undefined) {
  try {
    const cache = readCache(storage)
    delete cache[product.code]
    cache[product.code] = product
    // Object keys keep insertion order, so the oldest lookups are dropped first
    const codes = Object.keys(cache)
    for (const old of codes.slice(0, Math.max(0, codes.length - MAX_CACHED))) delete cache[old]
    storage?.setItem(CACHE_KEY, JSON.stringify(cache))
  } catch {
    // Private mode or a full quota: the lookup still worked, it just won't be remembered
  }
}

function safeLocalStorage(): Storage | undefined {
  try {
    return globalThis.localStorage
  } catch {
    return undefined
  }
}

/**
 * Answers from the local cache first, so a product scanned before works without a connection. Only found
 * products are cached: a missing one may be added to Open Food Facts later. Network failures throw.
 */
export async function lookupBarcode(
  code: string,
  { preferTurkish, fetchFn = fetch, storage = safeLocalStorage() }: { preferTurkish: boolean; fetchFn?: typeof fetch; storage?: Storage },
): Promise<LookupResult> {
  const cached = cachedProduct(code, storage)
  if (cached) return { status: 'found', product: cached }

  const response = await fetchFn(offProductUrl(code), { headers: { Accept: 'application/json' } })
  // Unknown codes answer 404 with {"status":0}
  if (response.status === 404) return { status: 'notFound' }
  if (!response.ok) throw new Error(`Open Food Facts answered ${response.status}`)
  const result = mapOffProduct(code, await response.json(), preferTurkish)
  if (result.status === 'found') cacheProduct(result.product, storage)
  return result
}
