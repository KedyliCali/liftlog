import { describe, expect, it, vi } from 'vitest'
import { displayName, isValidBarcode, lookupBarcode, mapOffProduct, offProductUrl, productAsCatalogFood, type ScannedProduct } from './openFoodFacts'

const ulkerBiscuit = {
  code: '8690504011234',
  status: 1,
  product: {
    product_name: 'Petit Beurre',
    product_name_tr: 'Pötibör Bisküvi',
    brands: 'Ülker, Yıldız Holding',
    serving_size: '5 adet (30 g)',
    serving_quantity: 30,
    nutriments: { 'energy-kcal_100g': 440, proteins_100g: 7.5, carbohydrates_100g: 72.3, fat_100g: 13.04 },
  },
}

function memoryStorage(): Storage {
  const data = new Map<string, string>()
  return {
    get length() {
      return data.size
    },
    clear: () => data.clear(),
    getItem: (key) => data.get(key) ?? null,
    key: (i) => [...data.keys()][i] ?? null,
    removeItem: (key) => void data.delete(key),
    setItem: (key, value) => void data.set(key, value),
  }
}

describe('mapOffProduct', () => {
  it('maps kcal, macros and the printed serving', () => {
    const result = mapOffProduct('8690504011234', ulkerBiscuit, true)
    expect(result).toEqual({
      status: 'found',
      product: {
        code: '8690504011234',
        name: 'Pötibör Bisküvi',
        brand: 'Ülker',
        kcalPer100g: 440,
        proteinPer100g: 7.5,
        carbsPer100g: 72.3,
        fatPer100g: 13,
        servingG: 30,
        servingLabel: '5 adet (30 g)',
      },
    })
  })

  it('uses the default name outside Turkish, and the Turkish one when it is the only name', () => {
    expect(mapOffProduct('1', ulkerBiscuit, false)).toMatchObject({ product: { name: 'Petit Beurre' } })
    const onlyTurkish = { product: { ...ulkerBiscuit.product, product_name: '' } }
    expect(mapOffProduct('1', onlyTurkish, false)).toMatchObject({ product: { name: 'Pötibör Bisküvi' } })
  })

  it('converts kJ to kcal when kcal is missing', () => {
    const kjOnly = { product: { product_name: 'Oat drink', nutriments: { 'energy-kj_100g': 192, proteins_100g: '1.0', fat_100g: 1.5 } } }
    const result = mapOffProduct('1', kjOnly, false)
    expect(result).toMatchObject({ status: 'found', product: { kcalPer100g: 45.9, proteinPer100g: 1, fatPer100g: 1.5 } })
    // The generic energy field is in kJ too
    expect(mapOffProduct('1', { product: { product_name: 'x', nutriments: { energy_100g: 418.4 } } }, false)).toMatchObject({ product: { kcalPer100g: 100 } })
  })

  it('counts missing macros as zero and has no serving when the pack gives none', () => {
    const sparse = { product: { product_name: 'Su', nutriments: { 'energy-kcal_100g': 0 } } }
    expect(mapOffProduct('1', sparse, true)).toEqual({
      status: 'found',
      product: { code: '1', name: 'Su', brand: null, kcalPer100g: 0, proteinPer100g: 0, carbsPer100g: 0, fatPer100g: 0, servingG: null, servingLabel: null },
    })
    // A serving size string alone is useless without its grams
    expect(mapOffProduct('1', { product: { ...sparse.product, serving_size: '1 bottle', serving_quantity: '' } }, true)).toMatchObject({ product: { servingG: null, servingLabel: null } })
  })

  it('builds a serving label from the grams when only the quantity is given', () => {
    const json = { product: { product_name: 'Yogurt', serving_quantity: '150', nutriments: { 'energy-kcal_100g': 60 } } }
    expect(mapOffProduct('1', json, true)).toMatchObject({ product: { servingG: 150, servingLabel: '150 g' } })
  })

  it('reports products without energy as noNutrition, keeping the name for manual entry', () => {
    expect(mapOffProduct('1', { product: { product_name: 'Mystery', nutriments: {} } }, true)).toEqual({ status: 'noNutrition', name: 'Mystery' })
    expect(mapOffProduct('1', { product: { brands: 'X' } }, true)).toEqual({ status: 'noNutrition', name: null })
    // Impossible values are treated as missing
    expect(mapOffProduct('1', { product: { product_name: 'Typo', nutriments: { 'energy-kcal_100g': 4400 } } }, true)).toEqual({ status: 'noNutrition', name: 'Typo' })
  })

  it('falls back to the brand when the product has values but no name', () => {
    expect(mapOffProduct('1', { product: { brands: 'Pınar', nutriments: { 'energy-kcal_100g': 60 } } }, true)).toMatchObject({ product: { name: 'Pınar' } })
  })

  it('reports a missing product as notFound', () => {
    expect(mapOffProduct('1', { status: 0, status_verbose: 'product not found' }, true)).toEqual({ status: 'notFound' })
    expect(mapOffProduct('1', null, true)).toEqual({ status: 'notFound' })
  })
})

