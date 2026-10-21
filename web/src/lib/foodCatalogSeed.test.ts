import { describe, expect, it } from 'vitest'
import v23 from '../../../backend/src/main/resources/db/migration/V23__more_foods.sql?raw'
import type { CatalogFood } from '../api/types'
import { CATEGORY_ORDER, categoriesOf, searchFoods } from './foodCatalog'
import { t } from './i18n'

// The search run against the real rows V23 adds, so a name that cannot be found typed plainly shows up here

const sqlText = String.raw`'((?:[^']|'')*)'`
const number = String.raw`([\d.]+)`
const row = new RegExp(String.raw`^\s*\((\d+), ${sqlText}, ${sqlText}, ${sqlText}, ${number}, ${number}, ${number}, ${number}, ${number}, ${sqlText}, ${sqlText}\),?$`)
const unquote = (s: string) => s.replace(/''/g, "'")

const seeded: CatalogFood[] = v23
  .split('\n')
  .map((line) => row.exec(line.trimEnd()))
  .filter((match) => match !== null)
  .map((m) => ({
    id: Number(m[1]),
    category: unquote(m[2]),
    nameTr: unquote(m[3]),
    nameEn: unquote(m[4]),
    kcalPer100g: Number(m[5]),
    proteinPer100g: Number(m[6]),
    carbsPer100g: Number(m[7]),
    fatPer100g: Number(m[8]),
    servingG: Number(m[9]),
    servingTr: unquote(m[10]),
    servingEn: unquote(m[11]),
  }))

const namesFor = (query: string) => searchFoods(seeded, query).map((f) => f.nameTr)

describe('V23 seed rows', () => {
  it('parses every row of the migration', () => {
    expect(seeded.length).toBeGreaterThan(700)
    expect(seeded.map((f) => f.id)).toEqual(seeded.map((_, i) => i + 1))
  })

  it('only uses categories the food sheet knows, each with a heading and a chip label', () => {
    for (const category of categoriesOf(seeded)) {
      expect(CATEGORY_ORDER).toContain(category)
      expect(t.foodCategories[category]).toBeTruthy()
      expect(t.foodCategoryChips[category]).toBeTruthy()
    }
  })

  it('finds chain items, sport products and Turkish dishes typed without Turkish letters', () => {
    expect(namesFor('big mac')[0]).toBe("Big Mac (McDonald's)")
    expect(namesFor('mcdonalds').length).toBeGreaterThan(15)
    expect(namesFor('whopper')).toEqual(expect.arrayContaining(['Whopper (Burger King)', 'Double Whopper (Burger King)']))
    expect(namesFor('whey')).toEqual(expect.arrayContaining(['Whey izolat', 'Whey protein (çikolatalı)']))
    expect(namesFor('barebells').length).toBeGreaterThanOrEqual(3)
    expect(namesFor('kreatin')).toEqual(['Kreatin monohidrat'])
    expect(namesFor('creatine')).toEqual(['Kreatin monohidrat'])
    expect(namesFor('coca cola zero')).toEqual(['Coca-Cola Zero'])
    expect(namesFor('ulker')).toEqual(expect.arrayContaining(['Albeni (Ülker)', 'Biskrem (Ülker)']))
    expect(namesFor('sekerpare')).toEqual(['Şekerpare'])
    expect(namesFor('iskender')).toEqual(expect.arrayContaining(['Tavuk iskender']))
    expect(namesFor('islak')).toEqual(expect.arrayContaining(['Islak burger (çift)']))
  })
})
