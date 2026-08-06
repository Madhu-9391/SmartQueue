import React,{useEffect,useState,useCallback} from 'react';
import {auditApi,HistoricalData,PriorityAuditEntry} from '../services/api';
import {Card,CardTitle,StatCard,Badge,Empty,Spinner,Button,Select} from '../components/UI';
import {BarChart,Bar,LineChart,Line,XAxis,YAxis,Tooltip,ResponsiveContainer,CartesianGrid,Legend} from 'recharts';

const RANGE=[{value:'7',label:'Last 7 days'},{value:'14',label:'Last 14 days'},{value:'30',label:'Last 30 days'}];
type Tab='overview'|'doctors'|'audit';

export const HistoricalPage=()=>{
  const[tab,setTab]=useState<Tab>('overview');
  const[days,setDays]=useState('7');
  const[data,setData]=useState<HistoricalData|null>(null);
  const[audit,setAudit]=useState<PriorityAuditEntry[]>([]);
  const[loading,setLoading]=useState(true);

  const fetch=useCallback(async()=>{
    setLoading(true);
    try{
      const[hRes,aRes]=await Promise.all([auditApi.getHistorical(parseInt(days)),auditApi.getPriorityLog(parseInt(days))]);
      setData(hRes.data.data);setAudit(aRes.data.data??[]);
    }catch{}finally{setLoading(false);}
  },[days]);
  useEffect(()=>{fetch();},[fetch]);

  const daily=(data?.dailyStats??[]).map(d=>({date:d.date,Completed:d.completed,'No-shows':d.noShows,Cancelled:d.cancelled,'Avg wait':d.avgWaitMinutes}));
  const weekday=Object.entries(data?.weekdayDistribution??{}).map(([day,count])=>({day:day.slice(0,3),patients:count}));
  const TABS:{id:Tab;label:string}[]=[{id:'overview',label:'Trends'},{id:'doctors',label:'Doctors'},{id:'audit',label:'Audit Log'}];

  return(
    <div className="max-w-7xl mx-auto px-4 sm:px-6 py-6 flex flex-col gap-5">
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-3">
        <div><h1 className="text-lg font-semibold text-gray-800">Historical Analytics</h1><p className="text-sm text-gray-400">Trends, performance and audit trail</p></div>
        <div className="flex gap-2"><Select value={days} onChange={setDays} options={RANGE}/><Button variant="outline" onClick={fetch}>Refresh</Button></div>
      </div>

      {data&&<div className="grid grid-cols-2 sm:grid-cols-4 gap-3">
        <StatCard label="Total appointments" value={data.totalAppointments} color="blue"/>
        <StatCard label="Avg wait" value={`${data.overallAvgWaitMinutes.toFixed(1)}m`} color="teal"/>
        <StatCard label="Doctors active" value={data.doctorPerformance.length} color="green"/>
        <StatCard label="Escalations" value={audit.filter(a=>a.newPriority==='EMERGENCY').length} color="amber"/>
      </div>}

      <div className="flex gap-1 bg-gray-100 p-1 rounded-xl w-fit">
        {TABS.map(t=><button key={t.id} onClick={()=>setTab(t.id)} className={`px-4 py-2 rounded-lg text-sm font-medium transition-all ${tab===t.id?'bg-white text-gray-800 shadow-sm':'text-gray-500 hover:text-gray-700'}`}>{t.label}</button>)}
      </div>

      {loading?<div className="flex justify-center py-20"><Spinner size={32}/></div>:<>

        {tab==='overview'&&<div className="flex flex-col gap-5">
          <Card><CardTitle>Daily appointment trends</CardTitle>
            {daily.length===0?<Empty message="No data for this period."/>:
            <ResponsiveContainer width="100%" height={220}>
              <BarChart data={daily} barSize={12}>
                <CartesianGrid strokeDasharray="3 3" stroke="#f0f0f0"/>
                <XAxis dataKey="date" tick={{fontSize:10,fill:'#9ca3af'}} axisLine={false} tickLine={false}/>
                <YAxis tick={{fontSize:10,fill:'#9ca3af'}} axisLine={false} tickLine={false} allowDecimals={false}/>
                <Tooltip contentStyle={{borderRadius:8,border:'none',fontSize:12}}/>
                <Legend iconSize={8} wrapperStyle={{fontSize:11}}/>
                <Bar dataKey="Completed" fill="#0d9488" radius={[3,3,0,0]}/>
                <Bar dataKey="No-shows" fill="#d97706" radius={[3,3,0,0]}/>
                <Bar dataKey="Cancelled" fill="#dc2626" radius={[3,3,0,0]}/>
              </BarChart>
            </ResponsiveContainer>}
          </Card>
          <div className="grid grid-cols-1 lg:grid-cols-2 gap-5">
            <Card><CardTitle>Average wait time trend (min)</CardTitle>
              {daily.length===0?<Empty message="No data."/>:
              <ResponsiveContainer width="100%" height={180}>
                <LineChart data={daily}>
                  <CartesianGrid strokeDasharray="3 3" stroke="#f0f0f0"/>
                  <XAxis dataKey="date" tick={{fontSize:10,fill:'#9ca3af'}} axisLine={false} tickLine={false}/>
                  <YAxis tick={{fontSize:10,fill:'#9ca3af'}} axisLine={false} tickLine={false}/>
                  <Tooltip contentStyle={{borderRadius:8,border:'none',fontSize:12}}/>
                  <Line type="monotone" dataKey="Avg wait" stroke="#0d9488" strokeWidth={2} dot={{r:3,fill:'#0d9488'}}/>
                </LineChart>
              </ResponsiveContainer>}
            </Card>
            <Card><CardTitle>Busiest days of the week</CardTitle>
              {weekday.length===0?<Empty message="No data."/>:
              <ResponsiveContainer width="100%" height={180}>
                <BarChart data={weekday} barSize={24}>
                  <CartesianGrid strokeDasharray="3 3" stroke="#f0f0f0"/>
                  <XAxis dataKey="day" tick={{fontSize:11,fill:'#9ca3af'}} axisLine={false} tickLine={false}/>
                  <YAxis tick={{fontSize:10,fill:'#9ca3af'}} axisLine={false} tickLine={false} allowDecimals={false}/>
                  <Tooltip contentStyle={{borderRadius:8,border:'none',fontSize:12}}/>
                  <Bar dataKey="patients" fill="#7c3aed" radius={[4,4,0,0]}/>
                </BarChart>
              </ResponsiveContainer>}
            </Card>
          </div>
        </div>}

        {tab==='doctors'&&<Card><CardTitle>Doctor performance ({days} days)</CardTitle>
          {(data?.doctorPerformance??[]).length===0?<Empty message="No data."/>:
          <div className="overflow-x-auto"><table className="w-full text-sm">
            <thead><tr className="text-xs text-gray-400 border-b border-gray-100">
              <th className="text-left pb-3 pr-4 font-medium">Doctor</th>
              <th className="text-right pb-3 pr-4 font-medium">Completed</th>
              <th className="text-right pb-3 pr-4 font-medium">Avg consult</th>
              <th className="text-right pb-3 pr-4 font-medium">No-shows</th>
              <th className="text-left pb-3 font-medium">Efficiency</th>
            </tr></thead>
            <tbody className="divide-y divide-gray-50">
              {(data?.doctorPerformance??[]).sort((a,b)=>b.totalCompleted-a.totalCompleted).map(dp=>{
                const ns=dp.totalCompleted+dp.noShows>0?Math.round(dp.noShows/(dp.totalCompleted+dp.noShows)*100):0;
                const eff=Math.min(100,Math.max(0,100-ns-Math.max(0,(dp.avgConsultationMinutes-15)*2)));
                return(<tr key={dp.doctorName} className="hover:bg-gray-50">
                  <td className="py-3 pr-4 font-medium text-gray-700">{dp.doctorName}</td>
                  <td className="py-3 pr-4 text-right text-green-600 font-semibold">{dp.totalCompleted}</td>
                  <td className="py-3 pr-4 text-right text-gray-600">{dp.avgConsultationMinutes>0?`${dp.avgConsultationMinutes.toFixed(1)}m`:'—'}</td>
                  <td className="py-3 pr-4 text-right text-amber-600">{dp.noShows}</td>
                  <td className="py-3"><div className="flex items-center gap-2">
                    <div className="flex-1 bg-gray-100 rounded-full h-2 overflow-hidden"><div className="h-full rounded-full bg-teal-500" style={{width:`${eff}%`}}/></div>
                    <span className="text-xs text-gray-500 w-8">{eff}%</span>
                  </div></td>
                </tr>);
              })}
            </tbody>
          </table></div>}
        </Card>}

        {tab==='audit'&&<Card>
          <div className="flex items-center justify-between mb-3">
            <CardTitle>Emergency escalation audit log</CardTitle>
            <span className="text-xs bg-red-50 text-red-600 px-2 py-0.5 rounded-full font-medium">{audit.filter(a=>a.newPriority==='EMERGENCY').length} escalations</span>
          </div>
          {audit.length===0?<Empty message="No priority changes in this period."/>:
          <div className="overflow-x-auto"><table className="w-full text-sm">
            <thead><tr className="text-xs text-gray-400 border-b border-gray-100">
              <th className="text-left pb-2 pr-3 font-medium">Time</th>
              <th className="text-left pb-2 pr-3 font-medium">Patient</th>
              <th className="text-left pb-2 pr-3 font-medium">Token</th>
              <th className="text-left pb-2 pr-3 font-medium">Change</th>
              <th className="text-left pb-2 pr-3 font-medium">By</th>
              <th className="text-left pb-2 font-medium">Reason</th>
            </tr></thead>
            <tbody className="divide-y divide-gray-50">
              {audit.map(e=><tr key={e.id} className="hover:bg-gray-50">
                <td className="py-2.5 pr-3 text-xs text-gray-400 whitespace-nowrap">{new Date(e.changedAt).toLocaleString('en-IN',{day:'2-digit',month:'short',hour:'2-digit',minute:'2-digit'})}</td>
                <td className="py-2.5 pr-3 font-medium text-gray-700">{e.patientName}</td>
                <td className="py-2.5 pr-3"><span className="bg-gray-100 text-gray-600 px-2 py-0.5 rounded text-xs font-mono">T-{String(e.tokenNumber).padStart(2,'0')}</span></td>
                <td className="py-2.5 pr-3"><div className="flex items-center gap-1.5"><Badge label={e.previousPriority}/><span className="text-gray-400 text-xs">→</span><Badge label={e.newPriority}/></div></td>
                <td className="py-2.5 pr-3 text-gray-500 text-xs">{e.changedByName}</td>
                <td className="py-2.5 text-gray-500 text-xs">{e.reason??'—'}</td>
              </tr>)}
            </tbody>
          </table></div>}
        </Card>}
      </>}
    </div>
  );
};
