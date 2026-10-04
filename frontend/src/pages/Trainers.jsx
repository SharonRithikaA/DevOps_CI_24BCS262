import { useState } from 'react';
import { Mail, Pencil, Phone, Plus, Trash2, Users } from 'lucide-react';
import { api } from '../api.js';
import { useAuth } from '../auth.jsx';
import { useToast } from '../toast.jsx';
import { Badge, ConfirmDialog, EmptyState, ErrorBox, Field, Modal, PageHeader, Spinner, SubmitButton, inputClass } from '../components/ui.jsx';
import { PHONE_RE, useAsync } from '../utils.js';

function TrainerForm({ trainer, onClose, onSaved }) {
  const toast = useToast();
  const [form, setForm] = useState(trainer ? { name: trainer.name, email: trainer.email, phone: trainer.phone, specialization: trainer.specialization || '', experienceYears: String(trainer.experienceYears), availability: trainer.availability || '', active: trainer.active } : { name: '', email: '', phone: '', specialization: '', experienceYears: '0', availability: '', active: true });
  const [errors, setErrors] = useState({});
  const [busy, setBusy] = useState(false);
  const set = (k) => (e) => setForm({ ...form, [k]: e.target.value });

  const submit = async (e) => {
    e.preventDefault();
    const errs = {};
    if (!form.name.trim()) errs.name = 'Name is required';
    if (!form.email.trim()) errs.email = 'Email is required';
    if (!form.phone.trim() || !PHONE_RE.test(form.phone.trim())) errs.phone = 'Enter a valid phone number';
    if (form.experienceYears === '' || Number(form.experienceYears) < 0) errs.experienceYears = 'Experience cannot be negative';
    setErrors(errs);
    if (Object.keys(errs).length) return;
    setBusy(true);
    try {
      const body = { ...form, name: form.name.trim(), email: form.email.trim(), phone: form.phone.trim(), experienceYears: Number(form.experienceYears) };
      if (trainer) await api.put(`/api/trainers/${trainer.id}`, body); else await api.post('/api/trainers', body);
      toast.success(trainer ? 'Trainer updated' : 'Trainer added');
      onSaved();
    } catch (err) { setErrors(err.fieldErrors || {}); toast.error(err.message); setBusy(false); }
  };

  return (
    <Modal title={trainer ? 'Edit trainer' : 'Add trainer'} onClose={onClose}
      footer={<><button className="btn-secondary" onClick={onClose}>Cancel</button><SubmitButton busy={busy} form="trainer-form" type="submit">Save trainer</SubmitButton></>}>
      <form id="trainer-form" onSubmit={submit} noValidate className="grid gap-4 sm:grid-cols-2">
        <Field label="Name" required error={errors.name}><input className={inputClass(errors.name)} value={form.name} onChange={set('name')} /></Field>
        <Field label="Email" required error={errors.email}><input type="email" className={inputClass(errors.email)} value={form.email} onChange={set('email')} /></Field>
        <Field label="Phone" required error={errors.phone}><input className={inputClass(errors.phone)} value={form.phone} onChange={set('phone')} /></Field>
        <Field label="Experience (years)" required error={errors.experienceYears}><input type="number" min="0" className={inputClass(errors.experienceYears)} value={form.experienceYears} onChange={set('experienceYears')} /></Field>
        <Field label="Specialization" error={errors.specialization}><input className={inputClass(errors.specialization)} value={form.specialization} onChange={set('specialization')} placeholder="e.g. Strength & Conditioning" /></Field>
        <Field label="Availability" error={errors.availability}><input className={inputClass(errors.availability)} value={form.availability} onChange={set('availability')} placeholder="e.g. Mon-Sat, 6 AM - 2 PM" /></Field>
        <label className="flex items-center gap-2 text-sm sm:col-span-2"><input type="checkbox" checked={form.active} onChange={(e) => setForm({ ...form, active: e.target.checked })} /> Active</label>
      </form>
    </Modal>
  );
}

export default function Trainers() {
  const { isAdmin } = useAuth();
  const toast = useToast();
  const { data, loading, error, reload } = useAsync(() => api.get('/api/trainers'), []);
  const [modal, setModal] = useState(null);
  const [busy, setBusy] = useState(false);

  const remove = async () => {
    setBusy(true);
    try { await api.del(`/api/trainers/${modal.trainer.id}`); toast.success('Trainer removed'); setModal(null); reload(); }
    catch (err) { toast.error(err.message); setModal(null); }
    setBusy(false);
  };

  return (
    <>
      <PageHeader title="Trainers" subtitle={isAdmin ? 'Manage trainers and their assigned members' : 'Gym trainers (read only)'}>
        {isAdmin && <button className="btn-primary" onClick={() => setModal({ type: 'form' })}><Plus className="h-4 w-4" /> Add trainer</button>}
      </PageHeader>
      {loading ? <Spinner /> : error ? <ErrorBox error={error} onRetry={reload} /> : data.length === 0 ? <div className="card"><EmptyState title="No trainers yet" /></div> : (
        <div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-3">
          {data.map((t) => (
            <div key={t.id} className="card p-5">
              <div className="flex items-start justify-between">
                <div><h3 className="text-lg font-semibold text-slate-900">{t.name}</h3><p className="text-sm text-slate-500">{t.specialization || 'General trainer'}</p></div>
                <Badge value={t.active ? 'ACTIVE' : 'INACTIVE'} text={t.active ? 'Active' : 'Inactive'} />
              </div>
              <div className="mt-3 space-y-1.5 text-sm text-slate-600">
                <p className="flex items-center gap-2"><Mail className="h-3.5 w-3.5 text-slate-400" /> {t.email}</p>
                <p className="flex items-center gap-2"><Phone className="h-3.5 w-3.5 text-slate-400" /> {t.phone}</p>
                <p className="flex items-center gap-2"><Users className="h-3.5 w-3.5 text-slate-400" /> {t.assignedCount} member(s) assigned</p>
              </div>
              <p className="mt-2 text-xs text-slate-400">{t.experienceYears} yr(s) experience - {t.availability || 'Availability not set'}</p>
              {t.assignedMembers.length > 0 && <p className="mt-2 truncate text-xs text-slate-500" title={t.assignedMembers.map((m) => m.fullName).join(', ')}>Members: {t.assignedMembers.map((m) => m.fullName).join(', ')}</p>}
              {isAdmin && (
                <div className="mt-4 flex gap-2 border-t border-slate-100 pt-3">
                  <button className="btn-secondary flex-1 py-1.5" onClick={() => setModal({ type: 'form', trainer: t })}><Pencil className="h-4 w-4" /> Edit</button>
                  <button className="btn-secondary py-1.5 text-red-600" onClick={() => setModal({ type: 'delete', trainer: t })} aria-label={`Remove ${t.name}`}><Trash2 className="h-4 w-4" /></button>
                </div>
              )}
            </div>
          ))}
        </div>
      )}
      {modal?.type === 'form' && <TrainerForm trainer={modal.trainer} onClose={() => setModal(null)} onSaved={() => { setModal(null); reload(); }} />}
      {modal?.type === 'delete' && <ConfirmDialog title="Remove trainer" busy={busy} onCancel={() => setModal(null)} onConfirm={remove} message={`Remove ${modal.trainer.trainer?.name || modal.trainer.name}? Assigned members will be unassigned, not deleted.`} />}
    </>
  );
}
