import React, { useEffect, useState } from 'react'
import { getSummary, getWearable } from '../api/client'
import {
  LineChart, Line, XAxis, YAxis, Tooltip, ResponsiveContainer, CartesianGrid, Legend,
} from 'recharts'
import { format, parseISO } from 'date-fns'

const s = {
  page: { padding: '2rem', maxWidth: 1200, margin: '0 auto' },
  heading: { fontSize: '1.4rem', fontWeight: 700, color: '#e2e8f0', marginBottom: '1.5rem' },
  grid: { display: 'grid', gridTemplateColumns: 'repeat(auto-fill, minmax(180px, 1fr))', gap: '1rem', marginBottom: '2rem' },
  card: { background: '#1a1d27', border: '1px solid #2d3348', borderRadius: 12, padding: '1.25rem' },
  metricLabel: { color: '#64748b', fontSize: '0.75rem', fontWeight: 500, textTransform: 'uppercase', letterSpacing: '0.05em', marginBottom: '0.4rem' },
  metricVal: { fontSize: '1.8rem', fontWeight: 700, color: '#e2e8f0' },
  metricUnit: { fontSize: '0.8rem', color: '#64748b', marginLeft: '0.25rem' },
  chartCard: { background: '#1a1d27', border: '1px solid #2d3348', borderRadius: 12, padding: '1.5rem', marginBottom: '1.5rem' },
  chartTitle: { color: '#94a3b8', fontSize: '0.9rem', fontWeight: 600, marginBottom: '1rem' },
  alertBadge: { display: 'inline-block', background: '#7f1d1d', color: '#fca5a5', borderRadius: 6, padding: '0.2rem 0.6rem', fontSize: '0.75rem', fontWeight: 600 },
}

const CHART_COLORS = ['#6366f1', '#22d3ee', '#f59e0b', '#10b981', '#f43f5e']

function MetricCard({ label, value, unit }) {
  return (
    <div style={s.card}>
      <div style={s.metricLabel}>{label}</div>
      <div style={s.metricVal}>
        {value ?? <span style={{ color: '#334155' }}>—</span>}
        {value != null && <span style={s.metricUnit}>{unit}</span>}
      </div>
    </div>
  )
}

