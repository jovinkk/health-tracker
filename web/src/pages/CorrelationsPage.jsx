import React, { useState } from 'react'
import { getCorrelation } from '../api/client'
import {
  ScatterChart, Scatter, XAxis, YAxis, CartesianGrid, Tooltip, ResponsiveContainer, Label,
} from 'recharts'

const METRICS = [
  { value: 'steps', label: 'Steps' },
  { value: 'heart_rate_avg', label: 'Avg Heart Rate' },
  { value: 'heart_rate_resting', label: 'Resting HR' },
  { value: 'hrv_ms', label: 'HRV (ms)' },
  { value: 'spo2_pct', label: 'SpO₂ (%)' },
  { value: 'sleep_duration_min', label: 'Sleep Duration' },
  { value: 'sleep_score', label: 'Sleep Score' },
  { value: 'calories_active', label: 'Active Calories' },
  { value: 'stress_score_wearable', label: 'Wearable Stress' },
  { value: 'stress', label: 'Self-reported Stress' },
  { value: 'pain', label: 'Pain Score' },
  { value: 'mood', label: 'Mood Score' },
]

const s = {
  page: { padding: '2rem', maxWidth: 900, margin: '0 auto' },
  heading: { fontSize: '1.4rem', fontWeight: 700, color: '#e2e8f0', marginBottom: '0.5rem' },
  sub: { color: '#64748b', fontSize: '0.85rem', marginBottom: '2rem' },
  row: { display: 'flex', gap: '1rem', alignItems: 'flex-end', marginBottom: '1.5rem', flexWrap: 'wrap' },
  selectWrap: { display: 'flex', flexDirection: 'column', gap: '0.35rem', flex: 1, minWidth: 160 },
  label: { color: '#64748b', fontSize: '0.75rem', fontWeight: 500, textTransform: 'uppercase' },
  select: { background: '#1a1d27', border: '1px solid #2d3348', borderRadius: 8, padding: '0.65rem 0.85rem', color: '#e2e8f0', fontSize: '0.9rem', width: '100%' },
  btn: { background: '#6366f1', color: '#fff', border: 'none', borderRadius: 8, padding: '0.65rem 1.5rem', fontSize: '0.9rem', fontWeight: 600, cursor: 'pointer' },
  card: { background: '#1a1d27', border: '1px solid #2d3348', borderRadius: 12, padding: '1.5rem' },
  rBig: { fontSize: '3rem', fontWeight: 800, textAlign: 'center', margin: '1rem 0 0.25rem' },
  interp: { textAlign: 'center', color: '#94a3b8', fontSize: '0.9rem', marginBottom: '1.5rem' },
}

function rColor(r) {
  const a = Math.abs(r)
  if (a >= 0.6) return r > 0 ? '#10b981' : '#f43f5e'
  if (a >= 0.3) return '#f59e0b'
  return '#64748b'
}

export default function CorrelationsPage() {
  const [m1, setM1] = useState('sleep_duration_min')
  const [m2, setM2] = useState('stress')
  const [days, setDays] = useState(30)
  const [result, setResult] = useState(null)
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState('')

  const run = async () => {
    setLoading(true); setError('')
    try {
      const { data } = await getCorrelation(m1, m2, days)
      setResult(data)
    } catch (e) {
      setError('Failed to fetch correlation data.')
    } finally {
      setLoading(false)
    }
  }

  const scatterData = result?.pairs?.map(p => ({ x: p.x, y: p.y, date: p.date })) || []

  return (
    <div style={s.page}>
      <h1 style={s.heading}>Correlation Analysis</h1>
      <p style={s.sub}>Discover which metrics move together using Pearson correlation.</p>

      <div style={s.row}>
        <div style={s.selectWrap}>
          <span style={s.label}>Metric 1 (X)</span>
          <select style={s.select} value={m1} onChange={e => setM1(e.target.value)}>
            {METRICS.map(m => <option key={m.value} value={m.value}>{m.label}</option>)}
          </select>
        </div>
        <div style={s.selectWrap}>
          <span style={s.label}>Metric 2 (Y)</span>
          <select style={s.select} value={m2} onChange={e => setM2(e.target.value)}>
            {METRICS.map(m => <option key={m.value} value={m.value}>{m.label}</option>)}
          </select>
        </div>
        <div style={s.selectWrap}>
          <span style={s.label}>Window</span>
          <select style={s.select} value={days} onChange={e => setDays(+e.target.value)}>
            {[14, 30, 60, 90].map(d => <option key={d} value={d}>{d} days</option>)}
          </select>
        </div>
        <button style={s.btn} onClick={run} disabled={loading}>{loading ? '…' : 'Analyse'}</button>
      </div>

      {error && <div style={{ color: '#fca5a5', marginBottom: '1rem' }}>{error}</div>}

      {result && (
        <div style={s.card}>
          <div style={{ ...s.rBig, color: rColor(result.pearson_r) }}>r = {result.pearson_r.toFixed(3)}</div>
          <div style={s.interp}>{result.interpretation}</div>
          <div style={{ display: 'flex', justifyContent: 'center', gap: '2rem', marginBottom: '1.5rem' }}>
            <span style={{ color: '#64748b', fontSize: '0.8rem' }}>p = {result.p_value.toFixed(4)}</span>
            <span style={{ color: '#64748b', fontSize: '0.8rem' }}>n = {result.n_samples} days</span>
          </div>

          {scatterData.length > 0 && (
            <ResponsiveContainer width="100%" height={280}>
              <ScatterChart margin={{ top: 10, right: 20, bottom: 30, left: 20 }}>
                <CartesianGrid strokeDasharray="3 3" stroke="#1e2334" />
                <XAxis dataKey="x" type="number" name={m1} tick={{ fill: '#64748b', fontSize: 11 }}>
                  <Label value={METRICS.find(m => m.value === m1)?.label || m1} offset={-10} position="insideBottom" fill="#64748b" fontSize={12} />
                </XAxis>
                <YAxis dataKey="y" type="number" name={m2} tick={{ fill: '#64748b', fontSize: 11 }} />
                <Tooltip
                  cursor={{ strokeDasharray: '3 3' }}
                  contentStyle={{ background: '#1a1d27', border: '1px solid #2d3348', borderRadius: 8 }}
                  formatter={(v, name) => [v.toFixed(2), name]}
                  labelFormatter={(_, payload) => payload?.[0]?.payload?.date || ''}
                />
                <Scatter data={scatterData} fill={rColor(result.pearson_r)} opacity={0.8} />
              </ScatterChart>
            </ResponsiveContainer>
          )}
        </div>
      )}
    </div>
  )
}
