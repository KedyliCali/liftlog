import { describe, expect, it } from 'vitest'
import type { CatalogFood } from '../api/types'
import { categoriesOf, entryName, foldTurkish, nutritionFor, searchFoods, sortByCategory, stepServings } from './foodCatalog'

const food = (id: number, nameTr: string, nameEn: string, kcalPer100g = 100, proteinPer100g = 10, servingG = 100, category = 'dish'): CatalogFood => ({
  id,
  category,
  nameTr,
  nameEn,
  kcalPer100g,
  proteinPer100g,
  carbsPer100g: 10,
  fatPer100g: 2,
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

  it('drops apostrophes and turns hyphens into spaces, as brand names are typed', () => {
    expect(foldTurkish("McDonald's")).toBe('mcdonalds')
    expect(foldTurkish('Coca-Cola')).toBe('coca cola')
    expect(foldTurkish('Simit Sarayı')).toBe('simit sarayi')
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

  it('finds brands typed without apostrophes, hyphens or spaces', () => {
    const brands = [
      food(10, "Big Mac (McDonald's)", "Big Mac (McDonald's)"),
      food(11, 'Coca-Cola Zero', 'Coca-Cola Zero'),
      food(12, 'KitKat (Nestlé)', 'KitKat (Nestle)'),
      food(13, 'Whey protein (çikolatalı)', 'Whey protein (chocolate)'),
    ]
    expect(searchFoods(brands, 'mcdonalds').map((f) => f.id)).toEqual([10])
    expect(searchFoods(brands, 'big mac').map((f) => f.id)).toEqual([10])
    expect(searchFoods(brands, 'coca cola').map((f) => f.id)).toEqual([11])
    expect(searchFoods(brands, 'cocacola').map((f) => f.id)).toEqual([11])
    expect(searchFoods(brands, 'kit kat').map((f) => f.id)).toEqual([12])
    expect(searchFoods(brands, 'kitkat').map((f) => f.id)).toEqual([12])
    expect(searchFoods(brands, 'WHEY cikolatali').map((f) => f.id)).toEqual([13])
  })

  it('returns nothing for an empty query', () => {
    expect(searchFoods(catalog, '  ')).toEqual([])
  })
})

describe('nutritionFor', () => {
  it('scales per-100 g values by servings or grams', () => {
    expect(nutritionFor(catalog[0], { kind: 'servings', count: 1 })).toEqual({ caloriesKcal: 150, proteinG: 8, carbsG: 25, fatG: 5 })
    expect(nutritionFor(catalog[2], { kind: 'servings', count: 1.5 })).toMatchObject({ caloriesKcal: 371, proteinG: 69.8 })
    expect(nutritionFor(catalog[2], { kind: 'grams', grams: 120 })).toEqual({ caloriesKcal: 198, proteinG: 37.2, carbsG: 12, fatG: 2.4 })
  })
})

describe('sortByCategory', () => {
  it('lists breakfast first and keeps catalog order within a category', () => {
    const foods = [food(1, 'Kola', 'Cola', 42, 0, 330, 'drink'), food(2, 'Omlet', 'Omelette', 154, 11, 120, 'breakfast'), food(3, 'Pişi', 'Pisi', 330, 7, 60, 'breakfast')]
    expect(sortByCategory(foods).map((f) => f.id)).toEqual([2, 3, 1])
  })
})

describe('categoriesOf', () => {
  it('lists the categories that have foods in browsing order, unknown ones last', () => {
    const foods = [
      food(1, 'Kola', 'Cola', 42, 0, 330, 'drink'),
      food(2, 'Whey', 'Whey', 400, 80, 30, 'supplement'),
      food(3, 'Big Mac', 'Big Mac', 251, 11, 219, 'fastfood'),
      food(4, 'Omlet', 'Omelette', 154, 11, 120, 'breakfast'),
      food(5, 'Gizemli', 'Mystery', 100, 1, 100, 'zzz'),
      food(6, 'Pişi', 'Pisi', 330, 7, 60, 'breakfast'),
    ]
    expect(categoriesOf(foods)).toEqual(['breakfast', 'fastfood', 'supplement', 'drink', 'zzz'])
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
