import type { CSSProperties } from 'react'
import { NavLink, Outlet, useLocation } from 'react-router'
import { t } from '../lib/i18n'
import { setNavDirection } from '../lib/navDirection'
import { useTabSwipe } from './useTabSwipe'
import { ChartIcon, ControllerIcon, DumbbellIcon, PersonIcon, PlateIcon } from './Icons'

const tabs = [
  { to: '/', label: t.navWorkouts, icon: <DumbbellIcon />, end: true },
  { to: '/nutrition', label: t.navNutrition, icon: <PlateIcon />, end: false },
  { to: '/progress', label: t.navProgress, icon: <ChartIcon />, end: false },
  { to: '/game', label: t.navGame, icon: <ControllerIcon />, end: false },
  { to: '/profile', label: t.navProfile, icon: <PersonIcon />, end: false },
]

/** Index of the tab owning the current path; detail pages belong to the workouts tab. */
function activeTabIndex(pathname: string): number {
  const index = tabs.findIndex((tab) => tab.to !== '/' && pathname.startsWith(tab.to))
  return index >= 0 ? index : 0
}

export function Layout() {
  const { pathname } = useLocation()
  useTabSwipe()
  const activeIndex = activeTabIndex(pathname)
  return (
    <div className="app">
      <Outlet />
      <nav className="bottom-nav" aria-label="Main">
        <div className="bottom-nav-inner" style={{ '--active-tab': activeIndex, '--tab-count': tabs.length } as CSSProperties}>
          {/* One pill slides between tabs instead of each tab fading its own background */}
          <span className="nav-indicator" aria-hidden />
          {tabs.map((tab, index) => (
            <NavLink
              key={tab.to}
              to={tab.to}
              end={tab.end}
              viewTransition
              onClick={() => index !== activeIndex && setNavDirection(index > activeIndex ? 'forward' : 'back')}
              className={({ isActive }) => (isActive ? 'nav-item active' : 'nav-item')}>
              <span className="nav-icon">{tab.icon}</span>
              {tab.label}
            </NavLink>
          ))}
        </div>
      </nav>
    </div>
  )
}
