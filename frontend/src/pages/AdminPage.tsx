import React, { useEffect, useState, useCallback } from 'react';
import {
  adminApi, DoctorResponse, UserResponse, QueueStatus,
  AnalyticsDashboard, AppointmentResponse
} from '../services/api';
import api from '../services/api';
import { useSocket } from '../hooks/useSocket';
import { Card, CardTitle, Badge, Button, StatCard, Empty, Spinner, Input, Select } from '../components/UI';
import { useToast } from '../components/UI';
import { BarChart, Bar, XAxis, YAxis, Tooltip, ResponsiveContainer, CartesianGrid } from 'recharts';

// BUG 7: Tabs now include payments
type Tab = 'queue' | 'doctors' | 'users' | 'queues' | 'appointments' | 'payments';

const PRIORITY_OPTS = [
  { value:'NORMAL', label:'Normal' }, { value:'SENIOR_CITIZEN', label:'Senior Citizen' },
  { value:'VIP', label:'VIP' },       { value:'EMERGENCY', label:'Emergency' },
];
const AVAIL_OPTS = [
  { value:'AVAILABLE', label:'Available' }, { value:'BUSY', label:'Busy' },
  { value:'ON_BREAK', label:'On Break' },   { value:'OFFLINE', label:'Offline' },
];
const ROLE_OPTS = [
  { value:'PATIENT', label:'Patient' }, { value:'DOCTOR', label:'Doctor' }, { value:'ADMIN', label:'Admin' },
];
const STATUS_COL: Record<string,string> = {
  AVAILABLE:'bg-green-500', BUSY:'bg-amber-500', ON_BREAK:'bg-blue-400', OFFLINE:'bg-gray-400',
};
const PRI_BG: Record<string,string> = {
  EMERGENCY:'bg-red-500', VIP:'bg-purple-500', SENIOR_CITIZEN:'bg-amber-500', NORMAL:'bg-teal-500',
};

/* ── Doctor Modal ─────────────────────────────────────────────── */
const DoctorModal = ({ doctor, onClose, onSave }: {
  doctor: DoctorResponse | null; onClose: () => void; onSave: () => void;
}) => {
  const toast = useToast();
  const [form, setForm] = useState({
    name: doctor?.name ?? '', specialization: doctor?.specialization ?? '',
    avgConsultationTime: String(doctor?.avgConsultationTime ?? 15),
    roomNumber: doctor?.roomNumber ?? '',
  });
  const [saving, setSaving] = useState(false);
  const set = (k: string) => (v: string) => setForm(f => ({ ...f, [k]: v }));

  const submit = async () => {
    if (!form.name || !form.specialization) { toast('Name and specialization required', 'error'); return; }
    setSaving(true);
    try {
      const payload = { ...form, avgConsultationTime: parseInt(form.avgConsultationTime) };
      if (doctor) await adminApi.updateDoctor(doctor.id, payload);
      else        await adminApi.createDoctor(payload as any);
      toast(doctor ? 'Doctor updated' : 'Doctor created with auto-queue', 'success');
      onSave(); onClose();
    } catch (e: any) { toast(e.response?.data?.message ?? 'Save failed', 'error'); }
    finally { setSaving(false); }
  };

  return (
    <div className="fixed inset-0 bg-black/40 flex items-center justify-center z-50 p-4">
      <div className="bg-white rounded-2xl shadow-xl w-full max-w-md">
        <div className="p-5 border-b border-gray-100 flex justify-between items-center">
          <h2 className="font-semibold text-gray-800">{doctor ? 'Edit Doctor' : 'Add Doctor'}</h2>
          <button onClick={onClose} className="text-gray-400 hover:text-gray-600 text-xl">×</button>
        </div>
        <div className="p-5 flex flex-col gap-3">
          <Input label="Full name" value={form.name} onChange={set('name')} placeholder="Dr. First Last"/>
          <Input label="Specialization" value={form.specialization} onChange={set('specialization')} placeholder="Cardiology…"/>
          <div className="grid grid-cols-2 gap-3">
            <Input label="Avg consult (min)" value={form.avgConsultationTime} onChange={set('avgConsultationTime')}/>
            <Input label="Room number" value={form.roomNumber} onChange={set('roomNumber')} placeholder="OPD-3"/>
          </div>
          {!doctor && (
            <div className="bg-teal-50 border border-teal-100 rounded-lg px-3 py-2 text-xs text-teal-700">
              ✅ A queue will be auto-created for this doctor.
            </div>
          )}
        </div>
        <div className="p-5 border-t border-gray-100 flex gap-2 justify-end">
          <Button variant="outline" onClick={onClose}>Cancel</Button>
          <Button onClick={submit} disabled={saving}>{saving ? <Spinner size={14}/> : (doctor ? 'Save' : 'Create doctor')}</Button>
        </div>
      </div>
    </div>
  );
};

