import { useQuery, useQueryClient } from '@tanstack/react-query'
import { useEffect, useMemo, useRef, useState } from 'react'
import { API_BASE_URL } from '../api/client'
import { api } from '../api/endpoints'
import { queryKeys } from '../api/queryKeys'
import type { Profile } from '../api/types'
import { Sheet } from '../components/Sheet'
import { ProfileSkeleton } from '../components/Skeletons'
import { ErrorState } from '../components/StateViews'
import { useToast } from '../components/contexts'
import { errorMessage, formatKg } from '../lib/format'
import { locale, t } from '../lib/i18n'
import { toAvatarJpeg } from '../lib/image'
import { initials } from '../lib/workoutLogic'
import { bmi, sanitizeDecimal, validateProfile, type ProfileField } from '../lib/validation'

/** True when running from the home screen rather than a browser tab (iOS standalone mode). */
const isStandalone = () =>
  window.matchMedia('(display-mode: standalone)').matches || (navigator as Navigator & { standalone?: boolean }).standalone === true

export function ProfilePage() {
  const queryClient = useQueryClient()
  const toast = useToast()
  const profile = useQuery({ queryKey: queryKeys.profile, queryFn: api.profile })
  const version = profile.data?.avatarVersion ?? null
  const avatar = useQuery({
    queryKey: queryKeys.avatar(version ?? 0),
    queryFn: api.avatar,
    enabled: version !== null,
    // The version changes whenever the photo does, so a cached image never goes stale
    staleTime: Infinity,
  })
  const avatarUrl = useObjectUrl(version !== null ? avatar.data : undefined)
  const [uploading, setUploading] = useState(false)
  const [editing, setEditing] = useState(false)
  const fileInput = useRef<HTMLInputElement>(null)

  const onFile = async (file: File | undefined) => {
    if (!file) return
    setUploading(true)
    try {
      let jpeg: Blob
      try {
        jpeg = await toAvatarJpeg(file)
      } catch {
        toast({ message: t.errorImage })
        return
      }
      const updated = await api.uploadAvatar(jpeg)
      // Seed the cache with the bytes we already have instead of downloading them right back
      if (updated.avatarVersion !== null) queryClient.setQueryData(queryKeys.avatar(updated.avatarVersion), jpeg)
      queryClient.setQueryData(queryKeys.profile, updated)
    } catch (error) {
      toast({ message: errorMessage(error) })
    } finally {
      setUploading(false)
      if (fileInput.current) fileInput.current.value = ''
    }
  }

  const removePhoto = async () => {
    try {
      await api.deleteAvatar()
      queryClient.setQueryData<Profile>(queryKeys.profile, (p) => (p ? { ...p, avatarVersion: null } : p))
    } catch (error) {
      toast({ message: errorMessage(error) })
    }
  }

  const header = (
    <header className="page-header">
      <h1 className="page-title">{t.profileTitle}</h1>
    </header>
  )
  if (profile.isPending) return <div className="page">{header}<ProfileSkeleton /></div>
  if (profile.isError) return <div className="page">{header}<ErrorState error={profile.error} onRetry={() => profile.refetch()} /></div>

  const data = profile.data
  const name = data.displayName ?? t.defaultName
  const bodyMassIndex = bmi(data.heightCm, data.bodyWeightKg)

  return (
    <div className="page">
      {header}
      <div className="profile-card-wrap">
        <div className="profile-card">
          <h2 className="profile-name" data-testid="profile-name">
            {name}
          </h2>
          <div className="row" style={{ justifyContent: 'flex-start', alignItems: 'center' }}>
            <button className="btn btn-tonal" style={{ flex: 'none' }} onClick={() => setEditing(true)} data-testid="edit-profile">
              ✎ {t.editProfile}
            </button>
            {version !== null && (
              <button className="btn btn-text" style={{ flex: 'none' }} onClick={removePhoto}>
                {t.removePhoto}
              </button>
            )}
          </div>
        </div>
        {/* Photo or initials in a ring, with a camera badge overlapping the lower right */}
        <button className="avatar" onClick={() => fileInput.current?.click()} aria-label={t.changePhoto} data-testid="avatar">
          <span className="avatar-circle">
            {avatarUrl ? <img src={avatarUrl} alt="" /> : initials(name)}
            {uploading && (
              <span className="avatar-uploading">
                <span className="spinner small" />
              </span>
            )}
          </span>
          <span className="avatar-badge" aria-hidden>
            📷
          </span>
        </button>
        <input ref={fileInput} type="file" accept="image/*" hidden onChange={(e) => onFile(e.target.files?.[0])} />
      </div>

      <div className="body-stats">
        <BodyStat label={t.height} value={data.heightCm != null ? t.cm(formatKg(data.heightCm)) : null} />
        <BodyStat label={t.bodyWeight} value={data.bodyWeightKg != null ? t.kg(formatKg(data.bodyWeightKg)) : null} />
        <BodyStat label={t.bmi} value={bodyMassIndex != null ? bodyMassIndex.toLocaleString(locale, { maximumFractionDigits: 1, minimumFractionDigits: 1 }) : null} emphasized />
      </div>

      <div className="info-card">
        <div className="info-row">
          <span className="muted">{t.server}</span>
          <span>{API_BASE_URL.replace(/^https?:\/\//, '')}</span>
        </div>
        <div className="info-row">
          <span className="muted">{t.version}</span>
          <span>{__APP_VERSION__}</span>
        </div>
      </div>
      {!isStandalone() && (
        <p className="muted" style={{ fontSize: 13, padding: '16px 4px' }}>
          {t.installHint}
        </p>
      )}

      {editing && <EditProfileSheet profile={data} onClose={() => setEditing(false)} />}
    </div>
  )
}

function BodyStat({ label, value, emphasized = false }: { label: string; value: string | null; emphasized?: boolean }) {
  return (
    <div className={emphasized ? 'body-stat emphasized' : 'body-stat'}>
      <small>{label}</small>
      <strong>{value ?? '—'}</strong>
    </div>
  )
}

/** Object URL for a Blob, revoked when the blob changes or the component unmounts. */
function useObjectUrl(blob: Blob | undefined) {
  const url = useMemo(() => (blob ? URL.createObjectURL(blob) : null), [blob])
  useEffect(() => () => {
    if (url) URL.revokeObjectURL(url)
  }, [url])
  return url
}

function EditProfileSheet({ profile, onClose }: { profile: Profile; onClose: () => void }) {
  const queryClient = useQueryClient()
  const toast = useToast()
  const [name, setName] = useState(profile.displayName ?? '')
  const [height, setHeight] = useState(profile.heightCm != null ? String(profile.heightCm) : '')
  const [weight, setWeight] = useState(profile.bodyWeightKg != null ? String(profile.bodyWeightKg) : '')
  const [errors, setErrors] = useState<Set<ProfileField>>(new Set())
  const [saving, setSaving] = useState(false)

  const save = async () => {
    const result = validateProfile(height, weight)
    if (result.errors.size > 0) {
      setErrors(result.errors)
      return
    }
    setSaving(true)
    try {
      const updated = await api.updateProfile(name.trim() || null, result.heightCm, result.bodyWeightKg)
      queryClient.setQueryData(queryKeys.profile, updated)
      onClose()
    } catch (error) {
      toast({ message: errorMessage(error) })
      setSaving(false)
    }
  }

  const clearError = (field: ProfileField) =>
    setErrors((e) => {
      const next = new Set(e)
      next.delete(field)
      return next
    })

  return (
    <Sheet title={t.editProfile} onClose={onClose}>
      <form className="stack" onSubmit={(e) => (e.preventDefault(), save())}>
        <div className="field">
          <label htmlFor="profile-name">{t.name}</label>
          <input id="profile-name" value={name} maxLength={100} onChange={(e) => setName(e.target.value)} />
        </div>
        <div className="row">
          <div className={errors.has('height') ? 'field invalid' : 'field'}>
            <label htmlFor="profile-height">{t.heightCm}</label>
            <input id="profile-height" inputMode="decimal" value={height} onChange={(e) => (setHeight(sanitizeDecimal(e.target.value, 5)), clearError('height'))} />
            {errors.has('height') && <span className="field-error">{t.errorHeight}</span>}
          </div>
          <div className={errors.has('weight') ? 'field invalid' : 'field'}>
            <label htmlFor="profile-weight">{t.bodyWeightKg}</label>
            <input id="profile-weight" inputMode="decimal" value={weight} onChange={(e) => (setWeight(sanitizeDecimal(e.target.value, 6)), clearError('weight'))} />
            {errors.has('weight') && <span className="field-error">{t.errorBodyWeight}</span>}
          </div>
        </div>
        <button type="submit" className="btn btn-primary btn-block" disabled={saving} data-testid="save-profile">
          {saving ? <span className="spinner small" /> : t.save}
        </button>
      </form>
    </Sheet>
  )
}
