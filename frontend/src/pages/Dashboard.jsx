import { Activity, CalendarCheck, IndianRupee, TimerOff, UserCheck, UserCog, Users } from 'lucide-react';
import { Area, AreaChart, Bar, BarChart, CartesianGrid, Cell, Legend, Pie, PieChart, ResponsiveContainer, Tooltip, XAxis, YAxis } from 'recharts';
import { api } from '../api.js';
import { Badge, EmptyState, ErrorBox, PageHeader, Spinner, StatCard } from '../components/ui.jsx';
import { fmtDate, fmtMoney, useAsync } from '../utils.js';

const COLORS = ['#10b981', '#3b82f6', '#f59e0b', '#8b5cf6', '#ef4444'];
const STATUS_COLORS = { Active: '#10b981', 'Expiring soon': '#f59e0b', Expired: '#ef4444' };

const toChart = (points) => (points || []).map((p) => ({ name: p.label, value: Number(p.value) }));

function ChartCard({ title, children }) {
  return (
    <div className="card p-5">
      <h3 className="mb-4 text-sm font-semibold text-slate-700">{title}</h3>
      <div className="h-64">{children}</div>
    </div>
  );
}

export default function Dashboard() {
  const { data, loading, error, reload } = useAsync(() => api.get('/api/dashboard'), []);
  if (loading) return <Spinner text="Loading dashboard..." />;
  if (error) return <ErrorBox error={error} onRetry={reload} />;

  const revenue = toChart(data.monthlyRevenueChart);
  const plans = toChart(data.planDistribution).filter((p) => p.value > 0);
  const attendance = toChart(data.attendanceTrend);
  const status = toChart(data.statusBreakdown).filter((p) => p.value > 0);

  return (
    <>
      <PageHeader title="Dashboard" subtitle="Overview of memberships, revenue and activity" />
      <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 xl:grid-cols-3">
        <StatCard icon={Users} title="Total Members" value={data.totalMembers} tone="sky" />
        <StatCard icon={UserCheck} title="Active Members" value={data.activeMembers} hint={`${data.expiringSoon} expiring within 7 days`} />
        <StatCard icon={TimerOff} title="Expired Memberships" value={data.expiredMembers} tone="red" />
        <StatCard icon={CalendarCheck} title="Today's Attendance" value={data.todayAttendance} tone="violet" />
        <StatCard icon={IndianRupee} title="Monthly Revenue" value={fmtMoney(data.monthlyRevenue)} hint="Paid, this calendar month" tone="amber" />
        <StatCard icon={UserCog} title="Active Trainers" value={data.activeTrainers} tone="slate" />
      </div>

      <div className="mt-6 grid gap-4 lg:grid-cols-2">
        <ChartCard title="Monthly revenue (last 6 months)">
          <ResponsiveContainer width="100%" height="100%">
            <BarChart data={revenue}>
              <CartesianGrid strokeDasharray="3 3" vertical={false} />
              <XAxis dataKey="name" tickLine={false} axisLine={false} fontSize={12} />
              <YAxis tickLine={false} axisLine={false} fontSize={12} width={56} tickFormatter={(v) => (v >= 1000 ? `${v / 1000}k` : v)} />
              <Tooltip formatter={(v) => fmtMoney(v)} />
              <Bar dataKey="value" name="Revenue" fill="#10b981" radius={[6, 6, 0, 0]} />
            </BarChart>
          </ResponsiveContainer>
        </ChartCard>

        <ChartCard title="Attendance trend (last 14 days)">
          <ResponsiveContainer width="100%" height="100%">
            <AreaChart data={attendance}>
              <defs><linearGradient id="att" x1="0" y1="0" x2="0" y2="1"><stop offset="5%" stopColor="#8b5cf6" stopOpacity={0.4} /><stop offset="95%" stopColor="#8b5cf6" stopOpacity={0} /></linearGradient></defs>
              <CartesianGrid strokeDasharray="3 3" vertical={false} />
              <XAxis dataKey="name" tickLine={false} axisLine={false} fontSize={11} interval="preserveStartEnd" />
              <YAxis tickLine={false} axisLine={false} fontSize={12} allowDecimals={false} width={30} />
              <Tooltip />
              <Area type="monotone" dataKey="value" name="Check-ins" stroke="#8b5cf6" strokeWidth={2} fill="url(#att)" />
            </AreaChart>
          </ResponsiveContainer>
        </ChartCard>

        <ChartCard title="Membership distribution by plan">
          {plans.length === 0 ? <EmptyState title="No members yet" /> : (
            <ResponsiveContainer width="100%" height="100%">
              <PieChart>
                <Pie data={plans} dataKey="value" nameKey="name" innerRadius={55} outerRadius={90} paddingAngle={2}>
                  {plans.map((_, i) => <Cell key={i} fill={COLORS[i % COLORS.length]} />)}
                </Pie>
                <Tooltip /><Legend />
              </PieChart>
            </ResponsiveContainer>
          )}
        </ChartCard>

        <ChartCard title="Active vs expiring vs expired">
          {status.length === 0 ? <EmptyState title="No members yet" /> : (
            <ResponsiveContainer width="100%" height="100%">
              <PieChart>
                <Pie data={status} dataKey="value" nameKey="name" outerRadius={90} paddingAngle={2} label={({ value }) => value}>
                  {status.map((s) => <Cell key={s.name} fill={STATUS_COLORS[s.name]} />)}
                </Pie>
                <Tooltip /><Legend />
              </PieChart>
            </ResponsiveContainer>
          )}
        </ChartCard>
      </div>

      <div className="mt-6 grid gap-4 lg:grid-cols-2">
        <div className="card">
          <h3 className="flex items-center gap-2 border-b border-slate-200 px-5 py-3 text-sm font-semibold text-slate-700"><Activity className="h-4 w-4 text-amber-500" /> Expiring within 7 days</h3>
          {data.expiringMembers.length === 0 ? <EmptyState title="No memberships expiring soon" /> : (
            <ul className="divide-y divide-slate-100">
              {data.expiringMembers.map((m) => (
                <li key={m.id} className="flex items-center justify-between px-5 py-3 text-sm">
                  <div><p className="font-medium text-slate-800">{m.fullName}</p><p className="text-xs text-slate-500">{m.planName} - ends {fmtDate(m.membershipEndDate)}</p></div>
                  <Badge value="EXPIRING_SOON" text={m.remainingDays === 0 ? 'Last day' : `${m.remainingDays} day${m.remainingDays === 1 ? '' : 's'} left`} />
                </li>
              ))}
            </ul>
          )}
        </div>
        <div className="card">
          <h3 className="flex items-center gap-2 border-b border-slate-200 px-5 py-3 text-sm font-semibold text-slate-700"><IndianRupee className="h-4 w-4 text-brand-600" /> Recent payments</h3>
          {data.recentPayments.length === 0 ? <EmptyState title="No payments recorded" /> : (
            <ul className="divide-y divide-slate-100">
              {data.recentPayments.map((p) => (
                <li key={p.id} className="flex items-center justify-between px-5 py-3 text-sm">
                  <div><p className="font-medium text-slate-800">{p.memberName}</p><p className="text-xs text-slate-500">{p.planName} - {fmtDate(p.paymentDate)}</p></div>
                  <div className="text-right"><p className="font-semibold">{fmtMoney(p.finalAmount)}</p><Badge value={p.status} /></div>
                </li>
              ))}
            </ul>
          )}
        </div>
      </div>
    </>
  );
}
