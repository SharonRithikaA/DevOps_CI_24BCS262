import { useState } from 'react';
import { KeyRound } from 'lucide-react';
import { api } from '../api.js';
import { useAuth } from '../auth.jsx';
import { useToast } from '../toast.jsx';
import { Badge, Field, PageHeader, SubmitButton, inputClass } from '../components/ui.jsx';

export default function Settings() {
  const { user } = useAuth();
  const toast = useToast();
  const [form, setForm] = useState({ currentPassword: '', newPassword: '', confirmPassword: '' });
  const [errors, setErrors] = useState({});
  const [busy, setBusy] = useState(false);
  const set = (k) => (e) => setForm({ ...form, [k]: e.target.value });

  const submit = async (e) => {
    e.preventDefault();
    const errs = {};
    if (!form.currentPassword) errs.currentPassword = 'Current password is required';
    if (!form.newPassword || form.newPassword.length < 8) errs.newPassword = 'New password must be at least 8 characters';
    if (form.newPassword !== form.confirmPassword) errs.confirmPassword = 'Passwords do not match';
    setErrors(errs);
    if (Object.keys(errs).length) return;
    setBusy(true);
    try {
      await api.post('/api/auth/change-password', { currentPassword: form.currentPassword, newPassword: form.newPassword });
      toast.success('Password changed');
      setForm({ currentPassword: '', newPassword: '', confirmPassword: '' });
    } catch (err) { setErrors(err.fieldErrors || {}); toast.error(err.message); }
    setBusy(false);
  };

  return (
    <>
      <PageHeader title="Settings" subtitle="Your account details" />
      <div className="grid gap-4 lg:grid-cols-2">
        <div className="card p-5">
          <h2 className="mb-3 text-sm font-semibold text-slate-700">Account</h2>
          <dl className="space-y-2 text-sm">
            <div className="flex justify-between"><dt className="text-slate-500">Username</dt><dd className="font-medium text-slate-800">{user.username}</dd></div>
            <div className="flex justify-between"><dt className="text-slate-500">Full name</dt><dd className="font-medium text-slate-800">{user.fullName}</dd></div>
            <div className="flex justify-between"><dt className="text-slate-500">Role</dt><dd><Badge value={user.role} /></dd></div>
          </dl>
        </div>
        <div className="card p-5">
          <h2 className="mb-3 flex items-center gap-2 text-sm font-semibold text-slate-700"><KeyRound className="h-4 w-4" /> Change password</h2>
          <form onSubmit={submit} noValidate className="space-y-3">
            <Field label="Current password" required error={errors.currentPassword}><input type="password" className={inputClass(errors.currentPassword)} value={form.currentPassword} onChange={set('currentPassword')} /></Field>
            <Field label="New password" required error={errors.newPassword} hint="At least 8 characters"><input type="password" className={inputClass(errors.newPassword)} value={form.newPassword} onChange={set('newPassword')} /></Field>
            <Field label="Confirm new password" required error={errors.confirmPassword}><input type="password" className={inputClass(errors.confirmPassword)} value={form.confirmPassword} onChange={set('confirmPassword')} /></Field>
            <SubmitButton busy={busy} type="submit">Change password</SubmitButton>
          </form>
        </div>
      </div>
    </>
  );
}
