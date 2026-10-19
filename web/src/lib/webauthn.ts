// WebAuthn (passkeys) speaks ArrayBuffers, the server speaks JSON with binary values as base64url. These
// helpers translate both ways by hand, because PublicKeyCredential.parseCreationOptionsFromJSON and
// credential.toJSON() are missing on older iOS versions.

/** Creation options as the server sends them: navigator.credentials.create's options with base64url binaries. */
export interface CreationOptionsJson {
  rp: { id?: string; name: string }
  user: { id: string; name: string; displayName: string }
  challenge: string
  pubKeyCredParams: { type: 'public-key'; alg: number }[]
  timeout?: number
  excludeCredentials?: CredentialDescriptorJson[]
  authenticatorSelection?: AuthenticatorSelectionCriteria
  attestation?: AttestationConveyancePreference
  extensions?: Record<string, unknown>
  [other: string]: unknown
}

/** Request (sign-in) options as the server sends them. */
export interface RequestOptionsJson {
  challenge: string
  timeout?: number
  rpId?: string
  allowCredentials?: CredentialDescriptorJson[]
  userVerification?: UserVerificationRequirement
  extensions?: Record<string, unknown>
  [other: string]: unknown
}

export interface CredentialDescriptorJson {
  type: 'public-key'
  id: string
  transports?: string[]
}

/** What the server's parseRegistrationResponseJson / parseAssertionResponseJson read. */
export interface CredentialJson {
  id: string
  rawId: string
  type: 'public-key'
  response: Record<string, unknown>
  clientExtensionResults: Record<string, unknown>
}

export function base64UrlToBuffer(value: string): ArrayBuffer {
  const base64 = value.replace(/-/g, '+').replace(/_/g, '/')
  const padded = base64 + '='.repeat((4 - (base64.length % 4)) % 4)
  const binary = atob(padded)
  const bytes = new Uint8Array(binary.length)
  for (let i = 0; i < binary.length; i++) bytes[i] = binary.charCodeAt(i)
  return bytes.buffer
}

export function bufferToBase64Url(value: ArrayBuffer | ArrayBufferView): string {
  const bytes = value instanceof ArrayBuffer ? new Uint8Array(value) : new Uint8Array(value.buffer, value.byteOffset, value.byteLength)
  let binary = ''
  // Byte by byte: spreading a large array into String.fromCharCode can overflow the call stack
  for (const byte of bytes) binary += String.fromCharCode(byte)
  return btoa(binary).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '')
}

function toDescriptor(descriptor: CredentialDescriptorJson): PublicKeyCredentialDescriptor {
  return { ...descriptor, id: base64UrlToBuffer(descriptor.id), transports: descriptor.transports as AuthenticatorTransport[] | undefined }
}

export function toCreationOptions(json: CreationOptionsJson): PublicKeyCredentialCreationOptions {
  return {
    ...json,
    challenge: base64UrlToBuffer(json.challenge),
    user: { ...json.user, id: base64UrlToBuffer(json.user.id) },
    excludeCredentials: json.excludeCredentials?.map(toDescriptor),
  } as PublicKeyCredentialCreationOptions
}

export function toRequestOptions(json: RequestOptionsJson): PublicKeyCredentialRequestOptions {
  return {
    ...json,
    challenge: base64UrlToBuffer(json.challenge),
    allowCredentials: json.allowCredentials?.map(toDescriptor),
  } as PublicKeyCredentialRequestOptions
}

/** Extension outputs may hold binary values (prf, largeBlob); everything binary becomes base64url. */
function jsonSafe(value: unknown): unknown {
  if (value instanceof ArrayBuffer || ArrayBuffer.isView(value)) return bufferToBase64Url(value)
  if (Array.isArray(value)) return value.map(jsonSafe)
  if (value !== null && typeof value === 'object') {
    return Object.fromEntries(Object.entries(value).map(([key, inner]) => [key, jsonSafe(inner)]))
  }
  return value
}

/**
 * Registration and sign-in answers in one function: the response's own fields tell them apart (duck typing,
 * so it also works where AuthenticatorAttestationResponse is not a global, like the test environment).
 */
export function credentialToJson(credential: PublicKeyCredential): CredentialJson {
  const response = credential.response as AuthenticatorResponse & Partial<AuthenticatorAttestationResponse & AuthenticatorAssertionResponse>
  const json: Record<string, unknown> = { clientDataJSON: bufferToBase64Url(response.clientDataJSON) }
  if (response.attestationObject) {
    json.attestationObject = bufferToBase64Url(response.attestationObject)
    const transports = response.getTransports?.()
    if (transports) json.transports = transports
  } else {
    json.authenticatorData = bufferToBase64Url(response.authenticatorData!)
    json.signature = bufferToBase64Url(response.signature!)
    // A discoverable credential always has one; the server maps it back to the account
    if (response.userHandle) json.userHandle = bufferToBase64Url(response.userHandle)
  }
  return {
    id: credential.id,
    rawId: bufferToBase64Url(credential.rawId),
    type: 'public-key',
    response: json,
    clientExtensionResults: jsonSafe(credential.getClientExtensionResults?.() ?? {}) as Record<string, unknown>,
  }
}

export function passkeysSupported(): boolean {
  return typeof window !== 'undefined' && 'PublicKeyCredential' in window && window.PublicKeyCredential != null &&
    typeof navigator.credentials?.create === 'function'
}

/** Closing the Face ID sheet (or the browser cancelling it) is not an error worth a message. */
export function isUserCancel(error: unknown): boolean {
  const name = (error as { name?: unknown } | null)?.name
  return name === 'NotAllowedError' || name === 'AbortError'
}
