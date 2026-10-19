import { afterEach, describe, expect, it, vi } from 'vitest'
import {
  base64UrlToBuffer,
  bufferToBase64Url,
  credentialToJson,
  isUserCancel,
  passkeysSupported,
  toCreationOptions,
  toRequestOptions,
} from './webauthn'

const bytes = (...values: number[]) => new Uint8Array(values).buffer
const asArray = (buffer: BufferSource) => Array.from(new Uint8Array(buffer as ArrayBuffer))

describe('base64url', () => {
  it('round-trips every byte value without padding or + and /', () => {
    const all = new Uint8Array(256).map((_, i) => i)
    const encoded = bufferToBase64Url(all)
    expect(encoded).toMatch(/^[A-Za-z0-9_-]+$/)
    expect(asArray(base64UrlToBuffer(encoded))).toEqual(Array.from(all))
  })

  it('handles each padding length', () => {
    expect(bufferToBase64Url(bytes(0xfb))).toBe('-w')
    expect(bufferToBase64Url(bytes(0xfb, 0xff))).toBe('-_8')
    expect(bufferToBase64Url(bytes(0xfb, 0xff, 0x01))).toBe('-_8B')
    expect(asArray(base64UrlToBuffer('-w'))).toEqual([0xfb])
    expect(asArray(base64UrlToBuffer('-_8'))).toEqual([0xfb, 0xff])
  })

  it('encodes only the viewed part of a typed array', () => {
    const view = new Uint8Array([9, 1, 2, 9]).subarray(1, 3)
    expect(bufferToBase64Url(view)).toBe(bufferToBase64Url(bytes(1, 2)))
  })
})

describe('options from the server', () => {
  it('turns creation options binaries into buffers and keeps the rest', () => {
    const options = toCreationOptions({
      rp: { id: 'liftlog.app', name: 'LiftLog' },
      user: { id: 'AQID', name: 'device', displayName: 'Behlul' },
      challenge: 'BAUG',
      pubKeyCredParams: [{ type: 'public-key', alg: -7 }],
      excludeCredentials: [{ type: 'public-key', id: 'BwgJ', transports: ['internal'] }],
      authenticatorSelection: { residentKey: 'required', userVerification: 'required' },
      attestation: 'none',
      extensions: { credProps: true },
    })
    expect(asArray(options.user.id)).toEqual([1, 2, 3])
    expect(asArray(options.challenge)).toEqual([4, 5, 6])
    expect(asArray(options.excludeCredentials![0].id)).toEqual([7, 8, 9])
    expect(options.excludeCredentials![0].transports).toEqual(['internal'])
    expect(options.user.displayName).toBe('Behlul')
    expect(options.authenticatorSelection?.residentKey).toBe('required')
    expect(options.extensions).toEqual({ credProps: true })
  })

  it('turns request options binaries into buffers, with or without an allow list', () => {
    const open = toRequestOptions({ challenge: 'BAUG', rpId: 'liftlog.app', userVerification: 'required' })
    expect(asArray(open.challenge)).toEqual([4, 5, 6])
    expect(open.allowCredentials).toBeUndefined()
    expect(open.rpId).toBe('liftlog.app')

    const listed = toRequestOptions({ challenge: 'BAUG', allowCredentials: [{ type: 'public-key', id: 'BwgJ' }] })
    expect(asArray(listed.allowCredentials![0].id)).toEqual([7, 8, 9])
  })
})

describe('credential to JSON', () => {
  it('writes a registration answer the server can parse', () => {
    const credential = {
      id: 'AQID',
      rawId: bytes(1, 2, 3),
      type: 'public-key',
      response: { clientDataJSON: bytes(10), attestationObject: bytes(11, 12), getTransports: () => ['internal', 'hybrid'] },
      getClientExtensionResults: () => ({ credProps: { rk: true } }),
    } as unknown as PublicKeyCredential

    expect(credentialToJson(credential)).toEqual({
      id: 'AQID',
      rawId: 'AQID',
      type: 'public-key',
      response: { clientDataJSON: 'Cg', attestationObject: 'Cww', transports: ['internal', 'hybrid'] },
      clientExtensionResults: { credProps: { rk: true } },
    })
  })

  it('writes a sign-in answer with its user handle and base64url extension binaries', () => {
    const credential = {
      id: 'AQID',
      rawId: bytes(1, 2, 3),
      type: 'public-key',
      response: { clientDataJSON: bytes(10), authenticatorData: bytes(13), signature: bytes(14), userHandle: bytes(15) },
      getClientExtensionResults: () => ({ prf: { results: { first: bytes(1, 2, 3) } } }),
    } as unknown as PublicKeyCredential

    expect(credentialToJson(credential)).toEqual({
      id: 'AQID',
      rawId: 'AQID',
      type: 'public-key',
      response: { clientDataJSON: 'Cg', authenticatorData: 'DQ', signature: 'Dg', userHandle: 'Dw' },
      clientExtensionResults: { prf: { results: { first: 'AQID' } } },
    })
  })

  it('leaves out a missing user handle', () => {
    const credential = {
      id: 'x',
      rawId: bytes(1),
      type: 'public-key',
      response: { clientDataJSON: bytes(10), authenticatorData: bytes(13), signature: bytes(14), userHandle: null },
      getClientExtensionResults: () => ({}),
    } as unknown as PublicKeyCredential

    expect(credentialToJson(credential).response).not.toHaveProperty('userHandle')
  })
})

describe('support and cancel', () => {
  afterEach(() => {
    vi.unstubAllGlobals()
    Reflect.deleteProperty(navigator, 'credentials')
  })

  it('needs both PublicKeyCredential and navigator.credentials.create', () => {
    expect(passkeysSupported()).toBe(false)
    vi.stubGlobal('PublicKeyCredential', class {})
    expect(passkeysSupported()).toBe(false)
    Object.defineProperty(navigator, 'credentials', { value: { create: vi.fn(), get: vi.fn() }, configurable: true })
    expect(passkeysSupported()).toBe(true)
  })

  it('treats closing the Face ID sheet as a cancel, and nothing else', () => {
    expect(isUserCancel(new DOMException('closed', 'NotAllowedError'))).toBe(true)
    expect(isUserCancel(new DOMException('aborted', 'AbortError'))).toBe(true)
    expect(isUserCancel(new DOMException('exists', 'InvalidStateError'))).toBe(false)
    expect(isUserCancel(new Error('boom'))).toBe(false)
    expect(isUserCancel(null)).toBe(false)
  })
})
