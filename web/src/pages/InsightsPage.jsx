import React, { useEffect, useState } from 'react'
import { getPatterns } from '../api/client'

const SEVERITY_COLORS = {
  alert: { bg: '#2d1b1b', border: '#7f1d1d', badge: '#7f1d1d', text: '#fca5a5', badgeText: '#fca5a5' },
  warning: { bg: '#2d2506', border: '#78350f', badge: '#92400e', text: '#fcd34d', badgeText: '#fcd34d' },
  info: { bg: '#0f2034', border: '#1e3a5f', badge: '#1e3a8a', text: '#93c5fd', badgeText: '#93c5fd' },
}

const s = {
  page: { padding: '2rem', maxWidth: 900, margin: '0 auto' },
  heading: { fontSize: '1.4rem', fontWeight: 700, color: '#e2e8f0', marginBottom: '0.5rem' },
  sub: { color: '#64748b', fontSize: '0.85rem', marginBottom: '2rem' },
  empty: { color: '#64748b', background: '#1a1d27', border: '1px solid #2d3348', borderRadius: 12, padding: '2.5rem', textAlign: 'center' },
  daysBtn: { background: 'none', border: '1px solid #2d3348', borderRadius: 6, padding: '0.3rem 0.7rem', color: '#94a3b8', cursor: 'pointer', fontSize: '0.8rem' },
  daysBtnActive: { background: '#6366f1', border: '1px solid #6366f1', borderRadius: 6, padding: '0.3rem 0.7rem', color: '#fff', cursor: 'pointer', fontSize: '0.8rem' },
}

function AlertCard({ alert }) {
  const [expanded, setExpanded] = useState(false)
  const col = SEVERITY_COLORS[alert.severity] || SEVERITY_COLORS.info
  return (
    <div style={{ background: col.bg, border: `1px solid ${col.border}`, borderRadius: 12, padding: '1.25rem', marginBottom: '1rem' }}>
      <div style={{ display: 'flex', alignItems: 'center', gap: '0.75rem', marginBottom: '0.5rem' }}>
        <span style={{ background: col.badge, color: col.badgeText, borderRadius: 6, padding: '0.2rem 0.6rem', fontSize: '0.7rem', fontWeight: 700, textTransform: 'uppercase' }}>{alert.severity}</span>
        <span style={{ color: '#e2e8f0', fontWeight: 600, fontSize: '1rem' }}>{alert.title}</span>
      </div>
      <p style={{ color: col.text, fontSize: '0.9rem', lineHeight: 1.6, marginBottom: '0.75rem' }}>{alert.description}</p>
      <div style={{ background: 'rgba(0,0,0,0.2)', borderRadius: 8, padding: '0.75rem', fontSize: '0.8rem', color: '#94a3b8', lineHeight: 1.6, borderLeft: `3px solid ${col.border}` }}>
        <span style={{ fontWeight: 600, color: '#64748b', fontSize: '0.7rem', textTransform: 'uppercase', letterSpacing: '0.05em' }}>Science Note  </span>
        {alert.science_note}
      </div>
      <button
        onClick={() => setExpanded(!expanded)}
        style={{ marginTop: '0.75rem', background: 'none', border: 'none', color: '#64748b', cursor: 'pointer', fontSize: '0.8rem' }}
      >
        {expanded ? 'Hide data ▲' : `Show ${alert.days_observed} data points ▼`}
      </button>
      {expanded && (
        <div style={{ marginTop: '0.75rem', display: 'flex', flexWrap: 'wrap', gap: '0.4rem' }}>
          {alert.data_points.map((dp, i) => (
            <span key={i} style={{ background: '#0f1117', border: `1px solid ${col.border}`, borderRadius: 6, padding: '0.2rem 0.5rem', fontSize: '0.75rem', color: '#94a3b8' }}>
              {dp.date}: {Object.entries(dp).filter(([k]) => k !== 'date').map(([k, v]) => `${k}=${typeof v === 'number' ? v.toFixed(1) : v}`).join(', ')}
            </span>
          ))}
        </div>
      )}
    </div>
  )
}

export default function InsightsPage() {
  const [patterns, setPatterns] = useState([])
  const [days, setDays] = useState(30)
  const [loading, setLoading] = useState(true)

  useEffect(() => {
    setLoading(true)
    getPatterns(days).then(r => setPatterns(r.data)).catch(console.error).finally(() => setLoading(false))
  }, [days])

  const alerts = patterns.filter(p => p.severity === 'alert')
  const warnings = patterns.filter(p => p.severity === 'warning')
  const infos = patterns.filter(p => p.severity === 'info')

  return (
    <div style={s.page}>
      <h1 style={s.heading}>Health Insights</h1>
      <p style={s.sub}>Scientifically-grounded pattern analysis of your health data.</p>

      <div style={{ display: 'flex', gap: '0.5rem', marginBottom: '1.5rem' }}>
        {[14, 30, 60, 90].map(d => (
          <button key={d} onClick={() => setDays(d)} style={days === d ? s.daysBtnActive : s.daysBtn}>{d}d</button>
        ))}
      </div>

      {loading ? (
        <div style={{ color: '#64748b' }}>Analysing…</div>
      ) : patterns.length === 0 ? (
        <div style={s.empty}>
          <div style={{ fontSize: '2rem', marginBottom: '0.5rem' }}>✓</div>
          <div style={{ color: '#e2e8f0', fontWeight: 600, marginBottom: '0.25rem' }}>No patterns detected</div>
          <div>Keep logging data for more accurate analysis.</div>
        </div>
      ) : (
        <>
          {alerts.length > 0 && <>{alerts.map(a => <AlertCard key={a.pattern_id} alert={a} />)}</>}
          {warnings.length > 0 && <>{warnings.map(a => <AlertCard key={a.pattern_id} alert={a} />)}</>}
          {infos.length > 0 && <>{infos.map(a => <AlertCard key={a.pattern_id} alert={a} />)}</>}
        </>
      )}
    </div>
  )
}