describe('helpers', () => {
  it('accepts only 8 to 14 digit codes', () => {
    expect(isValidBarcode('8690504011234')).toBe(true)
    expect(isValidBarcode('12345678')).toBe(true)
    expect(isValidBarcode('1234567')).toBe(false)
    expect(isValidBarcode('86905040112a4')).toBe(false)
  })

  it('adds the brand to the name unless it is already there', () => {
    const product = { name: 'Pötibör', brand: 'Ülker' } as ScannedProduct
    expect(displayName(product)).toBe('Ülker Pötibör')
    expect(displayName({ ...product, name: 'ÜLKER Pötibör' })).toBe('ÜLKER Pötibör')
    expect(displayName({ ...product, brand: null })).toBe('Pötibör')
  })

  it('turns a product into a catalog food with a 100 g serving when none is printed', () => {
    const result = mapOffProduct('1', { product: { product_name: 'Rice', nutriments: { 'energy-kcal_100g': 350 } } }, true)
    if (result.status !== 'found') throw new Error('expected found')
    expect(productAsCatalogFood(result.product)).toMatchObject({ nameTr: 'Rice', servingG: 100, servingTr: '100 g', kcalPer100g: 350 })
  })
})

describe('lookupBarcode', () => {
  it('fetches the product and answers repeat scans from the cache, even offline', async () => {
    const storage = memoryStorage()
    const fetchFn = vi.fn(async () => new Response(JSON.stringify(ulkerBiscuit), { status: 200 }))
    const first = await lookupBarcode('8690504011234', { preferTurkish: true, fetchFn, storage })
    expect(first.status).toBe('found')
    expect(fetchFn).toHaveBeenCalledWith(offProductUrl('8690504011234'), expect.anything())
    expect(offProductUrl('8690504011234')).toBe(
      'https://world.openfoodfacts.org/api/v2/product/8690504011234.json?fields=product_name,product_name_tr,brands,nutriments,serving_size,serving_quantity',
    )

    const offline = vi.fn(async () => {
      throw new TypeError('Failed to fetch')
    })
    const second = await lookupBarcode('8690504011234', { preferTurkish: true, fetchFn: offline, storage })
    expect(second).toEqual(first)
    expect(offline).not.toHaveBeenCalled()
  })

  it('treats a 404 as not found and does not cache misses', async () => {
    const storage = memoryStorage()
    const fetchFn = vi.fn(async () => new Response(JSON.stringify({ status: 0 }), { status: 404 }))
    expect(await lookupBarcode('12345678', { preferTurkish: true, fetchFn, storage })).toEqual({ status: 'notFound' })
    await lookupBarcode('12345678', { preferTurkish: true, fetchFn, storage })
    expect(fetchFn).toHaveBeenCalledTimes(2)
  })

  it('throws on server errors so the user can retry', async () => {
    const fetchFn = vi.fn(async () => new Response('busy', { status: 503 }))
    await expect(lookupBarcode('12345678', { preferTurkish: true, fetchFn, storage: memoryStorage() })).rejects.toThrow('503')
  })

  it('still works when storage is broken', async () => {
    const broken = memoryStorage()
    broken.getItem = () => {
      throw new Error('denied')
    }
    broken.setItem = () => {
      throw new Error('quota')
    }
    const fetchFn = vi.fn(async () => new Response(JSON.stringify(ulkerBiscuit), { status: 200 }))
    expect((await lookupBarcode('8690504011234', { preferTurkish: true, fetchFn, storage: broken })).status).toBe('found')
  })
})