/* ── Create Staff Modal ───────────────────────────────────────── */
const StaffModal = ({ onClose, onSave }: { onClose: () => void; onSave: () => void }) => {
  const toast = useToast();
  const [form, setForm] = useState({ name:'', email:'', password:'doctor@123', role:'DOCTOR', phone:'' });
  const [saving, setSaving] = useState(false);
  const set = (k: string) => (v: string) => setForm(f => ({ ...f, [k]: v }));

  const submit = async () => {
    if (!form.name || !form.email) { toast('Name and email required', 'error'); return; }
    setSaving(true);
    try {
      await api.post('/admin/users/create-staff', form);
      toast(`${form.role} account created. Login: ${form.email}`, 'success');
      onSave(); onClose();
    } catch (e: any) { toast(e.response?.data?.message ?? 'Failed', 'error'); }
    finally { setSaving(false); }
  };

  return (
    <div className="fixed inset-0 bg-black/40 flex items-center justify-center z-50 p-4">
      <div className="bg-white rounded-2xl shadow-xl w-full max-w-md">
        <div className="p-5 border-b border-gray-100 flex justify-between items-center">
          <h2 className="font-semibold text-gray-800">Create Staff Account</h2>
          <button onClick={onClose} className="text-gray-400 hover:text-gray-600 text-xl">×</button>
        </div>
        <div className="p-5 flex flex-col gap-3">
          <Select label="Role" value={form.role} onChange={set('role')}
            options={[{value:'DOCTOR',label:'Doctor'},{value:'ADMIN',label:'Admin'}]}/>
          <Input label="Full name" value={form.name} onChange={set('name')} placeholder="Dr. Full Name"/>
          <Input label="Email" type="email" value={form.email} onChange={set('email')} placeholder="doctor@hospital.com"/>
          <Input label="Password" value={form.password} onChange={set('password')} placeholder="Min 6 chars"/>
          <Input label="Phone (optional)" value={form.phone} onChange={set('phone')} placeholder="+91…"/>
          <div className="bg-amber-50 border border-amber-100 rounded-lg px-3 py-2 text-xs text-amber-700">
            ⚠️ Only admins can create DOCTOR/ADMIN accounts. Share credentials securely.
          </div>
        </div>
        <div className="p-5 border-t border-gray-100 flex gap-2 justify-end">
          <Button variant="outline" onClick={onClose}>Cancel</Button>
          <Button onClick={submit} disabled={saving}>{saving ? <Spinner size={14}/> : `Create ${form.role}`}</Button>
        </div>
      </div>
    </div>
  );
};

/* ── Confirm + Broadcast Modals ───────────────────────────────── */
const ConfirmModal = ({ message, onConfirm, onClose }: {
  message: string; onConfirm: () => void; onClose: () => void;
}) => (
  <div className="fixed inset-0 bg-black/40 flex items-center justify-center z-50 p-4">
    <div className="bg-white rounded-2xl shadow-xl w-full max-w-sm p-6 flex flex-col gap-4">
      <p className="text-sm text-gray-700">{message}</p>
      <div className="flex gap-2 justify-end">
        <Button variant="outline" onClick={onClose}>Cancel</Button>
        <Button variant="danger" onClick={() => { onConfirm(); onClose(); }}>Confirm</Button>
      </div>
    </div>
  </div>
);

const BroadcastModal = ({ queueId, onClose }: { queueId: number; onClose: () => void }) => {
  const toast = useToast();
  const [msg, setMsg] = useState('');
  const send = async () => {
    if (!msg.trim()) return;
    try { await adminApi.broadcast(queueId, msg); toast('Broadcast sent', 'success'); onClose(); }
    catch { toast('Failed', 'error'); }
  };
  return (
    <div className="fixed inset-0 bg-black/40 flex items-center justify-center z-50 p-4">
      <div className="bg-white rounded-2xl shadow-xl w-full max-w-sm p-6 flex flex-col gap-4">
        <h2 className="font-semibold">Broadcast to Queue</h2>
        <textarea value={msg} onChange={e => setMsg(e.target.value)}
          className="px-3 py-2 text-sm border border-gray-200 rounded-lg resize-none h-24 focus:outline-none focus:ring-2 focus:ring-teal-500"
          placeholder="Message to all waiting patients…"/>
        <div className="flex gap-2 justify-end">
          <Button variant="outline" onClick={onClose}>Cancel</Button>
          <Button onClick={send}>Send</Button>
        </div>
      </div>
    </div>
  );
};

