import { useState } from 'react';
import { Download, FileBarChart } from 'lucide-react';
import { api } from '../api.js';
import { useToast } from '../toast.jsx';
import { EmptyState, ErrorBox, PageHeader, Spinner } from '../components/ui.jsx';
import { useAsync } from '../utils.js';

const TYPES = [
  { key: 'membership', label: 'Membership report' }, { key: 'active', label: 'Active membership report' },
  { key: 'expired', label: 'Expired membership report' }, { key: 'revenue', label: 'Revenue report' },
  { key: 'attendance', label: 'Attendance report' },
];

export default function Reports() {
  const toast = useToast();
  const [type, setType] = useState('membership');
  const [filters, setFilters] = useState({ from: '', to: '' });
  const [downloading, setDownloading] = useState(false);
  const plans = useAsync(() => api.get('/api/plans'), []);
  const params = { ...filters, planId: filters.planId || undefined, paymentStatus: filters.paymentStatus || undefined };
  const { data, loading, error, reload } = useAsync(() => api.get(`/api/reports/${type}`, params), [type, filters.from, filters.to, filters.planId, filters.paymentStatus]);
  const set = (k) => (e) => setFilters({ ...filters, [k]: e.target.value });

  const downloadCsv = async () => {
    setDownloading(true);
    try { await api.download(`/api/reports/${type}/csv`, params, `${type}-report.csv`); }
    catch (err) { toast.error(err.message); }
    setDownloading(false);
  };

  return (
    <>
      <PageHeader title="Reports" subtitle="Membership, revenue and attendance reports with export">
        <button className="btn-secondary" onClick={downloadCsv} disabled={downloading || loading || !data?.rows?.length}><Download className="h-4 w-4" /> Export CSV</button>
      </PageHeader>

      <div className="card mb-4 flex flex-wrap gap-2 p-2">
        {TYPES.map((t) => (
          <button key={t.key} onClick={() => setType(t.key)} className={`rounded-lg px-3 py-2 text-sm font-medium transition ${type === t.key ? 'bg-brand-600 text-white' : 'text-slate-600 hover:bg-slate-100'}`}>{t.label}</button>
        ))}
      </div>

      <div className="card mb-4 grid gap-3 p-4 sm:grid-cols-2 lg:grid-cols-4">
        <input type="date" className="input" value={filters.from} onChange={set('from')} aria-label="From date" />
        <input type="date" className="input" value={filters.to} onChange={set('to')} aria-label="To date" />
        <select className="input" value={filters.planId || ''} onChange={set('planId')}><option value="">All plans</option>{(plans.data || []).map((p) => <option key={p.id} value={p.id}>{p.name}</option>)}</select>
        {(type === 'membership' || type === 'active' || type === 'expired' || type === 'revenue') && (
          <select className="input" value={filters.paymentStatus || ''} onChange={set('paymentStatus')}><option value="">Any payment status</option><option value="PAID">Paid</option><option value="PENDING">Pending</option><option value="FAILED">Failed</option></select>
        )}
      </div>

      {loading ? <Spinner text="Building report..." /> : error ? <ErrorBox error={error} onRetry={reload} /> : (
        <div className="card print-area overflow-hidden">
          <div className="flex flex-wrap items-center justify-between gap-3 border-b border-slate-200 px-5 py-4">
            <h2 className="flex items-center gap-2 text-lg font-semibold text-slate-900"><FileBarChart className="h-5 w-5 text-brand-600" /> {data.title}</h2>
            <div className="flex flex-wrap gap-4 text-sm text-slate-500">{Object.entries(data.summary).map(([k, v]) => <span key={k}><strong className="text-slate-800">{v}</strong> {k}</span>)}</div>
          </div>
          {data.rows.length === 0 ? <EmptyState title="No data for the selected filters" /> : (
            <div className="overflow-x-auto">
              <table className="min-w-full divide-y divide-slate-200">
                <thead className="bg-slate-50"><tr>{data.columns.map((c) => <th key={c} className="th">{c}</th>)}</tr></thead>
                <tbody className="divide-y divide-slate-100">
                  {data.rows.map((row, i) => <tr key={i} className="hover:bg-slate-50">{row.map((cell, j) => <td key={j} className="td">{cell}</td>)}</tr>)}
                </tbody>
              </table>
            </div>
          )}
        </div>
      )}
    </>
  );
}
