import React, { useEffect, useState, useCallback } from 'react';
import { analyticsApi, AnalyticsDashboard, QueueStatus } from '../services/api';
import { useSocket } from '../hooks/useSocket';
import { useAuth } from '../context/AuthContext';
import QueueSocket from "./QueueSocket";
import { Card, CardTitle, StatCard, Badge, LiveDot, Empty, Spinner } from '../components/UI';
import {
  BarChart, Bar, LineChart, Line, PieChart, Pie, Cell,
  XAxis, YAxis, Tooltip, ResponsiveContainer, CartesianGrid, Legend
} from 'recharts';

const PRIORITY_COLORS: Record<string, string> = {
  EMERGENCY: '#dc2626', VIP: '#7c3aed', SENIOR_CITIZEN: '#d97706', NORMAL: '#0d9488',
};
const PRIORITY_BG: Record<string, string> = {
  EMERGENCY: 'bg-red-500', VIP: 'bg-purple-500', SENIOR_CITIZEN: 'bg-amber-500', NORMAL: 'bg-teal-500',
};

export const DashboardPage = () => {
  const { user } = useAuth();
  const [analytics, setAnalytics] = useState<AnalyticsDashboard | null>(null);
  const [queues, setQueues]       = useState<QueueStatus[]>([]);
  const [events, setEvents]       = useState<{ msg: string; color: string; time: string }[]>([]);
  const [loading, setLoading]     = useState(true);

  const addEvent = (msg: string, color: string) => {
    const time = new Date().toLocaleTimeString('en-IN', { hour: '2-digit', minute: '2-digit', second: '2-digit' });
    setEvents(prev => [{ msg, color, time }, ...prev].slice(0, 10));
  };

  const fetchData = useCallback(async () => {
    try {
      const [aRes, qRes] = await Promise.all([
        analyticsApi.getDashboard(),
        analyticsApi.getAllQueues(),
      ]);
      setAnalytics(aRes.data.data);
      setQueues(qRes.data.data ?? []);
    } catch (e) {
      addEvent('Could not load live data', '#dc2626');
    } finally { setLoading(false); }
  }, []);

  useEffect(() => {
    fetchData();
    addEvent('Dashboard loaded — live data connected', '#0d9488');
    addEvent('WebSocket listening for queue events', '#16a34a');
  }, [fetchData]);

  // Subscribe to all active queue IDs
  {queues.map(q => (
  <QueueSocket
    key={q.queueId}
    queueId={q.queueId}
    queueName={q.queueName}
    fetchData={fetchData}
    addEvent={addEvent}
  />
))}

  if (loading) return <div className="flex justify-center py-20"><Spinner size={32} /></div>;

  const hourlyData = (analytics?.hourlyThroughput ?? []).map(h => ({
    hour: `${String(h.hour).padStart(2,'0')}:00`,
    patients: h.count,
  }));

  const priorityData = Object.entries(analytics?.priorityBreakdown ?? {})
    .map(([name, value]) => ({ name: name.replace('_', ' '), value, fill: PRIORITY_COLORS[name] ?? '#9ca3af' }))
    .filter(d => d.value > 0);

  return (
    <div className="max-w-7xl mx-auto px-4 sm:px-6 py-6 flex flex-col gap-5">

      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-lg font-semibold text-gray-800">Dashboard</h1>
          <p className="text-sm text-gray-400">Welcome back, {user?.name} · Live data</p>
        </div>
        <LiveDot />
      </div>

      {/* Real metrics */}
      <div className="grid grid-cols-2 sm:grid-cols-4 gap-3">
        <StatCard label="Waiting now"     value={analytics?.totalWaitingNow ?? 0}     color="blue"   sub="across all queues" />
        <StatCard label="Completed today" value={analytics?.totalCompletedToday ?? 0} color="green"  sub="consultations done" />
        <StatCard label="Avg wait time"   value={analytics?.avgWaitingTimeToday ? `${analytics.avgWaitingTimeToday.toFixed(1)}m` : '—'} color="teal" sub="real measured avg" />
        <StatCard label="No-show rate"    value={analytics?.noShowRateToday ? `${(analytics.noShowRateToday * 100).toFixed(1)}%` : '0%'} color="amber" sub={`${analytics?.totalNoShowsToday ?? 0} today`} />
      </div>

      {/* Live queues */}
      {queues.length === 0 ? (
        <Card><Empty message="No active queues. An admin needs to create queues first." /></Card>
      ) : queues.map(queue => (
        <Card key={queue.queueId}>
          <CardTitle>
            <svg className="w-4 h-4 text-teal-500" fill="none" stroke="currentColor" strokeWidth={2} viewBox="0 0 24 24">
              <path strokeLinecap="round" d="M17 21v-2a4 4 0 0 0-4-4H5a4 4 0 0 0-4 4v2"/>
              <circle cx="9" cy="7" r="4"/><path d="M23 21v-2a4 4 0 0 0-3-3.87M16 3.13a4 4 0 0 1 0 7.75"/>
            </svg>
            {queue.queueName} — {queue.doctorName}
            <span className="ml-auto text-xs font-normal text-gray-400">{queue.totalWaiting} waiting · Token #{queue.currentToken}</span>
          </CardTitle>
          {queue.appointments.length === 0 ? (
            <Empty message="No patients currently waiting." />
          ) : (
            <div className="flex flex-col gap-2">
              {queue.appointments.slice(0, 6).map(appt => (
                <div key={appt.id} className="flex items-center gap-3 p-3 bg-gray-50 rounded-lg">
                  <div className={`w-10 h-10 rounded-lg flex items-center justify-center text-white text-xs font-bold flex-shrink-0 ${PRIORITY_BG[appt.priority] ?? 'bg-teal-500'}`}>
                    T-{String(appt.tokenNumber).padStart(2,'0')}
                  </div>
                  <div className="flex-1 min-w-0">
                    <p className="text-sm font-medium text-gray-700 truncate">{appt.patientName}</p>
                    <div className="flex gap-1.5 mt-0.5"><Badge label={appt.priority} /><Badge label={appt.status} /></div>
                  </div>
                  <div className="text-right flex-shrink-0">
                    <p className="text-sm font-semibold text-teal-600">
                      {appt.predictedVisitTime
                        ? new Date(appt.predictedVisitTime).toLocaleTimeString('en-IN',{hour:'2-digit',minute:'2-digit'})
                        : '—'}
                    </p>
                    <p className="text-xs text-gray-400">AI ±{appt.predictionConfidence ?? '?'}m</p>
                  </div>
                </div>
              ))}
              {queue.appointments.length > 6 && (
                <p className="text-xs text-gray-400 text-center py-1">+{queue.appointments.length - 6} more patients</p>
              )}
            </div>
          )}
        </Card>
      ))}

      {/* Charts grid */}
      <div className="grid grid-cols-1 lg:grid-cols-2 gap-5">

        {/* Hourly throughput — real data */}
        <Card>
          <CardTitle>Hourly throughput (today)</CardTitle>
          {hourlyData.length === 0
            ? <Empty message="No completed consultations yet today." />
            : (
            <ResponsiveContainer width="100%" height={180}>
              <BarChart data={hourlyData} barSize={18}>
                <CartesianGrid strokeDasharray="3 3" stroke="#f0f0f0" />
                <XAxis dataKey="hour" tick={{ fontSize: 10, fill: '#9ca3af' }} axisLine={false} tickLine={false} />
                <YAxis tick={{ fontSize: 10, fill: '#9ca3af' }} axisLine={false} tickLine={false} allowDecimals={false} />
                <Tooltip contentStyle={{ borderRadius: 8, border: 'none', boxShadow: '0 2px 8px rgba(0,0,0,.1)', fontSize: 12 }} />
                <Bar dataKey="patients" fill="#0d9488" radius={[4,4,0,0]} name="Patients" />
              </BarChart>
            </ResponsiveContainer>
          )}
        </Card>

        {/* Priority breakdown pie — real data */}
        <Card>
          <CardTitle>Priority breakdown (today)</CardTitle>
          {priorityData.length === 0
            ? <Empty message="No appointments booked yet today." />
            : (
            <ResponsiveContainer width="100%" height={180}>
              <PieChart>
                <Pie data={priorityData} cx="50%" cy="50%" innerRadius={45} outerRadius={75}
                  dataKey="value" nameKey="name" paddingAngle={3}>
                  {priorityData.map((entry, i) => <Cell key={i} fill={entry.fill} />)}
                </Pie>
                <Tooltip contentStyle={{ borderRadius: 8, border: 'none', fontSize: 12 }}
                  formatter={(v: any, n: string) => [`${v} patients`, n]} />
                <Legend iconType="circle" iconSize={8}
                  formatter={(v) => <span style={{fontSize:11,color:'#6b7280'}}>{v}</span>} />
              </PieChart>
            </ResponsiveContainer>
          )}
        </Card>

        {/* Doctor loads — real data */}
        <Card>
          <CardTitle>Doctor queue loads</CardTitle>
          {(analytics?.doctorLoads ?? []).length === 0
            ? <Empty message="No doctors found." />
            : (
            <div className="flex flex-col gap-2 mt-1">
              {(analytics?.doctorLoads ?? []).map(dl => (
                <div key={dl.doctorName} className="flex items-center gap-3">
                  <div className={`w-2 h-2 rounded-full flex-shrink-0 ${
                    dl.waitingCount === 0 ? 'bg-gray-300' :
                    dl.waitingCount < 5 ? 'bg-green-500' :
                    dl.waitingCount < 10 ? 'bg-amber-500' : 'bg-red-500'
                  }`} />
                  <span className="text-xs text-gray-600 w-36 truncate flex-shrink-0">{dl.doctorName}</span>
                  <div className="flex-1 bg-gray-100 rounded-full h-2 overflow-hidden">
                    <div className="h-full rounded-full bg-teal-500 transition-all"
                      style={{ width: `${Math.min(100, (dl.waitingCount / 15) * 100)}%` }} />
                  </div>
                  <span className="text-xs text-gray-500 w-14 text-right flex-shrink-0">
                    {dl.waitingCount} waiting
                  </span>
                </div>
              ))}
            </div>
          )}
        </Card>

        {/* Live events feed */}
        <Card>
          <CardTitle>
            <svg className="w-4 h-4 text-teal-500" fill="none" stroke="currentColor" strokeWidth={2} viewBox="0 0 24 24">
              <path strokeLinecap="round" d="M22 12h-4l-3 9L9 3l-3 9H2"/>
            </svg>
            Real-time events
          </CardTitle>
          <div className="flex flex-col gap-1.5 max-h-52 overflow-y-auto">
            {events.length === 0 && <Empty message="No events yet." />}
            {events.map((e, i) => (
              <div key={i} className="flex items-center gap-2 px-2 py-1.5 rounded-lg bg-gray-50 text-xs">
                <span className="w-2 h-2 rounded-full flex-shrink-0" style={{ background: e.color }} />
                <span className="flex-1 text-gray-600">{e.msg}</span>
                <span className="text-gray-400 flex-shrink-0">{e.time}</span>
              </div>
            ))}
          </div>
        </Card>
      </div>
    </div>
  );
};
