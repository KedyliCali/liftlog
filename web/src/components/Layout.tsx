import { NavLink, Outlet } from 'react-router'
import { t } from '../lib/i18n'

const tabs = [
  { to: '/', label: t.navWorkouts, icon: <DumbbellIcon />, end: true },
  { to: '/progress', label: t.navProgress, icon: <ChartIcon />, end: false },
  { to: '/profile', label: t.navProfile, icon: <PersonIcon />, end: false },
]

export function Layout() {
  return (
    <div className="app">
      <Outlet />
      <nav className="bottom-nav" aria-label="Main">
        <div className="bottom-nav-inner">
          {tabs.map((tab) => (
            <NavLink key={tab.to} to={tab.to} end={tab.end} className={({ isActive }) => (isActive ? 'nav-item active' : 'nav-item')}>
              <span className="nav-icon">{tab.icon}</span>
              {tab.label}
            </NavLink>
          ))}
        </div>
      </nav>
    </div>
  )
}

function DumbbellIcon() {
  return (
    <svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" aria-hidden>
      <path d="M6.5 6.5l11 11M4 9l5-5M15 20l5-5M3 12l1-1M12 3l-1 1M21 12l-1 1M12 21l1-1" />
    </svg>
  )
}

function ChartIcon() {
  return (
    <svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" aria-hidden>
      <path d="M3 17l6-6 4 4 8-8" />
    </svg>
  )
}

function PersonIcon() {
  return (
    <svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" aria-hidden>
      <circle cx="12" cy="8" r="4" />
      <path d="M4 21c1.5-4 4.5-6 8-6s6.5 2 8 6" strokeLinecap="round" />
    </svg>
  )
}
