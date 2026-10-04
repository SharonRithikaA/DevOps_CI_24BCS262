import { useState } from 'react';
import { Dumbbell, Loader2, Lock, User } from 'lucide-react';
import { useAuth } from '../auth.jsx';

const SHOW_DEMO = import.meta.env.VITE_SHOW_DEMO_LOGINS !== 'false';

export default function Login() {
  const { login } = useAuth();
  const [form, setForm] = useState({ username: '', password: '' });
  const [errors, setErrors] = useState({});
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(false);

  const submit = async (e) => {
    e.preventDefault();
    const errs = {};
    if (!form.username.trim()) errs.username = 'Username is required';
    if (!form.password) errs.password = 'Password is required';
    setErrors(errs);
    setError('');
    if (Object.keys(errs).length) return;
    setBusy(true);
    try {
      await login(form.username.trim(), form.password);
    } catch (err) {
      setError(err.message);
      setBusy(false);
    }
  };

  const fill = (username, password) => setForm({ username, password });

  return (
    <div className="flex min-h-screen">
      <div className="hidden flex-1 flex-col justify-between bg-gradient-to-br from-slate-900 via-slate-900 to-brand-900 p-12 text-white lg:flex">
        <div className="flex items-center gap-2 text-xl font-bold"><span className="rounded-lg bg-brand-500 p-2"><Dumbbell className="h-6 w-6" /></span> IronPulse Gym</div>
        <div>
          <h2 className="text-4xl font-bold leading-tight">Run your gym,<br />not your spreadsheets.</h2>
          <p className="mt-4 max-w-md text-slate-300">Members, plans, payments, attendance and trainers in one place, with expiry tracking and revenue reports built in.</p>
        </div>
        <p className="text-sm text-slate-400">Gym Membership Management System</p>
      </div>

      <div className="flex flex-1 items-center justify-center p-6">
        <form onSubmit={submit} className="card w-full max-w-md space-y-5 p-8" noValidate>
          <div>
            <h1 className="text-2xl font-bold text-slate-900">Welcome back</h1>
            <p className="text-sm text-slate-500">Sign in to the management dashboard.</p>
          </div>
          {error && <div role="alert" className="rounded-lg border border-red-200 bg-red-50 px-3 py-2 text-sm text-red-700">{error}</div>}

          <label className="block">
            <span className="mb-1 block text-sm font-medium text-slate-700">Username</span>
            <div className="relative">
              <User className="absolute left-3 top-2.5 h-4 w-4 text-slate-400" />
              <input className={`input pl-9 ${errors.username ? 'input-error' : ''}`} value={form.username} autoComplete="username"
                onChange={(e) => setForm({ ...form, username: e.target.value })} placeholder="admin" autoFocus />
            </div>
            {errors.username && <span className="mt-1 block text-xs text-red-600">{errors.username}</span>}
          </label>
          <label className="block">
            <span className="mb-1 block text-sm font-medium text-slate-700">Password</span>
            <div className="relative">
              <Lock className="absolute left-3 top-2.5 h-4 w-4 text-slate-400" />
              <input type="password" className={`input pl-9 ${errors.password ? 'input-error' : ''}`} value={form.password} autoComplete="current-password"
                onChange={(e) => setForm({ ...form, password: e.target.value })} placeholder="••••••••" />
            </div>
            {errors.password && <span className="mt-1 block text-xs text-red-600">{errors.password}</span>}
          </label>

          <button className="btn-primary w-full py-2.5" disabled={busy}>{busy && <Loader2 className="h-4 w-4 animate-spin" />}Sign in</button>

          {SHOW_DEMO && (
            <div className="rounded-lg bg-slate-50 p-3 text-xs text-slate-500">
              <p className="mb-2 font-medium text-slate-600">Demo accounts (development only)</p>
              <div className="flex gap-2">
                <button type="button" className="btn-secondary flex-1 py-1.5 text-xs" onClick={() => fill('admin', 'Admin@123')}>Fill admin</button>
                <button type="button" className="btn-secondary flex-1 py-1.5 text-xs" onClick={() => fill('staff', 'Staff@123')}>Fill staff</button>
              </div>
            </div>
          )}
        </form>
      </div>
    </div>
  );
}