/* ── MAIN ADMIN PAGE ──────────────────────────────────────────── */
export const AdminPage = () => {
  const toast = useToast();
  const [tab, setTab]             = useState<Tab>('queue');
  const [analytics, setAnalytics] = useState<AnalyticsDashboard | null>(null);
  const [queues, setQueues]       = useState<QueueStatus[]>([]);
  const [doctors, setDoctors]     = useState<DoctorResponse[]>([]);
  const [users, setUsers]         = useState<UserResponse[]>([]);
  const [appointments, setAppointments] = useState<any[]>([]);
  const [payStats, setPayStats]   = useState<any>(null);
  const [loading, setLoading]     = useState(true);
  const [callingNext, setCallingNext] = useState<number | null>(null);

  const [doctorModal, setDoctorModal] = useState<DoctorResponse | null | 'new'>(undefined as any);
  const [staffModal, setStaffModal]   = useState(false);
  const [confirmModal, setConfirmModal] = useState<{ msg: string; fn: () => void } | null>(null);
  const [broadcastQ, setBroadcastQ]   = useState<number | null>(null);
  const [delayForm, setDelayForm]     = useState({ doctorId:'', minutes:'0', reason:'' });
  const [apptFilter, setApptFilter]   = useState({ status:'', doctorId:'' });

  const fetchAll = useCallback(async () => {
    try {
      const [a, q, d, u] = await Promise.all([
        adminApi.getDashboard(), adminApi.listQueues(),
        adminApi.listDoctors(),  adminApi.listUsers(),
      ]);
      setAnalytics(a.data.data);
      setQueues(q.data.data ?? []);
      setDoctors(d.data.data ?? []);
      setUsers(u.data.data ?? []);
    } catch (e: any) { toast(e.response?.data?.message ?? 'Load failed', 'error'); }
    finally { setLoading(false); }
  }, []);

  // BUG 7: Fetch payment stats when payments tab selected
  const fetchPayStats = useCallback(async () => {
    try {
      const res = await api.get('/payments/admin/stats');
      setPayStats(res.data.data);
    } catch {}
  }, []);

  const fetchAppointments = useCallback(async () => {
    try {
      const res = await adminApi.listAppointments(
        apptFilter.status || undefined,
        apptFilter.doctorId ? parseInt(apptFilter.doctorId) : undefined
      );
      setAppointments(res.data.data ?? []);
    } catch {}
  }, [apptFilter]);

  useEffect(() => { fetchAll(); }, [fetchAll]);
  useEffect(() => { if (tab === 'appointments') fetchAppointments(); }, [tab, fetchAppointments]);
  useEffect(() => { if (tab === 'payments') fetchPayStats(); }, [tab, fetchPayStats]);

  useSocket({
    onQueueUpdated: fetchAll,
    onEtaUpdated: fetchAll,
    onTokenCalled: (d) => toast(`Token T-${d.tokenNumber} called — ${d.patientName}`, 'success'),
  });

  const callNext = async (queueId: number) => {
    setCallingNext(queueId);
    try {
      const res = await api.put(`/queue/${queueId}/next`);
      toast(`Calling T-${res.data.data.tokenNumber} — ${res.data.data.patientName}`, 'success');
      fetchAll();
    } catch (e: any) { toast(e.response?.data?.message ?? 'Error', 'error'); }
    finally { setCallingNext(null); }
  };

  const updateDelay = async () => {
    if (!delayForm.doctorId) { toast('Select a doctor', 'error'); return; }
    try {
      await adminApi.updateDoctorDelay({ doctorId: parseInt(delayForm.doctorId), delayMinutes: parseInt(delayForm.minutes), reason: delayForm.reason });
      toast('Delay updated. ETAs recalculated.', 'success'); fetchAll();
    } catch (e: any) { toast(e.response?.data?.message ?? 'Failed', 'error'); }
  };

  if (loading) return <div className="flex justify-center py-20"><Spinner size={32}/></div>;

  const TABS: { id: Tab; label: string; icon: string }[] = [
    { id:'queue',        label:'Live Queue',   icon:'🔴' },
    { id:'doctors',      label:'Doctors',      icon:'👨‍⚕️' },
    { id:'users',        label:'Users',        icon:'👥' },
    { id:'queues',       label:'Queues',       icon:'📋' },
    { id:'appointments', label:'Appointments', icon:'📅' },
    { id:'payments',     label:'Payments',     icon:'💳' },
  ];

  return (
    <div className="max-w-7xl mx-auto px-4 sm:px-6 py-6 flex flex-col gap-5">
      <div>
        <h1 className="text-lg font-semibold text-gray-800">Admin Control Panel</h1>
        <p className="text-sm text-gray-400">Full system management</p>
      </div>

      {analytics && (
        <div className="grid grid-cols-2 sm:grid-cols-4 gap-3">
          <StatCard label="Waiting now"      value={analytics.totalWaitingNow}    color="blue"/>
          <StatCard label="Completed today"  value={analytics.totalCompletedToday} color="green"/>
          <StatCard label="Avg wait"         value={`${analytics.avgWaitingTimeToday?.toFixed(1)}m`} color="teal"/>
          <StatCard label="No-show rate"     value={`${((analytics.noShowRateToday??0)*100).toFixed(0)}%`} color="amber"/>
        </div>
      )}

      {/* Tabs */}
      <div className="flex gap-1 bg-gray-100 p-1 rounded-xl overflow-x-auto">
        {TABS.map(t => (
          <button key={t.id} onClick={() => setTab(t.id)}
            className={`flex items-center gap-1.5 px-3 py-2 rounded-lg text-sm font-medium whitespace-nowrap transition-all ${
              tab === t.id ? 'bg-white text-gray-800 shadow-sm' : 'text-gray-500 hover:text-gray-700'
            }`}>
            <span>{t.icon}</span>{t.label}
          </button>
        ))}
      </div>

      {/* ── LIVE QUEUE ── */}
      {tab === 'queue' && (
        <div className="flex flex-col gap-4">
          {queues.length === 0 && <Card><Empty message="No queues. Create one in the Queues tab."/></Card>}
          {queues.map(queue => (
            <Card key={queue.queueId}>
              <div className="flex items-center justify-between mb-3 flex-wrap gap-2">
                <div>
                  <CardTitle>{queue.queueName} — {queue.doctorName}</CardTitle>
                  <div className="flex gap-2 items-center">
                    <Badge label={queue.status}/>
                    <span className="text-xs text-gray-400">{queue.totalWaiting} waiting · Token #{queue.currentToken}</span>
                  </div>
                </div>
                <div className="flex gap-2">
                  <Button size="sm" variant="outline" onClick={() => setBroadcastQ(queue.queueId)}>📢</Button>
                  <Button size="sm" onClick={() => callNext(queue.queueId)} disabled={callingNext===queue.queueId}>
                    {callingNext===queue.queueId ? <Spinner size={12}/> : '▶ Next'}
                  </Button>
                </div>
              </div>
              {queue.appointments.length === 0 ? <Empty message="No active appointments."/> : (
                <div className="overflow-x-auto">
                  <table className="w-full text-sm">
                    <thead><tr className="text-xs text-gray-400 border-b border-gray-100">
                      <th className="text-left pb-2 pr-3">Token</th>
                      <th className="text-left pb-2 pr-3">Patient</th>
                      <th className="text-left pb-2 pr-3">Priority</th>
                      <th className="text-left pb-2 pr-3">ETA</th>
                      <th className="text-left pb-2 pr-3">Status</th>
                      <th className="text-left pb-2">Actions</th>
                    </tr></thead>
                    <tbody className="divide-y divide-gray-50">
                      {queue.appointments.map(appt => (
                        <tr key={appt.id} className="hover:bg-gray-50">
                          <td className="py-2 pr-3">
                            <span className={`inline-flex w-9 h-9 rounded-lg items-center justify-center text-white text-xs font-bold ${PRI_BG[appt.priority]??'bg-teal-500'}`}>
                              T-{String(appt.tokenNumber).padStart(2,'0')}
                            </span>
                          </td>
                          <td className="py-2 pr-3 font-medium text-gray-700">{appt.patientName}</td>
                          <td className="py-2 pr-3">
                            <Select value={appt.priority} onChange={async v => {
                              try { await adminApi.updatePriority(appt.id, v); toast('Priority updated','success'); fetchAll(); }
                              catch { toast('Failed','error'); }
                            }} options={PRIORITY_OPTS}/>
                          </td>
                          <td className="py-2 pr-3">
                            <p className="font-semibold text-teal-600 text-xs">
                              {appt.predictedVisitTime ? new Date(appt.predictedVisitTime).toLocaleTimeString('en-IN',{hour:'2-digit',minute:'2-digit'}) : '—'}
                            </p>
                            <p className="text-xs text-gray-400">±{appt.predictionConfidence??'?'}m</p>
                          </td>
                          <td className="py-2 pr-3"><Badge label={appt.status}/></td>
                          <td className="py-2">
                            <div className="flex gap-1.5 flex-wrap">
                              {appt.status==='WAITING'&&<Button size="sm" variant="outline" onClick={async()=>{await adminApi.completeAppointment(appt.id);fetchAll();}}>✓</Button>}
                              {(appt.status==='WAITING'||appt.status==='ACTIVE')&&<Button size="sm" variant="danger" onClick={()=>setConfirmModal({msg:`Cancel ${appt.patientName}?`,fn:async()=>{await adminApi.cancelAppointment(appt.id);fetchAll();}})}>✕</Button>}
                              {appt.status==='WAITING'&&<Button size="sm" variant="ghost" onClick={()=>setConfirmModal({msg:`Mark ${appt.patientName} no-show?`,fn:async()=>{await adminApi.markNoShow(appt.id);fetchAll();}})}>NS</Button>}
                            </div>
                          </td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
              )}
            </Card>
          ))}
          {/* Delay panel */}
          <Card>
            <CardTitle>⏰ Update doctor delay</CardTitle>
            <div className="grid grid-cols-1 sm:grid-cols-3 gap-3">
              <Select label="Doctor" value={delayForm.doctorId} onChange={v=>setDelayForm(f=>({...f,doctorId:v}))}
                options={[{value:'',label:'Select doctor…'},...doctors.map(d=>({value:String(d.id),label:d.name}))]}/>
              <Select label="Delay (min)" value={delayForm.minutes} onChange={v=>setDelayForm(f=>({...f,minutes:v}))}
                options={['0','5','10','15','20','30','45','60'].map(m=>({value:m,label:`${m} min`}))}/>
              <Input label="Reason" value={delayForm.reason} onChange={v=>setDelayForm(f=>({...f,reason:v}))} placeholder="Emergency case…"/>
            </div>
            <div className="mt-3"><Button variant="outline" onClick={updateDelay}>⚡ Update &amp; recalculate ETAs</Button></div>
          </Card>
        </div>
      )}

      {/* ── DOCTORS ── */}
      {tab === 'doctors' && (
        <div className="flex flex-col gap-4">
          <div className="flex justify-between items-center flex-wrap gap-2">
            <p className="text-sm text-gray-500">{doctors.length} doctors</p>
            <div className="flex gap-2">
              {/* BUG 1 FIX: Only admin creates staff accounts */}
              <Button variant="outline" onClick={() => setStaffModal(true)}>👤 Create Login Account</Button>
              <Button onClick={() => setDoctorModal('new')}>+ Add Doctor</Button>
            </div>
          </div>
          <div className="grid grid-cols-1 sm:grid-cols-2 xl:grid-cols-3 gap-4">
            {doctors.map(doc => (
              <Card key={doc.id} className="flex flex-col gap-3">
                <div className="flex items-start gap-3">
                  <div className="w-10 h-10 rounded-xl bg-teal-100 flex items-center justify-center text-teal-700 font-bold text-sm flex-shrink-0">
                    {doc.name.replace('Dr. ','').split(' ').map((w:string)=>w[0]).join('').slice(0,2).toUpperCase()}
                  </div>
                  <div className="flex-1 min-w-0">
                    <p className="font-semibold text-gray-800 text-sm truncate">{doc.name}</p>
                    <p className="text-xs text-gray-500">{doc.specialization}</p>
                    <p className="text-xs text-gray-400">{doc.roomNumber}</p>
                  </div>
                  <span className={`w-2.5 h-2.5 rounded-full flex-shrink-0 mt-1 ${STATUS_COL[doc.availabilityStatus]??'bg-gray-400'}`}/>
                </div>
                <div className="grid grid-cols-3 gap-2 text-center">
                  <div className="bg-gray-50 rounded-lg py-1.5"><p className="text-xs text-gray-400">Avg</p><p className="text-sm font-semibold text-gray-700">{doc.avgConsultationTime}m</p></div>
                  <div className="bg-gray-50 rounded-lg py-1.5"><p className="text-xs text-gray-400">Queue</p><p className="text-sm font-semibold text-gray-700">{doc.currentQueueSize}</p></div>
                  <div className="bg-gray-50 rounded-lg py-1.5"><p className="text-xs text-gray-400">Delay</p><p className={`text-sm font-semibold ${doc.delayMinutes>0?'text-amber-600':'text-gray-700'}`}>{doc.delayMinutes}m</p></div>
                </div>
                <Select value={doc.availabilityStatus} onChange={async v=>{try{await adminApi.updateAvailability(doc.id,v);toast('Updated','success');fetchAll();}catch{toast('Failed','error');}}} options={AVAIL_OPTS}/>
                <div className="flex gap-2">
                  <Button size="sm" variant="outline" className="flex-1" onClick={()=>setDoctorModal(doc)}>Edit</Button>
                  <Button size="sm" variant="danger" onClick={()=>setConfirmModal({msg:`Delete ${doc.name}?`,fn:async()=>{try{await adminApi.deleteDoctor(doc.id);toast('Deleted','success');fetchAll();}catch(e:any){toast(e.response?.data?.message??'Failed','error');}}})}>Delete</Button>
                </div>
              </Card>
            ))}
          </div>
        </div>
      )}

      {/* ── USERS ── */}
      {tab === 'users' && (
        <Card>
          <div className="flex justify-between items-center mb-4">
            <CardTitle>All users ({users.length})</CardTitle>
            <Button size="sm" onClick={() => setStaffModal(true)}>+ Create Staff</Button>
          </div>
          <div className="overflow-x-auto">
            <table className="w-full text-sm">
              <thead><tr className="text-xs text-gray-400 border-b border-gray-100">
                <th className="text-left pb-2 pr-4">Name</th>
                <th className="text-left pb-2 pr-4">Email</th>
                <th className="text-left pb-2 pr-4">Role</th>
                <th className="text-left pb-2 pr-4">Phone</th>
                <th className="text-left pb-2">Actions</th>
              </tr></thead>
              <tbody className="divide-y divide-gray-50">
                {users.map(u => (
                  <tr key={u.id} className="hover:bg-gray-50">
                    <td className="py-3 pr-4">
                      <div className="flex items-center gap-2">
                        <div className="w-7 h-7 rounded-full bg-teal-100 flex items-center justify-center text-xs font-bold text-teal-700">
                          {u.name.charAt(0).toUpperCase()}
                        </div>
                        <span className="font-medium text-gray-700">{u.name}</span>
                      </div>
                    </td>
                    <td className="py-3 pr-4 text-gray-500 text-xs">{u.email}</td>
                    <td className="py-3 pr-4">
                      <Select value={u.role} onChange={async role=>{
                        try{await adminApi.updateRole({userId:u.id,role});toast('Role updated','success');fetchAll();}
                        catch(e:any){toast(e.response?.data?.message??'Failed','error');}
                      }} options={ROLE_OPTS}/>
                    </td>
                    <td className="py-3 pr-4 text-gray-400 text-xs">{u.phone??'—'}</td>
                    <td className="py-3">
                      <div className="flex gap-1.5">
                        <Button size="sm" variant="outline" onClick={()=>setConfirmModal({msg:`Reset password for ${u.name}?`,fn:async()=>{await adminApi.resetPassword(u.id,'password123');toast('Reset to password123','success');}})}>Reset pwd</Button>
                        <Button size="sm" variant="danger" onClick={()=>setConfirmModal({msg:`Delete ${u.name}?`,fn:async()=>{try{await adminApi.deleteUser(u.id);toast('Deleted','success');fetchAll();}catch(e:any){toast(e.response?.data?.message??'Failed','error');}}})}>Delete</Button>
                      </div>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </Card>
      )}

      {/* ── QUEUES ── */}
      {tab === 'queues' && (
        <div className="flex flex-col gap-4">
          <p className="text-sm text-gray-500">{queues.length} queues</p>
          {queues.map(q => (
            <Card key={q.queueId}>
              <div className="flex items-center justify-between flex-wrap gap-3">
                <div>
                  <p className="font-semibold text-gray-800">{q.queueName}</p>
                  <p className="text-xs text-gray-400">{q.doctorName} · {q.totalWaiting} waiting · Token #{q.currentToken}</p>
                </div>
                <div className="flex gap-2 flex-wrap">
                  <Badge label={q.status}/>
                  {q.status==='ACTIVE'&&<Button size="sm" variant="outline" onClick={()=>setConfirmModal({msg:`Pause "${q.queueName}"?`,fn:async()=>{await adminApi.updateQueue(q.queueId,{status:'PAUSED'});fetchAll();}})}>⏸ Pause</Button>}
                  {q.status==='PAUSED'&&<Button size="sm" variant="outline" onClick={async()=>{await adminApi.updateQueue(q.queueId,{status:'ACTIVE'});fetchAll();}}>▶ Resume</Button>}
                  <Button size="sm" variant="danger" onClick={()=>setConfirmModal({msg:`Reset "${q.queueName}"? All waiting will be cancelled.`,fn:async()=>{await adminApi.resetQueue(q.queueId);toast('Reset','success');fetchAll();}})}>↺ Reset</Button>
                  <Button size="sm" variant="danger" onClick={()=>setConfirmModal({msg:`Delete "${q.queueName}"?`,fn:async()=>{try{await adminApi.deleteQueue(q.queueId);toast('Deleted','success');fetchAll();}catch(e:any){toast(e.response?.data?.message??'Failed','error');}}})}>Delete</Button>
                </div>
              </div>
            </Card>
          ))}
        </div>
      )}

      {/* ── APPOINTMENTS ── */}
      {tab === 'appointments' && (
        <div className="flex flex-col gap-4">
          <div className="flex gap-3 flex-wrap">
            <Select value={apptFilter.status} onChange={v=>setApptFilter(f=>({...f,status:v}))} options={[{value:'',label:'All statuses'},{value:'WAITING',label:'Waiting'},{value:'ACTIVE',label:'Active'},{value:'COMPLETED',label:'Completed'},{value:'CANCELLED',label:'Cancelled'},{value:'NO_SHOW',label:'No-show'}]}/>
            <Select value={apptFilter.doctorId} onChange={v=>setApptFilter(f=>({...f,doctorId:v}))} options={[{value:'',label:'All doctors'},...doctors.map(d=>({value:String(d.id),label:d.name}))]}/>
            <Button variant="outline" onClick={fetchAppointments}>Refresh</Button>
          </div>
          <Card>
            <div className="overflow-x-auto">
              <table className="w-full text-sm">
                <thead><tr className="text-xs text-gray-400 border-b border-gray-100">
                  <th className="text-left pb-2 pr-3">Token</th>
                  <th className="text-left pb-2 pr-3">Patient</th>
                  <th className="text-left pb-2 pr-3">Doctor</th>
                  <th className="text-left pb-2 pr-3">Priority</th>
                  <th className="text-left pb-2 pr-3">Status</th>
                  <th className="text-left pb-2 pr-3">Booked</th>
                  <th className="text-left pb-2">Actions</th>
                </tr></thead>
                <tbody className="divide-y divide-gray-50">
                  {appointments.length===0&&<tr><td colSpan={7} className="py-8 text-center text-gray-400 text-sm">No appointments found.</td></tr>}
                  {appointments.map((a:any)=>(
                    <tr key={a.id} className="hover:bg-gray-50">
                      <td className="py-2.5 pr-3"><span className="inline-flex w-8 h-8 rounded-lg items-center justify-center text-white text-xs font-bold bg-teal-500">{a.tokenNumber}</span></td>
                      <td className="py-2.5 pr-3 font-medium text-gray-700">{a.user?.name??'—'}</td>
                      <td className="py-2.5 pr-3 text-gray-500 text-xs">{a.doctor?.name??'—'}</td>
                      <td className="py-2.5 pr-3"><Badge label={a.priority}/></td>
                      <td className="py-2.5 pr-3"><Badge label={a.status}/></td>
                      <td className="py-2.5 pr-3 text-xs text-gray-400">{a.createdAt?new Date(a.createdAt).toLocaleString('en-IN',{day:'2-digit',month:'short',hour:'2-digit',minute:'2-digit'}):'—'}</td>
                      <td className="py-2.5">{(a.status==='WAITING'||a.status==='ACTIVE')&&<Button size="sm" variant="danger" onClick={()=>setConfirmModal({msg:`Cancel appt #${a.tokenNumber}?`,fn:async()=>{await adminApi.cancelAppointment(a.id);fetchAppointments();}})}>Cancel</Button>}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </Card>
        </div>
      )}

      {/* ── BUG 7 FIX: PAYMENTS DASHBOARD ── */}
      {tab === 'payments' && (
        <div className="flex flex-col gap-5">
          {payStats ? (
            <>
              <div className="grid grid-cols-2 sm:grid-cols-3 lg:grid-cols-5 gap-3">
                <StatCard label="Today's Revenue"     value={`₹${Number(payStats.todayRevenue).toFixed(0)}`}    color="green"/>
                <StatCard label="Week Revenue"         value={`₹${Number(payStats.weekRevenue).toFixed(0)}`}     color="teal"/>
                <StatCard label="Total Payments"       value={payStats.totalCount}                               color="blue"/>
                <StatCard label="Pending"              value={payStats.pendingCount}                             color="amber"/>
                <StatCard label="Failed"               value={payStats.failedCount}                              color="purple"/>
              </div>

              <div className="grid grid-cols-1 lg:grid-cols-2 gap-5">
                <Card>
                  <CardTitle>Avg transaction value</CardTitle>
                  <p className="text-3xl font-bold text-teal-600 mt-2">
                    ₹{Number(payStats.avgTransactionValue).toFixed(2)}
                  </p>
                  <p className="text-xs text-gray-400 mt-1">Last 7 days · {payStats.weekCount} transactions</p>
                </Card>

                <Card>
                  <CardTitle>Month revenue</CardTitle>
                  <p className="text-3xl font-bold text-green-600 mt-2">
                    ₹{Number(payStats.monthRevenue).toFixed(2)}
                  </p>
                  <p className="text-xs text-gray-400 mt-1">Last 30 days</p>
                </Card>
              </div>

              <Card>
                <CardTitle>Recent payments</CardTitle>
                {(payStats.recentPayments ?? []).length === 0 ? <Empty message="No payments yet." /> : (
                  <div className="overflow-x-auto">
                    <table className="w-full text-sm">
                      <thead><tr className="text-xs text-gray-400 border-b border-gray-100">
                        <th className="text-left pb-2 pr-4">Payment ID</th>
                        <th className="text-left pb-2 pr-4">Appointment</th>
                        <th className="text-right pb-2 pr-4">Amount</th>
                        <th className="text-left pb-2 pr-4">Status</th>
                        <th className="text-left pb-2">Date</th>
                      </tr></thead>
                      <tbody className="divide-y divide-gray-50">
                        {(payStats.recentPayments ?? []).map((p: any) => (
                          <tr key={p.id} className="hover:bg-gray-50">
                            <td className="py-2.5 pr-4 text-xs font-mono text-gray-500">{p.razorpayPaymentId ?? `PAY-${p.id}`}</td>
                            <td className="py-2.5 pr-4 text-gray-600">#{p.appointment?.id ?? '—'}</td>
                            <td className="py-2.5 pr-4 text-right font-semibold text-gray-800">₹{Number(p.amount).toFixed(2)}</td>
                            <td className="py-2.5 pr-4">
                              <span className={`inline-flex px-2 py-0.5 rounded-full text-xs font-semibold ${
                                p.status==='PAID' ? 'bg-green-100 text-green-700' :
                                p.status==='PENDING' ? 'bg-amber-100 text-amber-700' :
                                'bg-red-100 text-red-700'
                              }`}>{p.status}</span>
                            </td>
                            <td className="py-2.5 text-gray-400 text-xs">
                              {p.createdAt ? new Date(p.createdAt).toLocaleDateString('en-IN',{day:'2-digit',month:'short'}) : '—'}
                            </td>
                          </tr>
                        ))}
                      </tbody>
                    </table>
                  </div>
                )}
              </Card>
            </>
          ) : (
            <div className="flex justify-center py-20"><Spinner size={32}/></div>
          )}
        </div>
      )}

      {/* Modals */}
      {doctorModal === 'new' && <DoctorModal doctor={null} onClose={() => setDoctorModal(undefined as any)} onSave={fetchAll}/>}
      {doctorModal && doctorModal !== 'new' && <DoctorModal doctor={doctorModal as DoctorResponse} onClose={() => setDoctorModal(undefined as any)} onSave={fetchAll}/>}
      {staffModal && <StaffModal onClose={() => setStaffModal(false)} onSave={fetchAll}/>}
      {confirmModal && <ConfirmModal message={confirmModal.msg} onConfirm={confirmModal.fn} onClose={() => setConfirmModal(null)}/>}
      {broadcastQ !== null && <BroadcastModal queueId={broadcastQ} onClose={() => setBroadcastQ(null)}/>}
    </div>
  );
};