export default function DashboardPage() {
  const [summary, setSummary] = useState(null)
  const [wearableHistory, setWearableHistory] = useState([])
  const [loading, setLoading] = useState(true)

  useEffect(() => {
    Promise.all([getSummary(), getWearable({ days: 14 })]).then(([sumRes, wearRes]) => {
      setSummary(sumRes.data)
      const sorted = [...wearRes.data].sort((a, b) => a.timestamp.localeCompare(b.timestamp))
      setWearableHistory(sorted.map(s => ({
        ...s,
        date: format(parseISO(s.timestamp), 'MMM d'),
        sleep_h: s.sleep_duration_min ? +(s.sleep_duration_min / 60).toFixed(1) : null,
      })))
    }).catch(console.error).finally(() => setLoading(false))
  }, [])

  if (loading) return <div style={{ padding: '3rem', color: '#64748b' }}>Loading…</div>

  const w = summary?.latest_wearable

  return (
    <div style={s.page}>
      <div style={{ display: 'flex', alignItems: 'center', gap: '1rem', marginBottom: '1.5rem', flexWrap: 'wrap' }}>
        <h1 style={s.heading}>Today's Overview</h1>
        {summary?.pattern_alert_count > 0 && (
          <span style={s.alertBadge}>{summary.pattern_alert_count} alert{summary.pattern_alert_count > 1 ? 's' : ''}</span>
        )}
        {w?.timestamp && (
          <span style={{ color: '#475569', fontSize: '0.75rem', marginLeft: 'auto' }}>
            Last synced: {format(parseISO(w.timestamp), 'MMM d, HH:mm')}
          </span>
        )}
      </div>

      <div style={s.grid}>
        <MetricCard label="Steps" value={w?.steps?.toLocaleString()} unit="" />
        <MetricCard label="Avg Heart Rate" value={w?.heart_rate_avg?.toFixed(0)} unit="bpm" />
        <MetricCard label="Resting HR" value={w?.heart_rate_resting?.toFixed(0)} unit="bpm" />
        <MetricCard label="HRV" value={w?.hrv_ms?.toFixed(0)} unit="ms" />
        <MetricCard label="SpO₂" value={w?.spo2_pct?.toFixed(1)} unit="%" />
        <MetricCard label="Sleep" value={w?.sleep_duration_min ? (w.sleep_duration_min / 60).toFixed(1) : null} unit="hrs" />
        <MetricCard label="Sleep Score" value={w?.sleep_score} unit="/100" />
        <MetricCard label="Active Cal" value={w?.calories_active?.toFixed(0)} unit="kcal" />
        <MetricCard label="Stress Score" value={w?.stress_score} unit="/100" />
      </div>

      {wearableHistory.length > 0 && (
        <>
          <div style={s.chartCard}>
            <div style={s.chartTitle}>Steps — Last 14 Days</div>
            <ResponsiveContainer width="100%" height={200}>
              <LineChart data={wearableHistory}>
                <CartesianGrid strokeDasharray="3 3" stroke="#1e2334" />
                <XAxis dataKey="date" tick={{ fill: '#64748b', fontSize: 11 }} />
                <YAxis tick={{ fill: '#64748b', fontSize: 11 }} />
                <Tooltip contentStyle={{ background: '#1a1d27', border: '1px solid #2d3348', borderRadius: 8 }} />
                <Line type="monotone" dataKey="steps" stroke="#6366f1" dot={false} strokeWidth={2} />
              </LineChart>
            </ResponsiveContainer>
          </div>

          <div style={s.chartCard}>
            <div style={s.chartTitle}>Sleep & HRV — Last 14 Days</div>
            <ResponsiveContainer width="100%" height={200}>
              <LineChart data={wearableHistory}>
                <CartesianGrid strokeDasharray="3 3" stroke="#1e2334" />
                <XAxis dataKey="date" tick={{ fill: '#64748b', fontSize: 11 }} />
                <YAxis yAxisId="left" tick={{ fill: '#64748b', fontSize: 11 }} />
                <YAxis yAxisId="right" orientation="right" tick={{ fill: '#64748b', fontSize: 11 }} />
                <Tooltip contentStyle={{ background: '#1a1d27', border: '1px solid #2d3348', borderRadius: 8 }} />
                <Legend wrapperStyle={{ color: '#94a3b8', fontSize: '0.8rem' }} />
                <Line yAxisId="left" type="monotone" dataKey="sleep_h" name="Sleep (hrs)" stroke="#22d3ee" dot={false} strokeWidth={2} />
                <Line yAxisId="right" type="monotone" dataKey="hrv_ms" name="HRV (ms)" stroke="#10b981" dot={false} strokeWidth={2} />
              </LineChart>
            </ResponsiveContainer>
          </div>
        </>
      )}

      {summary?.today_entries?.length > 0 && (
        <div style={s.chartCard}>
          <div style={s.chartTitle}>Today's Log Entries</div>
          {summary.today_entries.map((e) => (
            <div key={e.id} style={{ padding: '0.75rem 0', borderBottom: '1px solid #1e2334', display: 'flex', gap: '1rem', alignItems: 'flex-start' }}>
              <span style={{ background: '#1e2334', borderRadius: 6, padding: '0.2rem 0.6rem', fontSize: '0.75rem', color: '#6366f1', fontWeight: 600, textTransform: 'capitalize', minWidth: 80, textAlign: 'center' }}>{e.entry_type}</span>
              <span style={{ color: '#94a3b8', fontSize: '0.85rem', flex: 1 }}>{e.raw_input || JSON.stringify(e.data)}</span>
              {e.numeric_value != null && <span style={{ color: '#e2e8f0', fontWeight: 600, fontSize: '0.85rem' }}>{e.numeric_value}/10</span>}
            </div>
          ))}
        </div>
      )}
    </div>
  )
}
