import type { ReactNode } from 'react'

// One stroke icon set for the whole app (24×24 grid, 2px round strokes), so empty states, badges and
// buttons share a single drawn style instead of platform emoji that look different on every phone.

function Icon({ size = 24, children }: { size?: number; children: ReactNode }) {
  return (
    <svg width={size} height={size} viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" aria-hidden>
      {children}
    </svg>
  )
}

export function DumbbellIcon({ size }: { size?: number }) {
  return (
    <Icon size={size}>
      <path d="M6.5 6.5l11 11M4 9l5-5M15 20l5-5M3 12l1-1M12 3l-1 1M21 12l-1 1M12 21l1-1" />
    </Icon>
  )
}

export function PlateIcon({ size }: { size?: number }) {
  return (
    <Icon size={size}>
      <circle cx="13" cy="13" r="7" />
      <circle cx="13" cy="13" r="3" />
      <path d="M3 3v6a2 2 0 0 0 2 2M5 3v18M7 3v6a2 2 0 0 1-2 2" />
    </Icon>
  )
}

export function ChartIcon({ size }: { size?: number }) {
  return (
    <Icon size={size}>
      <path d="M3 17l6-6 4 4 8-8" />
      <path d="M15 7h6v6" />
    </Icon>
  )
}

export function PersonIcon({ size }: { size?: number }) {
  return (
    <Icon size={size}>
      <circle cx="12" cy="8" r="4" />
      <path d="M4 21c1.5-4 4.5-6 8-6s6.5 2 8 6" />
    </Icon>
  )
}

export function TrophyIcon({ size }: { size?: number }) {
  return (
    <Icon size={size}>
      <path d="M8 4h8v5a4 4 0 0 1-8 0V4z" />
      <path d="M8 6H5a3 3 0 0 0 3 4M16 6h3a3 3 0 0 1-3 4M12 13v4M8 20h8M10 17h4" />
    </Icon>
  )
}

export function AlertIcon({ size }: { size?: number }) {
  return (
    <Icon size={size}>
      <path d="M12 3l10 18H2L12 3z" />
      <path d="M12 10v4M12 17.5v.01" />
    </Icon>
  )
}

export function PencilIcon({ size = 16 }: { size?: number }) {
  return (
    <Icon size={size}>
      <path d="M4 20h4L19 9l-4-4L4 16v4zM13.5 6.5l4 4" />
    </Icon>
  )
}

export function TrashIcon({ size = 20 }: { size?: number }) {
  return (
    <Icon size={size}>
      <path d="M4 7h16M10 11v6M14 11v6M6 7l1 13h10l1-13M9 7V4h6v3" />
    </Icon>
  )
}

/** A figure hanging from a bar: the calisthenics filter and badge. */
export function BodyweightIcon({ size = 16 }: { size?: number }) {
  return (
    <Icon size={size}>
      <path d="M3 3h18M8 3v4l4 3 4-3V3" />
      <circle cx="12" cy="13" r="2" />
      <path d="M12 15v4M9 21l3-2 3 2" />
    </Icon>
  )
}

/** Bars of different weights, like a real barcode, rather than evenly spaced lines. */
export function BarcodeIcon({ size = 20 }: { size?: number }) {
  return (
    <Icon size={size}>
      <path d="M3 5v14M6 5v14M10 5v14M13 5v14M17 5v14M21 5v14" />
      <path d="M8 5v14M19 5v14" strokeWidth="1" />
    </Icon>
  )
}

export function CameraIcon({ size = 16 }: { size?: number }) {
  return (
    <Icon size={size}>
      <path d="M4 8h3l2-3h6l2 3h3v11H4V8z" />
      <circle cx="12" cy="13" r="3.5" />
    </Icon>
  )
}

export function ControllerIcon({ size }: { size?: number }) {
  return (
    <Icon size={size}>
      <path d="M6 9h12a4 4 0 0 1 3.9 4.9l-.8 3.4a2.2 2.2 0 0 1-3.8 1L15 16H9l-2.3 2.3a2.2 2.2 0 0 1-3.8-1l-.8-3.4A4 4 0 0 1 6 9z" />
      <path d="M8 12v3M6.5 13.5h3M15.5 12.5v.01M17.5 14.5v.01" />
    </Icon>
  )
}

export function KeyIcon({ size = 20 }: { size?: number }) {
  return (
    <Icon size={size}>
      <circle cx="8" cy="15" r="4" />
      <path d="M10.8 12.2L20 3M16 7l3 3M14 9l2 2" />
    </Icon>
  )
}

/** Face scan frame: four corners around eyes, nose and smile, like the system Face ID glyph. */
export function FaceIdIcon({ size = 20 }: { size?: number }) {
  return (
    <Icon size={size}>
      <path d="M3 8V5a2 2 0 0 1 2-2h3M16 3h3a2 2 0 0 1 2 2v3M21 16v3a2 2 0 0 1-2 2h-3M8 21H5a2 2 0 0 1-2-2v-3" />
      <path d="M9 9v1M15 9v1M12 9v4h-1M9 16a4 4 0 0 0 6 0" />
    </Icon>
  )
}

export function CheckIcon({ size = 16 }: { size?: number }) {
  return (
    <Icon size={size}>
      <path d="M5 12.5l4.5 4.5L19 7.5" />
    </Icon>
  )
}

export function ChevronIcon({ size = 18 }: { size?: number }) {
  return (
    <Icon size={size}>
      <path d="M9 6l6 6-6 6" />
    </Icon>
  )
}

export function ShareIcon({ size = 18 }: { size?: number }) {
  return (
    <Icon size={size}>
      <path d="M12 3v12M8 7l4-4 4 4" />
      <path d="M8 11H6a2 2 0 0 0-2 2v6a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2v-6a2 2 0 0 0-2-2h-2" />
    </Icon>
  )
}

/** Three sliders: settings without a gear's many small teeth, which blur at 20px. */
export function SettingsIcon({ size = 20 }: { size?: number }) {
  return (
    <Icon size={size}>
      <path d="M4 6h9M17 6h3M4 12h3M11 12h9M4 18h11M19 18h1" />
      <circle cx="15" cy="6" r="2" />
      <circle cx="9" cy="12" r="2" />
      <circle cx="17" cy="18" r="2" />
    </Icon>
  )
}
