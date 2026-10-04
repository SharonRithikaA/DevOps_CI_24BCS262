import { useEffect, useMemo, useState } from 'react';
import { Eye, Pencil, Plus, Search, Trash2 } from 'lucide-react';
import { api } from '../api.js';
import { useAuth } from '../auth.jsx';
import { useToast } from '../toast.jsx';
import { Badge, ConfirmDialog, EmptyState, ErrorBox, Field, Modal, PageHeader, Pagination, Spinner, SubmitButton, inputClass } from '../components/ui.jsx';
import { EMAIL_RE, PHONE_RE, fmtDate, fmtMoney, fmtTime, todayIso, useAsync, useDebounced } from '../utils.js';

const PAGE_SIZE = 10;
const EMPTY = { fullName: '', email: '', phone: '', dateOfBirth: '', gender: 'MALE', address: '', emergencyContact: '', planId: '', membershipStartDate: todayIso(), membershipEndDate: '', trainerId: '' };

function validate(f) {
  const e = {};
  if (!f.fullName.trim()) e.fullName = 'Full name is required';
  if (!f.email.trim()) e.email = 'Email is required'; else if (!EMAIL_RE.test(f.email.trim())) e.email = 'Enter a valid email address';
  if (!f.phone.trim()) e.phone = 'Phone is required'; else if (!PHONE_RE.test(f.phone.trim())) e.phone = 'Phone must contain 10 to 13 digits';
  if (!f.dateOfBirth) e.dateOfBirth = 'Date of birth is required'; else if (f.dateOfBirth >= todayIso()) e.dateOfBirth = 'Date of birth must be in the past';
  if (!f.emergencyContact.trim()) e.emergencyContact = 'Emergency contact is required';
  if (!f.planId) e.planId = 'Select a membership plan';
  if (f.membershipStartDate && f.membershipEndDate && f.membershipEndDate < f.membershipStartDate) e.membershipEndDate = 'End date cannot be before the start date';
  return e;
}

