import React from 'react'
import { Routes, Route, Navigate, NavLink, useNavigate } from 'react-router-dom'
import LoginPage from './pages/LoginPage'
import DashboardPage from './pages/DashboardPage'
import InsightsPage from './pages/InsightsPage'
import CorrelationsPage from './pages/CorrelationsPage'
import LogPage from './pages/LogPage'

const isAuthenticated = () => !!localStorage.getItem('token')

function PrivateRoute({ children }) {
  return isAuthenticated() ? children : <Navigate to="/login" replace />
}

const navStyle = {
  display: 'flex', gap: '1.5rem', padding: '1rem 2rem',
  background: '#1a1d27', borderBottom: '1px solid #2d3348',
  alignItems: 'center',
}
const linkStyle = { color: '#94a3b8', textDecoration: 'none', fontSize: '0.9rem', fontWeight: 500 }
const activeLinkStyle = { ...linkStyle, color: '#6366f1' }

function Nav() {
  const navigate = useNavigate()
  const logout = () => { localStorage.removeItem('token'); navigate('/login') }
  return (
    <nav style={navStyle}>
      <span style={{ color: '#6366f1', fontWeight: 700, marginRight: '1rem' }}>HealthTracker</span>
      <NavLink to="/dashboard" style={({ isActive }) => isActive ? activeLinkStyle : linkStyle}>Dashboard</NavLink>
      <NavLink to="/log" style={({ isActive }) => isActive ? activeLinkStyle : linkStyle}>Log</NavLink>
      <NavLink to="/correlations" style={({ isActive }) => isActive ? activeLinkStyle : linkStyle}>Correlations</NavLink>
      <NavLink to="/insights" style={({ isActive }) => isActive ? activeLinkStyle : linkStyle}>Insights</NavLink>
      <button onClick={logout} style={{ marginLeft: 'auto', background: 'none', border: '1px solid #2d3348', color: '#94a3b8', padding: '0.4rem 0.8rem', borderRadius: 6, cursor: 'pointer', fontSize: '0.85rem' }}>Logout</button>
    </nav>
  )
}

export default function App() {
  return (
    <>
      {isAuthenticated() && <Nav />}
      <Routes>
        <Route path="/login" element={<LoginPage />} />
        <Route path="/dashboard" element={<PrivateRoute><DashboardPage /></PrivateRoute>} />
        <Route path="/log" element={<PrivateRoute><LogPage /></PrivateRoute>} />
        <Route path="/correlations" element={<PrivateRoute><CorrelationsPage /></PrivateRoute>} />
        <Route path="/insights" element={<PrivateRoute><InsightsPage /></PrivateRoute>} />
        <Route path="*" element={<Navigate to={isAuthenticated() ? '/dashboard' : '/login'} replace />} />
      </Routes>
    </>
  )
}
