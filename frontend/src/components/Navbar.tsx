import React, { useState, useEffect } from 'react';
import { Link, useLocation, useNavigate } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import { LiveDot } from './UI';
import { notifApi } from '../services/api';

const NAV_LINKS = [
  { to: '/dashboard',      label: 'Dashboard',     roles: ['PATIENT','ADMIN','DOCTOR'] },
  { to: '/book',           label: 'Book',          roles: ['PATIENT'] },
  { to: '/my-queue',       label: 'My Queue',      roles: ['PATIENT'] },
  { to: '/payment-history',label: 'Payments',      roles: ['PATIENT'] },
  { to: '/admin',          label: 'Admin',         roles: ['ADMIN'] },
  { to: '/doctor-portal',  label: 'Doctor Portal', roles: ['ADMIN','DOCTOR'] },
  { to: '/historical',     label: 'Analytics',     roles: ['ADMIN'] },
  { to: '/ai-engine',      label: 'AI Engine',     roles: ['ADMIN','DOCTOR'] },
];

export const Navbar = () => {
  const { user, logout } = useAuth();
  const location  = useLocation();
  const navigate  = useNavigate();
  const [menuOpen, setMenuOpen] = useState(false);
  const [unread, setUnread]     = useState(0);

  useEffect(() => {
    if (!user) return;
    const poll = async () => {
      try {
        const res = await notifApi.getAll();
        const list: any[] = res.data?.data ?? [];
        setUnread(list.filter((n: any) => n.status === 'UNREAD').length);
      } catch {}
    };
    poll();
    const t = setInterval(poll, 30_000);
    return () => clearInterval(t);
  }, [user]);

  const visibleLinks = NAV_LINKS.filter(l => l.roles.includes(user?.role ?? ''));
  const handleLogout = () => { logout(); navigate('/login'); };

  return (
    <nav className="sticky top-0 z-50 nav-3d">
      <div className="max-w-7xl mx-auto px-4 sm:px-6">
        <div className="flex items-center justify-between h-14">

          {/* Brand */}
          <Link to="/dashboard" className="flex items-center gap-2.5 flex-shrink-0">
            <div className="w-9 h-9 rounded-xl bg-gradient-to-br from-teal-500 via-cyan-500 to-blue-600 flex items-center justify-center shadow-[0_8px_18px_rgba(8,145,178,.28)] glow-pulse">
              <svg className="w-4 h-4 text-white" fill="none" stroke="currentColor" strokeWidth={2.5} viewBox="0 0 24 24">
                <path strokeLinecap="round" d="M22 12h-4l-3 9L9 3l-3 9H2"/>
              </svg>
            </div>
            <div className="hidden sm:block">
              <p className="text-sm font-semibold text-gray-800 leading-none">SmartQueue</p>
              <p className="text-xs text-gray-400 leading-none mt-0.5">Hospital System</p>
            </div>
          </Link>

          {/* Desktop nav */}
          <div className="hidden lg:flex items-center gap-0.5 overflow-x-auto">
            {visibleLinks.map(l => (
              <Link key={l.to} to={l.to}
                className={`px-3 py-1.5 rounded-lg text-sm font-medium transition-colors whitespace-nowrap ${
                  location.pathname.startsWith(l.to)
                    ? 'bg-teal-50 text-teal-700'
                    : 'text-gray-500 hover:text-gray-800 hover:bg-gray-50'
                }`}>
                {l.label}
              </Link>
            ))}
          </div>

          {/* Right side */}
          <div className="flex items-center gap-2">
            <LiveDot />

            {/* Kiosk link for admin */}
            {user?.role === 'ADMIN' && (
              <Link to="/kiosk" target="_blank"
                className="hidden sm:flex items-center gap-1 text-xs text-gray-400 hover:text-teal-600 px-2 py-1 rounded-lg hover:bg-teal-50 transition-colors">
                🖥 Kiosk
              </Link>
            )}

            {/* BUG 5 FIX: Notification bell → /notifications page */}
            {user && (
              <Link to="/notifications"
                className="relative p-1.5 rounded-lg hover:bg-gray-100 transition-colors"
                title="Notifications">
                <svg className="w-5 h-5 text-gray-500" fill="none" stroke="currentColor" strokeWidth={2} viewBox="0 0 24 24">
                  <path strokeLinecap="round" d="M15 17h5l-1.405-1.405A2.032 2.032 0 0118 14.158V11a6.002 6.002 0 00-4-5.659V5a2 2 0 10-4 0v.341C7.67 6.165 6 8.388 6 11v3.159c0 .538-.214 1.055-.595 1.436L4 17h5m6 0v1a3 3 0 11-6 0v-1m6 0H9"/>
                </svg>
                {unread > 0 && (
                  <span className="absolute -top-0.5 -right-0.5 min-w-[16px] h-4 rounded-full bg-red-500 text-white text-[10px] font-bold flex items-center justify-center px-0.5">
                    {unread > 9 ? '9+' : unread}
                  </span>
                )}
              </Link>
            )}

            {/* User chip */}
            {user && (
              <div className="hidden sm:flex items-center gap-2">
                <div className="w-7 h-7 rounded-full bg-teal-100 flex items-center justify-center text-xs font-semibold text-teal-700 flex-shrink-0">
                  {user.name.charAt(0).toUpperCase()}
                </div>
                <div className="hidden xl:block">
                  <p className="text-xs font-medium text-gray-700 leading-none">{user.name}</p>
                  <p className="text-[10px] text-gray-400 mt-0.5">{user.role}</p>
                </div>
              </div>
            )}

            <button onClick={handleLogout}
              className="text-xs text-gray-400 hover:text-red-500 transition-colors px-2 py-1 rounded-lg hover:bg-red-50">
              Logout
            </button>

            {/* Mobile hamburger */}
            <button className="lg:hidden p-1.5 rounded-lg hover:bg-gray-100"
              onClick={() => setMenuOpen(o => !o)}>
              <svg className="w-5 h-5 text-gray-600" fill="none" stroke="currentColor" strokeWidth={2} viewBox="0 0 24 24">
                <path strokeLinecap="round" d={menuOpen ? "M6 18L18 6M6 6l12 12" : "M4 6h16M4 12h16M4 18h16"}/>
              </svg>
            </button>
          </div>
        </div>

        {/* Mobile menu */}
        {menuOpen && (
          <div className="lg:hidden border-t border-gray-100 py-2 flex flex-col gap-1 pb-3">
            {visibleLinks.map(l => (
              <Link key={l.to} to={l.to} onClick={() => setMenuOpen(false)}
                className={`px-3 py-2 rounded-lg text-sm font-medium transition-colors ${
                  location.pathname.startsWith(l.to) ? 'bg-teal-50 text-teal-700' : 'text-gray-500 hover:text-gray-800'
                }`}>
                {l.label}
              </Link>
            ))}
            {/* BUG 5 FIX: Notifications in mobile menu */}
            <Link to="/notifications" onClick={() => setMenuOpen(false)}
              className="px-3 py-2 rounded-lg text-sm font-medium text-gray-500 hover:text-gray-800 flex items-center justify-between">
              <span>Notifications</span>
              {unread > 0 && <span className="bg-red-500 text-white text-xs px-1.5 py-0.5 rounded-full">{unread}</span>}
            </Link>
            {user?.role === 'ADMIN' && (
              <Link to="/kiosk" target="_blank" onClick={() => setMenuOpen(false)}
                className="px-3 py-2 rounded-lg text-sm font-medium text-gray-500 hover:text-gray-800">
                🖥 Kiosk Mode
              </Link>
            )}
            <div className="px-3 py-2 mt-1 border-t border-gray-100">
              <p className="text-xs text-gray-400">{user?.name} · {user?.role}</p>
            </div>
          </div>
        )}
      </div>
    </nav>
  );
};