function MemberForm({ member, plans, trainers, onClose, onSaved }) {
  const toast = useToast();
  const [form, setForm] = useState(member ? {
    fullName: member.fullName, email: member.email, phone: member.phone, dateOfBirth: member.dateOfBirth, gender: member.gender,
    address: member.address || '', emergencyContact: member.emergencyContact, planId: String(member.planId),
    membershipStartDate: member.membershipStartDate, membershipEndDate: member.membershipEndDate, trainerId: member.trainerId ? String(member.trainerId) : '',
  } : EMPTY);
  const [errors, setErrors] = useState({});
  const [busy, setBusy] = useState(false);
  const set = (k) => (e) => setForm({ ...form, [k]: e.target.value });

  const submit = async (e) => {
    e.preventDefault();
    const errs = validate(form);
    setErrors(errs);
    if (Object.keys(errs).length) return;
    const body = {
      ...form, fullName: form.fullName.trim(), email: form.email.trim(), phone: form.phone.trim(),
      planId: Number(form.planId), trainerId: form.trainerId ? Number(form.trainerId) : null,
      membershipStartDate: form.membershipStartDate || null, membershipEndDate: form.membershipEndDate || null,
    };
    setBusy(true);
    try {
      if (member) await api.put(`/api/members/${member.id}`, body); else await api.post('/api/members', body);
      toast.success(member ? 'Member updated' : 'Member registered');
      onSaved();
    } catch (err) {
      setErrors(err.fieldErrors || {});
      toast.error(err.message);
      setBusy(false);
    }
  };

  const activePlans = plans.filter((p) => p.active || String(p.id) === form.planId);
  return (
    <Modal title={member ? `Edit ${member.fullName}` : 'Register new member'} onClose={onClose}
      footer={<><button className="btn-secondary" onClick={onClose} type="button">Cancel</button><SubmitButton busy={busy} form="member-form" type="submit">{member ? 'Save changes' : 'Register member'}</SubmitButton></>}>
      <form id="member-form" onSubmit={submit} noValidate className="grid gap-4 sm:grid-cols-2">
        <Field label="Full name" required error={errors.fullName}><input className={inputClass(errors.fullName)} value={form.fullName} onChange={set('fullName')} /></Field>
        <Field label="Email" required error={errors.email}><input type="email" className={inputClass(errors.email)} value={form.email} onChange={set('email')} /></Field>
        <Field label="Phone" required error={errors.phone}><input className={inputClass(errors.phone)} value={form.phone} onChange={set('phone')} inputMode="tel" placeholder="9840012345" /></Field>
        <Field label="Date of birth" required error={errors.dateOfBirth}><input type="date" max={todayIso()} className={inputClass(errors.dateOfBirth)} value={form.dateOfBirth} onChange={set('dateOfBirth')} /></Field>
        <Field label="Gender" required error={errors.gender}>
          <select className={inputClass(errors.gender)} value={form.gender} onChange={set('gender')}><option value="MALE">Male</option><option value="FEMALE">Female</option><option value="OTHER">Other</option></select>
        </Field>
        <Field label="Emergency contact" required error={errors.emergencyContact} hint="Name and phone"><input className={inputClass(errors.emergencyContact)} value={form.emergencyContact} onChange={set('emergencyContact')} /></Field>
        <div className="sm:col-span-2"><Field label="Address" error={errors.address}><input className={inputClass(errors.address)} value={form.address} onChange={set('address')} /></Field></div>
        <Field label="Membership plan" required error={errors.planId}>
          <select className={inputClass(errors.planId)} value={form.planId} onChange={set('planId')}>
            <option value="">Select a plan</option>
            {activePlans.map((p) => <option key={p.id} value={p.id}>{p.name} - {p.durationMonths} mo - {fmtMoney(p.price)}</option>)}
          </select>
        </Field>
        <Field label="Trainer" error={errors.trainerId}>
          <select className={inputClass(errors.trainerId)} value={form.trainerId} onChange={set('trainerId')}>
            <option value="">No trainer</option>
            {trainers.filter((t) => t.active || String(t.id) === form.trainerId).map((t) => <option key={t.id} value={t.id}>{t.name} ({t.specialization})</option>)}
          </select>
        </Field>
        <Field label="Membership start" error={errors.membershipStartDate}><input type="date" className={inputClass(errors.membershipStartDate)} value={form.membershipStartDate} onChange={set('membershipStartDate')} /></Field>
        <Field label="Membership end" error={errors.membershipEndDate} hint="Leave blank to calculate from the plan"><input type="date" className={inputClass(errors.membershipEndDate)} value={form.membershipEndDate} onChange={set('membershipEndDate')} /></Field>
      </form>
    </Modal>
  );
}

