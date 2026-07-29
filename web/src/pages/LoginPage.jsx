import React, { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { login, register } from '../api/client'

const s = {
  page: { display: 'flex', alignItems: 'center', justifyContent: 'center', minHeight: '100vh', background: '#0f1117' },
  card: { background: '#1a1d27', border: '1px solid #2d3348', borderRadius: 12, padding: '2.5rem', width: 360 },
  title: { fontSize: '1.6rem', fontWeight: 700, color: '#6366f1', marginBottom: '0.25rem' },
  sub: { color: '#64748b', fontSize: '0.85rem', marginBottom: '2rem' },
  label: { display: 'block', color: '#94a3b8', fontSize: '0.8rem', marginBottom: '0.35rem', fontWeight: 500 },
  input: { width: '100%', background: '#0f1117', border: '1px solid #2d3348', borderRadius: 8, padding: '0.7rem 1rem', color: '#e2e8f0', fontSize: '0.9rem', marginBottom: '1rem', outline: 'none' },
  btn: { width: '100%', background: '#6366f1', color: '#fff', border: 'none', borderRadius: 8, padding: '0.75rem', fontSize: '0.95rem', fontWeight: 600, cursor: 'pointer', marginTop: '0.5rem' },
  toggle: { textAlign: 'center', marginTop: '1.25rem', color: '#64748b', fontSize: '0.85rem' },
  link: { color: '#6366f1', cursor: 'pointer', textDecoration: 'underline' },
  error: { background: '#2d1b1b', border: '1px solid #7f1d1d', borderRadius: 8, padding: '0.65rem 1rem', color: '#fca5a5', fontSize: '0.85rem', marginBottom: '1rem' },
}

export default function LoginPage() {
  const navigate = useNavigate()
  const [mode, setMode] = useState('login')
  const [username, setUsername] = useState('')
  const [password, setPassword] = useState('')
  const [error, setError] = useState('')
  const [loading, setLoading] = useState(false)

  const submit = async (e) => {
    e.preventDefault()
    setLoading(true); setError('')
    try {
      if (mode === 'login') {
        const { data } = await login(username, password)
        localStorage.setItem('token', data.access_token)
        navigate('/dashboard')
      } else {
        await register(username, password)
        const { data } = await login(username, password)
        localStorage.setItem('token', data.access_token)
        navigate('/dashboard')
      }
    } catch (err) {
      setError(err.response?.data?.detail || 'An error occurred')
    } finally {
      setLoading(false)
    }
  }

  return (
    <div style={s.page}>
      <div style={s.card}>
        <div style={s.title}>HealthTracker</div>
        <div style={s.sub}>{mode === 'login' ? 'Sign in to your account' : 'Create a new account'}</div>
        {error && <div style={s.error}>{error}</div>}
        <form onSubmit={submit}>
          <label style={s.label}>Username</label>
          <input style={s.input} value={username} onChange={e => setUsername(e.target.value)} autoFocus required />
          <label style={s.label}>Password</label>
          <input style={s.input} type="password" value={password} onChange={e => setPassword(e.target.value)} required />
          <button style={s.btn} disabled={loading}>{loading ? 'Please wait…' : mode === 'login' ? 'Sign In' : 'Create Account'}</button>
        </form>
        <div style={s.toggle}>
          {mode === 'login' ? <>No account? <span style={s.link} onClick={() => setMode('register')}>Register</span></> : <>Have an account? <span style={s.link} onClick={() => setMode('login')}>Sign in</span></>}
        </div>
      </div>
    </div>
  )
}
