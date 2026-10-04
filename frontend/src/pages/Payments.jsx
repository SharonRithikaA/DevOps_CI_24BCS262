import { useMemo, useState } from 'react';
import { CheckCircle2, IndianRupee, Plus, Search, XCircle } from 'lucide-react';
import { api } from '../api.js';
import { useToast } from '../toast.jsx';
import { Badge, EmptyState, ErrorBox, Field, Modal, PageHeader, Spinner, SubmitButton, inputClass } from '../components/ui.jsx';
import { fmtDate, fmtMoney, useAsync, useDebounced } from '../utils.js';

const METHODS = ['CASH', 'UPI', 'CARD', 'ONLINE'];

function RecordPaymentForm({ members, onClose, onSaved }) {
  const toast = useToast();
  const [form, setForm] = useState({ memberId: '', planId: '', discountPercent: '0', method: 'CASH', status: 'PAID', transactionReference: '' });
  const [errors, setErrors] = useState({});
  const [busy, setBusy] = useState(false);
  const set = (k) => (e) => setForm({ ...form, [k]: e.target.value });

  const member = members.find((m) => String(m.id) === form.memberId);
  const plans = useAsync(() => api.get('/api/plans'), []);
  const selectedPlan = (plans.data || []).find((p) => String(p.id) === form.planId);
  const base = selectedPlan ? Number(selectedPlan.price) : 0;
  const discount = Math.min(Math.max(Number(form.discountPercent) || 0, 0), 100);
  const finalAmount = base > 0 ? Math.max(0, base - (base * discount) / 100) : 0;

  const submit = async (e) => {
    e.preventDefault();
    const errs = {};
    if (!form.memberId) errs.memberId = 'Select a member';
    if (!form.planId) errs.planId = 'Select a membership plan';
    const d = Number(form.discountPercent);
    if (form.discountPercent !== '' && (Number.isNaN(d) || d < 0)) errs.discountPercent = 'Discount cannot be negative';
    else if (d > 50) errs.discountPercent = 'Discount cannot exceed 50%';
    setErrors(errs);
    if (Object.keys(errs).length) return;
    setBusy(true);
    try {
      await api.post('/api/payments', {
        memberId: Number(form.memberId), planId: Number(form.planId), discountPercent: d || 0,
        method: form.method, status: form.status, transactionReference: form.transactionReference.trim() || null,
      });
      toast.success('Payment recorded');
      onSaved();
    } catch (err) { setErrors(err.fieldErrors || {}); toast.error(err.message); setBusy(false); }
  };

  return (
    <Modal title="Record a payment" onClose={onClose}
      footer={<><button className="btn-secondary" onClick={onClose}>Cancel</button><SubmitButton busy={busy} form="payment-form" type="submit">Record payment</SubmitButton></>}>
      <form id="payment-form" onSubmit={submit} noValidate className="grid gap-4 sm:grid-cols-2">
        <div className="sm:col-span-2"><Field label="Member" required error={errors.memberId}>
          <select className={inputClass(errors.memberId)} value={form.memberId} onChange={set('memberId')}>
            <option value="">Select a member</option>
            {members.map((m) => <option key={m.id} value={m.id}>{m.memberCode} - {m.fullName}</option>)}
          </select>
        </Field></div>
        <Field label="Membership plan" required error={errors.planId}>
          <select className={inputClass(errors.planId)} value={form.planId} onChange={set('planId')}>
            <option value="">Select a plan</option>
            {(plans.data || []).filter((p) => p.active).map((p) => <option key={p.id} value={p.id}>{p.name} - {fmtMoney(p.price)}</option>)}
          </select>
        </Field>
        <Field label="Discount (%)" error={errors.discountPercent} hint="Maximum 50%"><input type="number" min="0" max="50" step="0.01" className={inputClass(errors.discountPercent)} value={form.discountPercent} onChange={set('discountPercent')} /></Field>
        <Field label="Payment method" required error={errors.method}>
          <select className={inputClass(errors.method)} value={form.method} onChange={set('method')}>{METHODS.map((m) => <option key={m} value={m}>{m}</option>)}</select>
        </Field>
        <Field label="Status" required error={errors.status}>
          <select className={inputClass(errors.status)} value={form.status} onChange={set('status')}><option value="PAID">Paid</option><option value="PENDING">Pending</option></select>
        </Field>
        <div className="sm:col-span-2"><Field label="Transaction reference" error={errors.transactionReference} hint="Leave blank to auto-generate"><input className={inputClass(errors.transactionReference)} value={form.transactionReference} onChange={set('transactionReference')} /></Field></div>

        <div className="sm:col-span-2 rounded-lg bg-slate-50 p-3 text-sm">
          <div className="flex justify-between text-slate-500"><span>Base fee</span><span>{fmtMoney(base)}</span></div>
          <div className="flex justify-between text-slate-500"><span>Discount ({discount || 0}%)</span><span>-{fmtMoney((base * discount) / 100)}</span></div>
          <div className="mt-1 flex justify-between border-t border-slate-200 pt-1 font-semibold text-slate-900"><span>Final amount</span><span>{fmtMoney(finalAmount)}</span></div>
        </div>
      </form>
    </Modal>
  );
}