function MemberDetails({ member, onClose }) {
  const { data, loading } = useAsync(() => Promise.all([api.get(`/api/members/${member.id}/payments`), api.get(`/api/members/${member.id}/attendance`)]), [member.id]);
  const rows = [
    ['Member ID', member.memberCode], ['Email', member.email], ['Phone', member.phone], ['Date of birth', fmtDate(member.dateOfBirth)],
    ['Gender', member.gender], ['Address', member.address || '-'], ['Emergency contact', member.emergencyContact], ['Joined', fmtDate(member.joinDate)],
    ['Plan', member.planName], ['Membership', `${fmtDate(member.membershipStartDate)} to ${fmtDate(member.membershipEndDate)}`],
    ['Trainer', member.trainerName || '-'],
  ];
  return (
    <Modal title={member.fullName} onClose={onClose} footer={<button className="btn-secondary" onClick={onClose}>Close</button>}>
      <div className="mb-4 flex flex-wrap items-center gap-2">
        <Badge value={member.membershipStatus} /><Badge value={member.paymentStatus} text={`Payment: ${member.paymentStatus.toLowerCase()}`} />
        <span className="text-sm text-slate-500">{member.remainingDays} day(s) remaining</span>
      </div>
      <dl className="grid gap-x-6 gap-y-3 sm:grid-cols-2">
        {rows.map(([k, v]) => <div key={k}><dt className="text-xs uppercase tracking-wide text-slate-400">{k}</dt><dd className="text-sm text-slate-800">{v}</dd></div>)}
      </dl>
      {loading ? <Spinner /> : (
        <div className="mt-6 grid gap-4 sm:grid-cols-2">
          <div><h4 className="mb-2 text-sm font-semibold text-slate-700">Payments</h4>
            {data[0].length === 0 ? <p className="text-sm text-slate-400">No payments yet</p> : <ul className="space-y-1 text-sm">{data[0].slice(0, 5).map((p) => <li key={p.id} className="flex justify-between"><span>{fmtDate(p.paymentDate)} - {p.planName}</span><span className="font-medium">{fmtMoney(p.finalAmount)}</span></li>)}</ul>}
          </div>
          <div><h4 className="mb-2 text-sm font-semibold text-slate-700">Recent attendance ({data[1].length} total)</h4>
            {data[1].length === 0 ? <p className="text-sm text-slate-400">No check-ins yet</p> : <ul className="space-y-1 text-sm">{data[1].slice(0, 5).map((a) => <li key={a.id} className="flex justify-between"><span>{fmtDate(a.attendanceDate)}</span><span className="text-slate-500">{fmtTime(a.checkInTime)}</span></li>)}</ul>}
          </div>
        </div>
      )}
    </Modal>
  );
}

