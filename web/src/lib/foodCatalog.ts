import type { CatalogFood } from '../api/types'

// Searching the built-in food list and turning a chosen amount into calories and protein.

/**
 * Lowercases with Turkish rules and drops the dots and cedillas, so "corba" finds "Çorbası" and
 * "IZGARA" finds "ızgara". Without the Turkish locale, "I".toLowerCase() would give "i" and miss "ı".
 */
export function foldTurkish(text: string): string {
  return text
    .toLocaleLowerCase('tr')
    .normalize('NFD')
    .replace(/[̀-ͯ]/g, '')
    .replace(/ı/g, 'i')
    // Brands: "McDonald's" is typed "mcdonalds", "Coca-Cola" as "coca cola"
    .replace(/['’`]/g, '')
    .replace(/[-/&.,()+]/g, ' ')
}

/** Folded once per catalog row: the list is about a thousand rows and is searched on every keystroke. */
type SearchKey = { names: string[]; compact: string[] }
const searchKeys = new WeakMap<CatalogFood, SearchKey>()

function searchKeyOf(food: CatalogFood): SearchKey {
  let key = searchKeys.get(food)
  if (!key) {
    const names = [foldTurkish(food.nameTr), foldTurkish(food.nameEn)]
    // Without spaces too, so "kitkat" finds "KitKat" and "cocacola" finds "Coca-Cola"
    key = { names, compact: names.map((n) => n.replace(/\s+/g, '')) }
    searchKeys.set(food, key)
  }
  return key
}

const MAX_RESULTS = 30

/**
 * Every word typed must appear in the Turkish or English name, or in it with the spaces squeezed out. Names
 * that start with the query come first, then names with a word starting with it, then any other match; the
 * catalog order breaks ties.
 */
export function searchFoods(foods: CatalogFood[], query: string): CatalogFood[] {
  const words = foldTurkish(query).split(/\s+/).filter(Boolean)
  if (words.length === 0) return []
  const scored: { food: CatalogFood; score: number; index: number }[] = []
  foods.forEach((food, index) => {
    const { names, compact } = searchKeyOf(food)
    if (!words.every((word) => names.some((name) => name.includes(word)) || compact.some((name) => name.includes(word)))) return
    const first = words[0]
    const score = names.some((n) => n.startsWith(first)) ? 0 : names.some((n) => n.split(/[\s(]+/).some((w) => w.startsWith(first))) ? 1 : 2
    scored.push({ food, score, index })
  })
  return scored
    .sort((a, b) => a.score - b.score || a.index - b.index)
    .slice(0, MAX_RESULTS)
    .map((s) => s.food)
}

export type Amount = { kind: 'servings'; count: number } | { kind: 'grams'; grams: number }

export function amountInGrams(food: CatalogFood, amount: Amount): number {
  return amount.kind === 'grams' ? amount.grams : food.servingG * amount.count
}

/** Calories as whole numbers and grams to one decimal, matching what the backend stores. */
export function nutritionFor(food: CatalogFood, amount: Amount): { caloriesKcal: number; proteinG: number; carbsG: number; fatG: number } {
  const grams = amountInGrams(food, amount)
  const scale = (per100g: number) => Math.round((per100g * grams) / 10) / 10
  return {
    caloriesKcal: Math.round((food.kcalPer100g * grams) / 100),
    proteinG: scale(food.proteinPer100g),
    carbsG: scale(food.carbsPer100g),
    fatG: scale(food.fatPer100g),
  }
}

/** Order the catalog is browsed in: a day from breakfast on, then ingredients, then extras. */
export const CATEGORY_ORDER = ['breakfast', 'dish', 'street', 'fastfood', 'protein', 'supplement', 'dairy', 'grain', 'vegetable', 'fruit', 'nut', 'sweet', 'packaged', 'drink']

/** Categories that have foods, in browsing order: the filter chips above the list. */
export function categoriesOf(foods: CatalogFood[]): string[] {
  const present = new Set(foods.map((food) => food.category))
  const known = CATEGORY_ORDER.filter((category) => present.has(category))
  // A category a newer backend added still gets a chip, after the known ones
  const unknown = [...present].filter((category) => !CATEGORY_ORDER.includes(category)).sort()
  return [...known, ...unknown]
}

export function sortByCategory(foods: CatalogFood[]): CatalogFood[] {
  const rank = (category: string) => {
    const index = CATEGORY_ORDER.indexOf(category)
    return index >= 0 ? index : CATEGORY_ORDER.length
  }
  return foods.map((food, index) => ({ food, index })).sort((a, b) => rank(a.food.category) - rank(b.food.category) || a.index - b.index).map((x) => x.food)
}

/** Half-serving steps from ½ to 10, enough for "1½ portions" without a free-form field. */
export function stepServings(count: number, direction: 1 | -1): number {
  return Math.min(10, Math.max(0.5, count + direction * 0.5))
}

/** The logged name keeps the amount, so "Oats (4 tbsp)" and "Oats (100 g)" stay apart in quick add. */
export function entryName(name: string, serving: string, amount: Amount, formatNumber: (n: number) => string): string {
  const label =
    amount.kind === 'grams' ? `${formatNumber(amount.grams)} g` : amount.count === 1 ? serving : `${formatNumber(amount.count)} × ${serving}`
  return `${name} (${label})`.slice(0, 100)
}
