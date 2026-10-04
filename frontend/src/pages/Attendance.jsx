import { useState } from 'react';
import { CalendarCheck, Search, UserPlus } from 'lucide-react';
import { api } from '../api.js';
import { useToast } from '../toast.jsx';
import { EmptyState, ErrorBox, Field, Modal, PageHeader, Spinner, StatCard, SubmitButton, inputClass } from '../components/ui.jsx';
import { fmtDate, fmtTime, todayIso, useAsync, useDebounced } from '../utils.js';

function MarkAttendanceForm({ members, onClose, onSaved }) {
  const toast = useToast();
  const [memberId, setMemberId] = useState('');
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(false);

  const submit = async (e) => {
    e.preventDefault();
    if (!memberId) { setError('Select a member'); return; }
    setBusy(true);
    try { await api.post('/api/attendance', { memberId: Number(memberId) }); toast.success('Attendance marked'); onSaved(); }
    catch (err) { setError(err.message); toast.error(err.message); setBusy(false); }
  };

  return (
    <Modal title="Mark attendance" onClose={onClose} size="max-w-md"
      footer={<><button className="btn-secondary" onClick={onClose}>Cancel</button><SubmitButton busy={busy} form="attendance-form" type="submit">Mark present</SubmitButton></>}>
      <form id="attendance-form" onSubmit={submit} noValidate>
        <Field label="Member" required error={error}>
          <select className={inputClass(error)} value={memberId} onChange={(e) => { setMemberId(e.target.value); setError(''); }}>
            <option value="">Select a member</option>
            {members.map((m) => <option key={m.id} value={m.id}>{m.memberCode} - {m.fullName} ({m.membershipStatus.replace('_', ' ').toLowerCase()})</option>)}
          </select>
        </Field>
      </form>
    </Modal>
  );
}

export default function Attendance() {
  const toast = useToast();
  const [date, setDate] = useState(todayIso());
  const [q, setQ] = useState('');
  const [modal, setModal] = useState(false);
  const debouncedQ = useDebounced(q);

  const membersAsync = useAsync(() => api.get('/api/members', { status: 'ACTIVE' }), []);
  const list = useAsync(() => api.get('/api/attendance', { date: date || undefined, q: debouncedQ }), [date, debouncedQ]);
  const todayCount = useAsync(() => api.get('/api/attendance/today'), []);

  const isToday = date === todayIso();

  return (
    <>
      <PageHeader title="Attendance" subtitle="Mark check-ins and review history">
        <button className="btn-primary" onClick={() => setModal(true)} disabled={membersAsync.loading}><UserPlus className="h-4 w-4" /> Mark attendance</button>
      </PageHeader>

      <div className="mb-4"><StatCard icon={CalendarCheck} title="Today's attendance" value={todayCount.data?.length ?? '-'} tone="violet" /></div>

      <div className="card mb-4 flex flex-col gap-3 p-4 sm:flex-row sm:items-center">
        <div className="relative flex-1">
          <Search className="absolute left-3 top-2.5 h-4 w-4 text-slate-400" />
          <input className="input pl-9" placeholder="Search by member name or ID" value={q} onChange={(e) => setQ(e.target.value)} />
        </div>
        <input type="date" className="input sm:w-48" value={date} onChange={(e) => setDate(e.target.value)} max={todayIso()} />
        {!isToday && <button className="btn-secondary" onClick={() => setDate(todayIso())}>Today</button>}
        {date && <button className="btn-secondary" onClick={() => setDate('')}>All dates</button>}
      </div>

      {list.loading && !list.data ? <Spinner text="Loading attendance..." /> : list.error ? <ErrorBox error={list.error} onRetry={list.reload} /> : (
        <div className="card overflow-hidden">
          {list.data.length === 0 ? <EmptyState title="No attendance records" hint="Mark a check-in to get started." /> : (
            <div className="overflow-x-auto">
              <table className="min-w-full divide-y divide-slate-200">
                <thead className="bg-slate-50"><tr>{['Date', 'Check-in time', 'Member ID', 'Name', 'Plan'].map((h) => <th key={h} className="th">{h}</th>)}</tr></thead>
                <tbody className="divide-y divide-slate-100">
                  {list.data.map((a) => (
                    <tr key={a.id} className="hover:bg-slate-50">
                      <td className="td">{fmtDate(a.attendanceDate)}</td>
                      <td className="td">{fmtTime(a.checkInTime)}</td>
                      <td className="td font-mono text-xs">{a.memberCode}</td>
                      <td className="td font-medium text-slate-900">{a.memberName}</td>
                      <td className="td">{a.planName}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </div>
      )}

      {modal && <MarkAttendanceForm members={membersAsync.data || []} onClose={() => setModal(false)} onSaved={() => { setModal(false); list.reload(); todayCount.reload(); }} />}
    </>
  );
}
