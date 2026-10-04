import { useState } from 'react';
import { Clock, Pencil, Plus, Trash2, Users } from 'lucide-react';
import { api } from '../api.js';
import { useAuth } from '../auth.jsx';
import { useToast } from '../toast.jsx';
import { Badge, ConfirmDialog, EmptyState, ErrorBox, Field, Modal, PageHeader, Spinner, SubmitButton, inputClass } from '../components/ui.jsx';
import { fmtMoney, useAsync } from '../utils.js';

function PlanForm({ plan, onClose, onSaved }) {
  const toast = useToast();
  const [form, setForm] = useState(plan ? { name: plan.name, durationMonths: String(plan.durationMonths), price: String(plan.price), description: plan.description || '', active: plan.active } : { name: '', durationMonths: '1', price: '', description: '', active: true });
  const [errors, setErrors] = useState({});
  const [busy, setBusy] = useState(false);

  const submit = async (e) => {
    e.preventDefault();
    const errs = {};
    if (!form.name.trim()) errs.name = 'Plan name is required';
    if (!(Number(form.durationMonths) >= 1)) errs.durationMonths = 'Duration must be at least 1 month';
    if (!(Number(form.price) > 0)) errs.price = 'Price must be greater than zero';
    setErrors(errs);
    if (Object.keys(errs).length) return;
    setBusy(true);
    try {
      const body = { ...form, name: form.name.trim(), durationMonths: Number(form.durationMonths), price: Number(form.price) };
      if (plan) await api.put(`/api/plans/${plan.id}`, body); else await api.post('/api/plans', body);
      toast.success(plan ? 'Plan updated' : 'Plan created');
      onSaved();
    } catch (err) { setErrors(err.fieldErrors || {}); toast.error(err.message); setBusy(false); }
  };
  const set = (k) => (e) => setForm({ ...form, [k]: e.target.value });

  return (
    <Modal title={plan ? 'Edit plan' : 'New membership plan'} onClose={onClose} size="max-w-lg"
      footer={<><button className="btn-secondary" onClick={onClose}>Cancel</button><SubmitButton busy={busy} form="plan-form" type="submit">Save plan</SubmitButton></>}>
      <form id="plan-form" onSubmit={submit} noValidate className="grid gap-4 sm:grid-cols-2">
        <div className="sm:col-span-2"><Field label="Plan name" required error={errors.name}><input className={inputClass(errors.name)} value={form.name} onChange={set('name')} /></Field></div>
        <Field label="Duration (months)" required error={errors.durationMonths}><input type="number" min="1" className={inputClass(errors.durationMonths)} value={form.durationMonths} onChange={set('durationMonths')} /></Field>
        <Field label="Price (INR)" required error={errors.price}><input type="number" min="0" step="0.01" className={inputClass(errors.price)} value={form.price} onChange={set('price')} /></Field>
        <div className="sm:col-span-2"><Field label="Description" error={errors.description}><textarea rows={2} className={inputClass(errors.description)} value={form.description} onChange={set('description')} /></Field></div>
        <label className="flex items-center gap-2 text-sm"><input type="checkbox" checked={form.active} onChange={(e) => setForm({ ...form, active: e.target.checked })} /> Active (available for new members)</label>
      </form>
    </Modal>
  );
}

export default function Plans() {
  const { isAdmin } = useAuth();
  const toast = useToast();
  const { data, loading, error, reload } = useAsync(() => api.get('/api/plans'), []);
  const [modal, setModal] = useState(null);
  const [busy, setBusy] = useState(false);

  const remove = async () => {
    setBusy(true);
    try { await api.del(`/api/plans/${modal.plan.id}`); toast.success('Plan deleted'); setModal(null); reload(); }
    catch (err) { toast.error(err.message); setModal(null); }
    setBusy(false);
  };

  return (
    <>
      <PageHeader title="Membership Plans" subtitle={isAdmin ? 'Create and manage the plans members can buy' : 'Available membership plans (read only)'}>
        {isAdmin && <button className="btn-primary" onClick={() => setModal({ type: 'form' })}><Plus className="h-4 w-4" /> Add plan</button>}
      </PageHeader>
      {loading ? <Spinner /> : error ? <ErrorBox error={error} onRetry={reload} /> : data.length === 0 ? <div className="card"><EmptyState title="No plans yet" hint="Add your first membership plan." /></div> : (
        <div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-4">
          {data.map((p) => (
            <div key={p.id} className="card flex flex-col p-5">
              <div className="flex items-start justify-between"><h3 className="text-lg font-semibold text-slate-900">{p.name}</h3><Badge value={p.active ? 'ACTIVE' : 'INACTIVE'} text={p.active ? 'Active' : 'Inactive'} /></div>
              <p className="mt-3 text-3xl font-bold text-brand-700">{fmtMoney(p.price)}</p>
              <p className="mt-1 flex items-center gap-1.5 text-sm text-slate-500"><Clock className="h-4 w-4" /> {p.durationMonths} month{p.durationMonths > 1 ? 's' : ''}</p>
              <p className="mt-3 flex-1 text-sm text-slate-600">{p.description || 'No description'}</p>
              <p className="mt-3 flex items-center gap-1.5 text-xs text-slate-400"><Users className="h-3.5 w-3.5" /> {p.memberCount} member(s)</p>
              {isAdmin && (
                <div className="mt-4 flex gap-2 border-t border-slate-100 pt-3">
                  <button className="btn-secondary flex-1 py-1.5" onClick={() => setModal({ type: 'form', plan: p })}><Pencil className="h-4 w-4" /> Edit</button>
                  <button className="btn-secondary py-1.5 text-red-600" onClick={() => setModal({ type: 'delete', plan: p })} aria-label={`Delete ${p.name}`}><Trash2 className="h-4 w-4" /></button>
                </div>
              )}
            </div>
          ))}
        </div>
      )}
      {modal?.type === 'form' && <PlanForm plan={modal.plan} onClose={() => setModal(null)} onSaved={() => { setModal(null); reload(); }} />}
      {modal?.type === 'delete' && <ConfirmDialog title="Delete plan" busy={busy} onCancel={() => setModal(null)} onConfirm={remove} message={`Delete the ${modal.plan.name} plan? Plans already used by members or payments cannot be deleted; mark them inactive instead.`} />}
    </>
  );
}
