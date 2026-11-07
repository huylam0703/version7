import { useEffect, useState } from 'react'
import { getErrorMessage, reportApi } from '../../api'
import type { ReportSummary } from '../../types'
import { Card, ErrorBox, Input, PageHeader, StatCard } from '../../components/Ui'

export function ReportsPage() {
  const [from, setFrom] = useState('')
  const [to, setTo] = useState('')
  const [data, setData] = useState<ReportSummary>({})
  const [error, setError] = useState('')

  useEffect(() => {
    reportApi.summary({ from: from || undefined, to: to || undefined })
      .then(setData)
      .catch((e) => setError(getErrorMessage(e)))
  }, [from, to])

  return (
    <div>
      <PageHeader title="Reports" description="High-level counts for farms, readings, alerts and missions." />
      <ErrorBox message={error} />
      <div className="mb-4 flex max-w-md gap-2">
        <Input type="date" value={from} onChange={(e) => setFrom(e.target.value)} />
        <Input type="date" value={to} onChange={(e) => setTo(e.target.value)} />
      </div>
      <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-5">
        <StatCard label="Farms" value={data.farmCount ?? '—'} />
        <StatCard label="Zones" value={data.zoneCount ?? '—'} />
        <StatCard label="Readings" value={data.readingCount ?? '—'} />
        <StatCard label="Alerts" value={data.alertCount ?? '—'} />
        <StatCard label="Missions" value={data.missionCount ?? '—'} />
      </div>
      <Card className="mt-4">
        <p className="text-sm text-gray-500">
          This page only shows summary numbers from GET /api/reports/summary. Image/AI crop analysis is out of scope.
        </p>
      </Card>
    </div>
  )
}
