import React, { useEffect, useState } from 'react'
import { getEntries } from '../api/client'
import { format, parseISO } from 'date-fns'

const TYPE_COLORS = {
  stress: '#f59e0b', pain: '#f43f5e', mood: '#10b981',
  nutrition: '#6366f1', sleep: '#22d3ee', medication: '#a78bfa',
  exercise: '#34d399', note: '#94a3b8',
}

const s = {
  page: { padding: '2rem', maxWidth: 900, margin: '0 auto' },
  heading: { fontSize: '1.4rem', fontWeight: 700, color: '#e2e8f0', marginBottom: '0.5rem' },
  sub: { color: '#64748b', fontSize: '0.85rem', marginBottom: '2rem' },
  filters: { display: 'flex', gap: '0.5rem', flexWrap: 'wrap', marginBottom: '1.5rem' },
  filterBtn: { background: 'none', border: '1px solid #2d3348', borderRadius: 20, padding: '0.3rem 0.8rem', color: '#94a3b8', cursor: 'pointer', fontSize: '0.8rem' },
  filterBtnActive: { border: '1px solid #6366f1', borderRadius: 20, padding: '0.3rem 0.8rem', color: '#6366f1', cursor: 'pointer', fontSize: '0.8rem', background: 'rgba(99,102,241,0.1)' },
  entry: { background: '#1a1d27', border: '1px solid #2d3348', borderRadius: 10, padding: '1rem 1.25rem', marginBottom: '0.75rem', display: 'flex', gap: '1rem', alignItems: 'flex-start' },
  badge: (type) => ({
    background: TYPE_COLORS[type] ? `${TYPE_COLORS[type]}22` : '#1e2334',
    color: TYPE_COLORS[type] || '#94a3b8',
    borderRadius: 6, padding: '0.2rem 0.65rem', fontSize: '0.75rem', fontWeight: 600,
    textTransform: 'capitalize', whiteSpace: 'nowrap', flexShrink: 0,
  }),
  time: { color: '#64748b', fontSize: '0.75rem', whiteSpace: 'nowrap', flexShrink: 0 },
  raw: { color: '#94a3b8', fontSize: '0.85rem', flex: 1, lineHeight: 1.5 },
  score: { color: '#e2e8f0', fontWeight: 700, fontSize: '0.9rem', flexShrink: 0 },
  dataChip: { background: '#0f1117', border: '1px solid #2d3348', borderRadius: 6, padding: '0.15rem 0.5rem', fontSize: '0.72rem', color: '#64748b', display: 'inline-block', marginRight: '0.3rem', marginTop: '0.3rem' },
  empty: { color: '#64748b', textAlign: 'center', padding: '3rem', background: '#1a1d27', border: '1px solid #2d3348', borderRadius: 12 },
}

const TYPES = ['all', 'stress', 'pain', 'mood', 'nutrition', 'sleep', 'medication', 'exercise', 'note']

export default function LogPage() {
  const [entries, setEntries] = useState([])
  const [filter, setFilter] = useState('all')
  const [days, setDays] = useState(30)
  const [loading, setLoading] = useState(true)

  useEffect(() => {
    setLoading(true)
    getEntries({ days, limit: 200, ...(filter !== 'all' ? { entry_type: filter } : {}) })
      .then(r => setEntries(r.data))
      .catch(console.error)
      .finally(() => setLoading(false))
  }, [filter, days])

  return (
    <div style={s.page}>
      <h1 style={s.heading}>Health Log</h1>
      <p style={s.sub}>All entries from speech input and manual logging.</p>

      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', flexWrap: 'wrap', gap: '0.75rem', marginBottom: '1rem' }}>
        <div style={s.filters}>
          {TYPES.map(t => (
            <button key={t} style={filter === t ? s.filterBtnActive : s.filterBtn} onClick={() => setFilter(t)}>
              {t === 'all' ? 'All' : t.charAt(0).toUpperCase() + t.slice(1)}
            </button>
          ))}
        </div>
        <select style={{ background: '#1a1d27', border: '1px solid #2d3348', borderRadius: 8, padding: '0.4rem 0.8rem', color: '#94a3b8', fontSize: '0.8rem' }} value={days} onChange={e => setDays(+e.target.value)}>
          {[7, 14, 30, 60, 90].map(d => <option key={d} value={d}>Last {d} days</option>)}
        </select>
      </div>

      {loading ? (
        <div style={{ color: '#64748b' }}>Loading…</div>
      ) : entries.length === 0 ? (
        <div style={s.empty}>No entries found. Start logging via the widget or app.</div>
      ) : (
        entries.map(e => (
          <div key={e.id} style={s.entry}>
            <span style={s.badge(e.entry_type)}>{e.entry_type}</span>
            <div style={{ flex: 1 }}>
              {e.raw_input && <div style={s.raw}>{e.raw_input}</div>}
              <div>
                {Object.entries(e.data || {}).map(([k, v]) => (
                  <span key={k} style={s.dataChip}>{k}: {typeof v === 'object' ? JSON.stringify(v) : String(v)}</span>
                ))}
              </div>
            </div>
            {e.numeric_value != null && <span style={s.score}>{e.numeric_value}/10</span>}
            <span style={s.time}>{format(parseISO(e.timestamp), 'MMM d, HH:mm')}</span>
          </div>
        ))
      )}
    </div>
  )
}