export default function Payments() {
  const toast = useToast();
  const [filters, setFilters] = useState({ q: '', status: '', method: '', from: '', to: '' });
  const [modal, setModal] = useState(null);
  const [busyId, setBusyId] = useState(null);
  const q = useDebounced(filters.q);

  const membersAsync = useAsync(() => api.get('/api/members'), []);
  const list = useAsync(() => api.get('/api/payments', { ...filters, q }), [q, filters.status, filters.method, filters.from, filters.to]);
  const set = (k) => (e) => setFilters({ ...filters, [k]: e.target.value });

  const totals = useMemo(() => {
    const rows = list.data || [];
    return { collected: rows.filter((p) => p.status === 'PAID').reduce((s, p) => s + Number(p.finalAmount), 0), pending: rows.filter((p) => p.status === 'PENDING').length };
  }, [list.data]);

  const settle = async (payment, status) => {
    setBusyId(payment.id);
    try { await api.put(`/api/payments/${payment.id}/status`, { status }); toast.success(`Payment marked ${status.toLowerCase()}`); list.reload(); }
    catch (err) { toast.error(err.message); }
    setBusyId(null);
  };

  return (
    <>
      <PageHeader title="Payments" subtitle={list.data ? `${list.data.length} transaction(s) - ${fmtMoney(totals.collected)} collected` : 'Record and track member payments'}>
        <button className="btn-primary" onClick={() => setModal({ type: 'form' })} disabled={membersAsync.loading}><Plus className="h-4 w-4" /> Record payment</button>
      </PageHeader>

      <div className="card mb-4 grid gap-3 p-4 sm:grid-cols-2 lg:grid-cols-5">
        <div className="relative lg:col-span-2">
          <Search className="absolute left-3 top-2.5 h-4 w-4 text-slate-400" />
          <input className="input pl-9" placeholder="Search member or reference" value={filters.q} onChange={set('q')} />
        </div>
        <select className="input" value={filters.status} onChange={set('status')}><option value="">All statuses</option><option value="PAID">Paid</option><option value="PENDING">Pending</option><option value="FAILED">Failed</option></select>
        <select className="input" value={filters.method} onChange={set('method')}><option value="">All methods</option>{METHODS.map((m) => <option key={m} value={m}>{m}</option>)}</select>
        <div className="flex gap-2">
          <input type="date" className="input" value={filters.from} onChange={set('from')} aria-label="From date" />
          <input type="date" className="input" value={filters.to} onChange={set('to')} aria-label="To date" />
        </div>
      </div>

      {list.loading && !list.data ? <Spinner text="Loading payments..." /> : list.error ? <ErrorBox error={list.error} onRetry={list.reload} /> : (
        <div className="card overflow-hidden">
          {list.data.length === 0 ? <EmptyState title="No payments found" hint="Record a payment to get started." /> : (
            <div className="overflow-x-auto">
              <table className="min-w-full divide-y divide-slate-200">
                <thead className="bg-slate-50"><tr>{['Date', 'Reference', 'Member', 'Plan', 'Method', 'Amount', 'Status', ''].map((h) => <th key={h} className="th">{h}</th>)}</tr></thead>
                <tbody className="divide-y divide-slate-100">
                  {list.data.map((p) => (
                    <tr key={p.id} className="hover:bg-slate-50">
                      <td className="td">{fmtDate(p.paymentDate)}</td>
                      <td className="td font-mono text-xs">{p.transactionReference}</td>
                      <td className="td"><p className="font-medium text-slate-900">{p.memberName}</p><p className="text-xs text-slate-500">{p.memberCode}</p></td>
                      <td className="td">{p.planName}</td>
                      <td className="td">{p.method}</td>
                      <td className="td"><p className="font-semibold">{fmtMoney(p.finalAmount)}</p>{Number(p.discountPercent) > 0 && <p className="text-xs text-emerald-600">{p.discountPercent}% off</p>}</td>
                      <td className="td"><Badge value={p.status} /></td>
                      <td className="td text-right">
                        {p.status === 'PENDING' && (
                          <div className="flex justify-end gap-1">
                            <button className="btn-ghost text-emerald-600" disabled={busyId === p.id} onClick={() => settle(p, 'PAID')} aria-label="Mark paid"><CheckCircle2 className="h-4 w-4" /></button>
                            <button className="btn-ghost text-red-600" disabled={busyId === p.id} onClick={() => settle(p, 'FAILED')} aria-label="Mark failed"><XCircle className="h-4 w-4" /></button>
                          </div>
                        )}
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </div>
      )}

      {modal?.type === 'form' && <RecordPaymentForm members={membersAsync.data || []} onClose={() => setModal(null)} onSaved={() => { setModal(null); list.reload(); }} />}
    </>
  );
}