export default function Members() {
  const { isAdmin } = useAuth();
  const toast = useToast();
  const [filters, setFilters] = useState({ q: '', status: '', planId: '', paymentStatus: '', sortBy: '', direction: 'asc' });
  const [page, setPage] = useState(1);
  const [modal, setModal] = useState(null); // {type: 'form'|'view'|'delete', member?}
  const [busy, setBusy] = useState(false);
  const q = useDebounced(filters.q);
  const params = { ...filters, q };

  const lookups = useAsync(() => Promise.all([api.get('/api/plans'), api.get('/api/trainers')]), []);
  const list = useAsync(() => api.get('/api/members', params), [q, filters.status, filters.planId, filters.paymentStatus, filters.sortBy, filters.direction]);
  useEffect(() => setPage(1), [q, filters.status, filters.planId, filters.paymentStatus, filters.sortBy, filters.direction]);

  const set = (k) => (e) => setFilters({ ...filters, [k]: e.target.value });
  const pageRows = useMemo(() => (list.data || []).slice((page - 1) * PAGE_SIZE, page * PAGE_SIZE), [list.data, page]);
  const [plans, trainers] = lookups.data || [[], []];

  const remove = async () => {
    setBusy(true);
    try { await api.del(`/api/members/${modal.member.id}`); toast.success('Member deleted'); setModal(null); list.reload(); }
    catch (err) { toast.error(err.message); }
    setBusy(false);
  };
  const saved = () => { setModal(null); list.reload(); };

  return (
    <>
      <PageHeader title="Members" subtitle={list.data ? `${list.data.length} member(s) match your filters` : 'Manage gym members'}>
        <button className="btn-primary" onClick={() => setModal({ type: 'form' })} disabled={lookups.loading}><Plus className="h-4 w-4" /> Add member</button>
      </PageHeader>

      <div className="card mb-4 grid gap-3 p-4 sm:grid-cols-2 lg:grid-cols-6">
        <div className="relative lg:col-span-2">
          <Search className="absolute left-3 top-2.5 h-4 w-4 text-slate-400" />
          <input className="input pl-9" placeholder="Search name, ID, phone or email" value={filters.q} onChange={set('q')} aria-label="Search members" />
        </div>
        <select className="input" value={filters.status} onChange={set('status')} aria-label="Filter by status">
          <option value="">All statuses</option><option value="ACTIVE">Active</option><option value="EXPIRING_SOON">Expiring soon</option><option value="EXPIRED">Expired</option><option value="UPCOMING">Upcoming</option>
        </select>
        <select className="input" value={filters.planId} onChange={set('planId')} aria-label="Filter by plan">
          <option value="">All plans</option>{plans.map((p) => <option key={p.id} value={p.id}>{p.name}</option>)}
        </select>
        <select className="input" value={filters.paymentStatus} onChange={set('paymentStatus')} aria-label="Filter by payment status">
          <option value="">Any payment status</option><option value="PAID">Paid</option><option value="PENDING">Pending</option><option value="FAILED">Failed</option>
        </select>
        <div className="flex gap-2">
          <select className="input" value={filters.sortBy} onChange={set('sortBy')} aria-label="Sort by">
            <option value="">Newest first</option><option value="name">Name</option><option value="memberCode">Member ID</option><option value="expiry">Expiry date</option><option value="joinDate">Join date</option>
          </select>
          <button className="btn-secondary px-3" onClick={() => setFilters({ ...filters, direction: filters.direction === 'asc' ? 'desc' : 'asc' })} title="Toggle sort direction" disabled={!filters.sortBy}>{filters.direction === 'asc' ? 'A-Z' : 'Z-A'}</button>
        </div>
      </div>

      {list.loading && !list.data ? <Spinner text="Loading members..." /> : list.error ? <ErrorBox error={list.error} onRetry={list.reload} /> : (
        <div className="card overflow-hidden">
          {list.data.length === 0 ? <EmptyState title="No members found" hint="Try clearing the filters or register a new member." /> : (
            <>
              <div className="overflow-x-auto">
                <table className="min-w-full divide-y divide-slate-200">
                  <thead className="bg-slate-50"><tr>{['ID', 'Member', 'Phone', 'Plan', 'Expires', 'Status', 'Payment', 'Trainer', ''].map((h) => <th key={h} className="th">{h}</th>)}</tr></thead>
                  <tbody className="divide-y divide-slate-100">
                    {pageRows.map((m) => (
                      <tr key={m.id} className="hover:bg-slate-50">
                        <td className="td font-mono text-xs">{m.memberCode}</td>
                        <td className="td"><p className="font-medium text-slate-900">{m.fullName}</p><p className="text-xs text-slate-500">{m.email}</p></td>
                        <td className="td">{m.phone}</td>
                        <td className="td">{m.planName}</td>
                        <td className="td">{fmtDate(m.membershipEndDate)}<p className="text-xs text-slate-400">{m.remainingDays} day(s) left</p></td>
                        <td className="td"><Badge value={m.membershipStatus} /></td>
                        <td className="td"><Badge value={m.paymentStatus} /></td>
                        <td className="td">{m.trainerName || '-'}</td>
                        <td className="td text-right">
                          <button className="btn-ghost" onClick={() => setModal({ type: 'view', member: m })} aria-label={`View ${m.fullName}`}><Eye className="h-4 w-4" /></button>
                          {isAdmin && <>
                            <button className="btn-ghost" onClick={() => setModal({ type: 'form', member: m })} aria-label={`Edit ${m.fullName}`}><Pencil className="h-4 w-4" /></button>
                            <button className="btn-ghost text-red-600" onClick={() => setModal({ type: 'delete', member: m })} aria-label={`Delete ${m.fullName}`}><Trash2 className="h-4 w-4" /></button>
                          </>}
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
              <Pagination page={page} pageSize={PAGE_SIZE} total={list.data.length} onPage={setPage} />
            </>
          )}
        </div>
      )}

      {modal?.type === 'form' && <MemberForm member={modal.member} plans={plans} trainers={trainers} onClose={() => setModal(null)} onSaved={saved} />}
      {modal?.type === 'view' && <MemberDetails member={modal.member} onClose={() => setModal(null)} />}
      {modal?.type === 'delete' && <ConfirmDialog title="Delete member" busy={busy} onCancel={() => setModal(null)} onConfirm={remove}
        message={`Delete ${modal.member.fullName} (${modal.member.memberCode})? Their payments and attendance history will also be removed. This cannot be undone.`} />}
    </>
  );
}
