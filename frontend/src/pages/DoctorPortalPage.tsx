import React,{useEffect,useState,useCallback} from 'react';
import {useAuth} from '../context/AuthContext';
import {doctorPortalApi,doctorApi,DoctorResponse,DoctorStatsResponse,AppointmentResponse} from '../services/api';
import {useSocket} from '../hooks/useSocket';
import {Card,CardTitle,StatCard,Badge,Button,Select,Empty,Spinner} from '../components/UI';
import {useToast} from '../components/UI';
import {BarChart,Bar,XAxis,YAxis,Tooltip,ResponsiveContainer,CartesianGrid} from 'recharts';

const AVAIL_OPTS=[{value:'AVAILABLE',label:'🟢 Available'},{value:'BUSY',label:'🟡 Busy'},{value:'ON_BREAK',label:'🔵 On Break'},{value:'OFFLINE',label:'⚫ Offline'}];
const PBG:Record<string,string>={EMERGENCY:'bg-red-500',VIP:'bg-purple-500',SENIOR_CITIZEN:'bg-amber-500',NORMAL:'bg-teal-500'};

export const DoctorPortalPage=()=>{
  const toast=useToast();
  const {user}=useAuth();
  const[doctors,setDoctors]=useState<DoctorResponse[]>([]);
  const[selectedId,setSelectedId]=useState<number|null>(null);
  const[stats,setStats]=useState<DoctorStatsResponse|null>(null);
  const[queue,setQueue]=useState<AppointmentResponse[]>([]);
  const[loading,setLoading]=useState(true);
  const[actionId,setActionId]=useState<number|null>(null);

  const fetchDoctors=useCallback(async()=>{
    try{
      const res=await doctorApi.listAll();
      const list=res.data.data??[];
      setDoctors(list);
      if(user?.role==='DOCTOR'){
        const me=await doctorApi.getMe();
        setSelectedId(me.data.data.id);
      } else if(list.length>0&&!selectedId){
        setSelectedId(list[0].id);
      }
    }
    catch{toast('Could not load doctors','error');}finally{setLoading(false);}
  },[user?.role]);

  const fetchData=useCallback(async()=>{
    if(!selectedId)return;
    try{
      const[qRes,sRes]=await Promise.all([doctorPortalApi.getMyQueue(selectedId),doctorPortalApi.getStats(selectedId)]);
      setQueue(qRes.data.data??[]);setStats(sRes.data.data??null);
    }catch{}
  },[selectedId]);

  useEffect(()=>{fetchDoctors();},[fetchDoctors]);
  useEffect(()=>{fetchData();},[fetchData]);

  useSocket({onQueueUpdated:fetchData,onEtaUpdated:fetchData,onTokenCalled:(d)=>toast(`Token T-${d.tokenNumber} called`,'info')});

  const callNext=async()=>{
    if(!selectedId)return;setActionId(-1);
    try{const res=await doctorPortalApi.callNext(selectedId);toast(`Called T-${String(res.data.data?.tokenNumber??'').padStart(2,'0')}`,'success');fetchData();}
    catch(e:any){toast(e.response?.data?.message??'No waiting patient','error');}finally{setActionId(null);}
  };

  const markDone=async(apptId:number)=>{
    if(!selectedId)return;setActionId(apptId);
    try{await doctorPortalApi.markDone(selectedId,apptId);toast('Marked completed','success');fetchData();}
    catch(e:any){toast(e.response?.data?.message??'Failed','error');}finally{setActionId(null);}
  };
  const markNoShow=async(apptId:number)=>{
    if(!selectedId)return;setActionId(apptId);
    try{await doctorPortalApi.markNoShow(selectedId,apptId);toast('Marked no-show','success');fetchData();}
    catch(e:any){toast(e.response?.data?.message??'Failed','error');}finally{setActionId(null);}
  };
  const updateAvail=async(status:string)=>{
    if(!selectedId)return;
    try{await doctorPortalApi.updateAvailability(selectedId,status);toast('Availability updated','success');fetchDoctors();}
    catch{toast('Failed','error');}
  };

  const hourlyData=(stats?.hourlyThroughput??[]).map(h=>({hour:`${String(h.hour).padStart(2,'0')}h`,patients:h.count}));
  const selDoc=doctors.find(d=>d.id===selectedId);

  if(loading)return<div className="flex justify-center py-20"><Spinner size={32}/></div>;

  return(
    <div className="max-w-7xl mx-auto px-4 sm:px-6 py-6 flex flex-col gap-5">
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-3">
        <div><h1 className="text-lg font-semibold text-gray-800">Doctor Portal</h1><p className="text-sm text-gray-400">Manage your queue and track today's performance</p></div>
        <div className="flex gap-2 flex-wrap">
          {user?.role==='ADMIN'&&doctors.length>1&&<Select value={String(selectedId??'')} onChange={v=>setSelectedId(parseInt(v))} options={doctors.map(d=>({value:String(d.id),label:d.name}))}/>}
          <Select value={selDoc?.availabilityStatus??'AVAILABLE'} onChange={updateAvail} options={AVAIL_OPTS}/>
          <Button size="sm" onClick={callNext} disabled={actionId!==null}>▶ Call next</Button>
        </div>
      </div>

      {stats&&<div className="grid grid-cols-2 sm:grid-cols-5 gap-3">
        <StatCard label="Completed" value={stats.completedToday} color="green" sub="today"/>
        <StatCard label="Waiting" value={stats.waitingNow} color="blue" sub="right now"/>
        <StatCard label="Avg consult" value={`${stats.avgConsultationMinutesToday.toFixed(1)}m`} color="teal" sub="today"/>
        <StatCard label="No-shows" value={stats.noShowsToday} color="amber" sub="today"/>
        <StatCard label="Emergencies" value={stats.emergenciesToday} color="purple" sub="today"/>
      </div>}

      <div className="grid grid-cols-1 lg:grid-cols-3 gap-5">
        <div className="lg:col-span-2">
          <Card>
            <CardTitle>
              <span className="w-2 h-2 rounded-full bg-green-500 animate-pulse inline-block mr-1"/>
              Live queue — {selDoc?.name}
              <span className="ml-auto text-xs font-normal text-gray-400">{queue.length} patients</span>
            </CardTitle>
            {queue.length===0?<Empty message="No patients in queue right now."/>:(
              <div className="flex flex-col gap-2">
                {queue.map((appt,idx)=>(
                  <div key={appt.id} className={`flex items-center gap-3 p-3 rounded-xl border ${appt.status==='ACTIVE'?'border-green-200 bg-green-50':'border-gray-100 bg-gray-50'}`}>
                    <div className={`w-11 h-11 rounded-xl flex items-center justify-center text-white text-sm font-bold flex-shrink-0 ${PBG[appt.priority]??'bg-teal-500'}`}>
                      T-{String(appt.tokenNumber).padStart(2,'0')}
                    </div>
                    <div className="flex-1 min-w-0">
                      <div className="flex items-center gap-2 flex-wrap">
                        {idx===0&&appt.status==='ACTIVE'&&<span className="text-xs bg-green-100 text-green-700 px-1.5 py-0.5 rounded font-medium">NOW</span>}
                        {idx===0&&appt.status==='WAITING'&&<span className="text-xs bg-blue-100 text-blue-700 px-1.5 py-0.5 rounded font-medium">NEXT</span>}
                        <p className="text-sm font-semibold text-gray-700 truncate">{appt.patientName}</p>
                      </div>
                      <div className="flex gap-1 mt-0.5"><Badge label={appt.priority}/></div>
                    </div>
                    <div className="text-right flex-shrink-0 mr-2">
                      <p className="text-sm font-bold text-teal-600">
                        {appt.predictedVisitTime?new Date(appt.predictedVisitTime).toLocaleTimeString('en-IN',{hour:'2-digit',minute:'2-digit'}):'—'}
                      </p>
                      <p className="text-xs text-gray-400">±{appt.predictionConfidence??'?'}m</p>
                    </div>
                    <div className="flex flex-col gap-1.5">
                      {appt.status==='ACTIVE'&&
                        <Button size="sm" onClick={()=>markDone(appt.id)} disabled={actionId===appt.id}>
                          {actionId===appt.id?<Spinner size={12}/>:'✓ Done'}
                        </Button>
                      }
                      {appt.status==='WAITING'&&
                        <Button size="sm" onClick={callNext} disabled={actionId!==null}>
                          {actionId===-1?<Spinner size={12}/>:`Call${idx===0?' now':''}`}
                        </Button>
                      }
                      {(appt.status==='WAITING'||appt.status==='ACTIVE')&&
                        <Button size="sm" variant="ghost" onClick={()=>markNoShow(appt.id)} disabled={actionId===appt.id}>Absent</Button>
                      }
                    </div>
                  </div>
                ))}
              </div>
            )}
          </Card>
        </div>

        <Card>
          <CardTitle>Today's throughput</CardTitle>
          {hourlyData.length===0?<Empty message="No completions yet today."/>:(
            <ResponsiveContainer width="100%" height={200}>
              <BarChart data={hourlyData} barSize={14}>
                <CartesianGrid strokeDasharray="3 3" stroke="#f0f0f0"/>
                <XAxis dataKey="hour" tick={{fontSize:10,fill:'#9ca3af'}} axisLine={false} tickLine={false}/>
                <YAxis tick={{fontSize:10,fill:'#9ca3af'}} axisLine={false} tickLine={false} allowDecimals={false}/>
                <Tooltip contentStyle={{borderRadius:8,border:'none',fontSize:12}}/>
                <Bar dataKey="patients" fill="#0d9488" radius={[4,4,0,0]} name="Patients"/>
              </BarChart>
            </ResponsiveContainer>
          )}
          {selDoc&&<div className="mt-4 flex flex-col gap-2">
            {[{label:'Avg consult time',value:`${selDoc.avgConsultationTime} min`},{label:'Room',value:selDoc.roomNumber??'—'},{label:'Delay today',value:selDoc.delayMinutes>0?`+${selDoc.delayMinutes} min`:'None',color:selDoc.delayMinutes>0?'text-amber-600':'text-gray-700'}].map(r=>(
              <div key={r.label} className="flex justify-between text-xs">
                <span className="text-gray-500">{r.label}</span>
                <span className={`font-semibold ${(r as any).color??'text-gray-700'}`}>{r.value}</span>
              </div>
            ))}
          </div>}
        </Card>
      </div>
    </div>
  );
};
