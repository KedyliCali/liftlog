import { describe, expect, it } from 'vitest'
import type { CatalogFood } from '../api/types'
import { entryName, foldTurkish, nutritionFor, searchFoods, stepServings } from './foodCatalog'

const food = (id: number, nameTr: string, nameEn: string, kcalPer100g = 100, proteinPer100g = 10, servingG = 100): CatalogFood => ({
  id,
  category: 'dish',
  nameTr,
  nameEn,
  kcalPer100g,
  proteinPer100g,
  servingG,
  servingTr: '1 porsiyon',
  servingEn: '1 serving',
})

const catalog = [
  food(1, 'Mercimek çorbası', 'Red lentil soup', 60, 3.2, 250),
  food(2, 'Haşlanmış mercimek', 'Cooked lentils'),
  food(3, 'Izgara tavuk göğsü', 'Grilled chicken breast', 165, 31, 150),
  food(4, 'Tavuk şiş', 'Chicken shish'),
  food(5, 'Şehriyeli tavuk çorbası', 'Chicken noodle soup'),
]

describe('foldTurkish', () => {
  it('ignores Turkish letters and case', () => {
    expect(foldTurkish('IZGARA Çorbası ŞİŞ Göğüs')).toBe('izgara corbasi sis gogus')
  })
})

describe('searchFoods', () => {
  it('finds names typed without Turkish characters', () => {
    expect(searchFoods(catalog, 'corba').map((f) => f.id)).toEqual([1, 5])
    expect(searchFoods(catalog, 'gogsu').map((f) => f.id)).toEqual([3])
  })

  it('needs every word, and also searches the English name', () => {
    expect(searchFoods(catalog, 'tavuk corba').map((f) => f.id)).toEqual([5])
    expect(searchFoods(catalog, 'chicken').map((f) => f.id)).toEqual([4, 5, 3])
  })

  it('ranks names starting with the query first', () => {
    expect(searchFoods(catalog, 'tavuk').map((f) => f.id)).toEqual([4, 3, 5])
    expect(searchFoods(catalog, 'mercimek').map((f) => f.id)).toEqual([1, 2])
  })

  it('returns nothing for an empty query', () => {
    expect(searchFoods(catalog, '  ')).toEqual([])
  })
})

describe('nutritionFor', () => {
  it('scales per-100 g values by servings or grams', () => {
    expect(nutritionFor(catalog[0], { kind: 'servings', count: 1 })).toEqual({ caloriesKcal: 150, proteinG: 8 })
    expect(nutritionFor(catalog[2], { kind: 'servings', count: 1.5 })).toEqual({ caloriesKcal: 371, proteinG: 69.8 })
    expect(nutritionFor(catalog[2], { kind: 'grams', grams: 120 })).toEqual({ caloriesKcal: 198, proteinG: 37.2 })
  })
})

describe('serving steps and names', () => {
  it('moves in halves between ½ and 10', () => {
    expect(stepServings(1, 1)).toBe(1.5)
    expect(stepServings(0.5, -1)).toBe(0.5)
    expect(stepServings(10, 1)).toBe(10)
  })

  it('keeps the amount in the logged name', () => {
    const fmt = (n: number) => String(n).replace('.', ',')
    expect(entryName('Mercimek çorbası', '1 kase', { kind: 'servings', count: 1 }, fmt)).toBe('Mercimek çorbası (1 kase)')
    expect(entryName('Mercimek çorbası', '1 kase', { kind: 'servings', count: 1.5 }, fmt)).toBe('Mercimek çorbası (1,5 × 1 kase)')
    expect(entryName('Yulaf ezmesi', '4 yemek kaşığı', { kind: 'grams', grams: 60 }, fmt)).toBe('Yulaf ezmesi (60 g)')
  })
})
