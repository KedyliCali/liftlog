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
}

const MAX_RESULTS = 30

/**
 * Every word typed must appear in the Turkish or English name. Names that start with the query come
 * first, then names with a word starting with it, then any other match; the catalog order breaks ties.
 */
export function searchFoods(foods: CatalogFood[], query: string): CatalogFood[] {
  const words = foldTurkish(query).split(/\s+/).filter(Boolean)
  if (words.length === 0) return []
  const scored: { food: CatalogFood; score: number; index: number }[] = []
  foods.forEach((food, index) => {
    const names = [foldTurkish(food.nameTr), foldTurkish(food.nameEn)]
    if (!words.every((word) => names.some((name) => name.includes(word)))) return
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

/** Calories as whole numbers and protein to one decimal, matching what the backend stores. */
export function nutritionFor(food: CatalogFood, amount: Amount): { caloriesKcal: number; proteinG: number } {
  const grams = amountInGrams(food, amount)
  return {
    caloriesKcal: Math.round((food.kcalPer100g * grams) / 100),
    proteinG: Math.round((food.proteinPer100g * grams) / 10) / 10,
  }
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
