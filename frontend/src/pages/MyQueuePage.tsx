import React,{useEffect,useState,useCallback} from 'react';
import {appointmentApi,analyticsApi,doctorApi,AppointmentResponse,DoctorResponse,QueueStatus} from '../services/api';
import {useAuth} from '../context/AuthContext';
import {useSocket} from '../hooks/useSocket';
import {Card,CardTitle,Badge,Button,Empty,Spinner,AiPredictionCard,Select} from '../components/UI';
import {useToast} from '../components/UI';
import {Link} from 'react-router-dom';

const PBG:Record<string,string>={EMERGENCY:'bg-red-500',VIP:'bg-purple-500',SENIOR_CITIZEN:'bg-amber-500',NORMAL:'bg-teal-500'};
const fmtTime=(iso:string|null)=>iso?new Date(iso).toLocaleTimeString('en-IN',{hour:'2-digit',minute:'2-digit'}):'—';

const CANCEL_REASONS=[
  {value:'Feeling better now',label:'Feeling better now'},
  {value:'Cannot attend today',label:'Cannot attend today'},
  {value:'Found another doctor',label:'Found another doctor'},
  {value:'Emergency elsewhere',label:'Emergency elsewhere'},
  {value:'Other',label:'Other reason'},
];

const CancelModal=({apptId,onClose,onDone}:{apptId:number;onClose:()=>void;onDone:()=>void})=>{
  const toast=useToast();
  const[reason,setReason]=useState('Cannot attend today');
  const[custom,setCustom]=useState('');
  const[loading,setLoading]=useState(false);
  const submit=async()=>{
    const finalReason=reason==='Other'?custom:reason;
    setLoading(true);
    try{await appointmentApi.cancel(apptId,finalReason);toast('Appointment cancelled','success');onDone();}
    catch(e:any){toast(e.response?.data?.message??'Failed','error');}finally{setLoading(false);}
  };
  return(
    <div className="fixed inset-0 bg-black/40 flex items-center justify-center z-50 p-4">
      <div className="bg-white rounded-2xl shadow-xl w-full max-w-sm">
        <div className="p-5 border-b border-gray-100 flex justify-between items-center">
          <h2 className="font-semibold text-gray-800">Cancel appointment</h2>
          <button onClick={onClose} className="text-gray-400 hover:text-gray-600 text-xl leading-none">×</button>
        </div>
        <div className="p-5 flex flex-col gap-3">
          <p className="text-sm text-gray-500">Please let us know why you're cancelling:</p>
          <Select value={reason} onChange={setReason} options={CANCEL_REASONS}/>
          {reason==='Other'&&(
            <textarea value={custom} onChange={e=>setCustom(e.target.value)} placeholder="Describe your reason..."
              className="w-full px-3 py-2 text-sm border border-gray-200 rounded-lg resize-none h-20 focus:outline-none focus:ring-2 focus:ring-teal-500"/>
          )}
        </div>
        <div className="p-5 border-t border-gray-100 flex gap-2 justify-end">
          <Button variant="outline" onClick={onClose}>Keep appointment</Button>
          <Button variant="danger" onClick={submit} disabled={loading}>{loading?<Spinner size={14}/>:'Cancel appointment'}</Button>
        </div>
      </div>
    </div>
  );
};

const RescheduleModal=({appt,onClose,onDone}:{appt:AppointmentResponse;onClose:()=>void;onDone:()=>void})=>{
  const toast=useToast();
  const[doctors,setDoctors]=useState<DoctorResponse[]>([]);
  const[queues,setQueues]=useState<QueueStatus[]>([]);
  const[newDoctorId,setNewDoctorId]=useState('');
  const[newQueueId,setNewQueueId]=useState('');
  const[reason,setReason]=useState('');
  const[loading,setLoading]=useState(false);

  useEffect(()=>{
    Promise.all([doctorApi.listAll(),analyticsApi.getAllQueues()]).then(([dRes,qRes])=>{
      const docs=dRes.data.data??[];setDoctors(docs);setQueues(qRes.data.data??[]);
      if(docs.length>0)setNewDoctorId(String(docs[0].id));
    });
  },[]);

  useEffect(()=>{
    if(!newDoctorId)return;
    const doc=doctors.find(d=>d.id===parseInt(newDoctorId));
    const q=queues.find(q=>q.doctorName===doc?.name&&q.status==='ACTIVE');
    setNewQueueId(q?String(q.queueId):'');
  },[newDoctorId,doctors,queues]);

  const submit=async()=>{
    if(!newQueueId){toast('No active queue for this doctor','error');return;}
    setLoading(true);
    try{await appointmentApi.reschedule(appt.id,{newDoctorId:parseInt(newDoctorId),newQueueId:parseInt(newQueueId),reason});toast('Appointment rescheduled','success');onDone();}
    catch(e:any){toast(e.response?.data?.message??'Reschedule failed','error');}finally{setLoading(false);}
  };

  const rescheduleCount=appt.rescheduleCount??0;

  return(
    <div className="fixed inset-0 bg-black/40 flex items-center justify-center z-50 p-4">
      <div className="bg-white rounded-2xl shadow-xl w-full max-w-sm">
        <div className="p-5 border-b border-gray-100 flex justify-between items-center">
          <h2 className="font-semibold text-gray-800">Reschedule appointment</h2>
          <button onClick={onClose} className="text-gray-400 hover:text-gray-600 text-xl leading-none">×</button>
        </div>
        <div className="p-5 flex flex-col gap-3">
          {rescheduleCount>=2?(
            <div className="bg-amber-50 border border-amber-100 text-amber-700 text-sm px-3 py-2 rounded-lg">
              Maximum reschedule limit (2) reached for this appointment.
            </div>
          ):(
            <>
              <p className="text-xs text-gray-500">Reschedule {rescheduleCount+1}/2 — Original appointment will be cancelled.</p>
              <Select label="New doctor" value={newDoctorId} onChange={setNewDoctorId}
                options={doctors.map(d=>({value:String(d.id),label:`${d.name} (${d.currentQueueSize} waiting)`}))}/>
              {!newQueueId&&newDoctorId&&<p className="text-xs text-amber-600">No active queue for this doctor.</p>}
              <div className="flex flex-col gap-1">
                <label className="text-xs font-medium text-gray-600">Reason (optional)</label>
                <input value={reason} onChange={e=>setReason(e.target.value)} placeholder="Why are you rescheduling?"
                  className="px-3 py-2 text-sm border border-gray-200 rounded-lg focus:outline-none focus:ring-2 focus:ring-teal-500"/>
              </div>
            </>
          )}
        </div>
        <div className="p-5 border-t border-gray-100 flex gap-2 justify-end">
          <Button variant="outline" onClick={onClose}>Cancel</Button>
          {rescheduleCount<2&&<Button onClick={submit} disabled={loading||!newQueueId}>{loading?<Spinner size={14}/>:'Reschedule'}</Button>}
        </div>
      </div>
    </div>
  );
};

export const MyQueuePage=()=>{
  const{user}=useAuth();
  const toast=useToast();
  const[appointments,setAppointments]=useState<AppointmentResponse[]>([]);
  const[myQueues,setMyQueues]=useState<QueueStatus[]>([]);
  const[loading,setLoading]=useState(true);
  const[cancelModal,setCancelModal]=useState<number|null>(null);
  const[rescheduleModal,setRescheduleModal]=useState<AppointmentResponse|null>(null);

  const fetchAll=useCallback(async()=>{
    try{
      const[aRes,qRes]=await Promise.all([appointmentApi.getMyAppointments(),analyticsApi.getAllQueues()]);
      setAppointments(aRes.data.data??[]);setMyQueues(qRes.data.data??[]);
    }catch{toast('Could not load appointments','error');}finally{setLoading(false);}
  },[]);
  useEffect(()=>{fetchAll();},[fetchAll]);

  useSocket({
    userId:user?.userId,
    onQueueUpdated:fetchAll,
    onEtaUpdated:fetchAll,
    onTokenCalled:(d)=>toast(`🔔 Token T-${d.tokenNumber} is now being called!`,'info'),
    onDoctorDelayed:(d)=>toast(`⏰ Your doctor is delayed by ${d.delayMinutes} min`,'info'),
    onNotification:(d)=>toast(d.message,'info'),
  });

  if(loading)return<div className="flex justify-center py-20"><Spinner size={32}/></div>;

  const active=appointments.filter(a=>a.status==='ACTIVE');
  const waiting=appointments.filter(a=>a.status==='WAITING');
  const past=appointments.filter(a=>['COMPLETED','CANCELLED','NO_SHOW'].includes(a.status));

  return(
    <div className="max-w-3xl mx-auto px-4 sm:px-6 py-6 flex flex-col gap-5">
      <div>
        <h1 className="text-lg font-semibold text-gray-800">My Queue</h1>
        <p className="text-sm text-gray-400 mt-0.5">Live AI-predicted times · Updates via WebSocket</p>
      </div>

      {active.map(appt=>(
        <div key={appt.id}>
          <p className="text-xs font-semibold text-green-600 uppercase tracking-wide mb-2">🟢 Currently being called</p>
          <AiPredictionCard predictedTime={fmtTime(appt.predictedVisitTime)} confidence={appt.predictionConfidence??5} waitMinutes={0} token={appt.tokenNumber}/>
          <div className="mt-2 bg-green-50 border border-green-100 rounded-xl px-4 py-3 text-sm text-green-700 font-medium">
            Please proceed to {appt.doctorName}'s room immediately.
          </div>
        </div>
      ))}

      {waiting.length>0&&(
        <Card>
          <CardTitle>
            <svg className="w-4 h-4 text-blue-500" fill="none" stroke="currentColor" strokeWidth={2} viewBox="0 0 24 24"><circle cx="12" cy="12" r="10"/><polyline points="12 6 12 12 16 14"/></svg>
            Waiting ({waiting.length})
          </CardTitle>
          <div className="flex flex-col gap-2.5">
            {waiting.map(appt=>(
              <div key={appt.id} className="flex items-center gap-3 p-3 bg-gray-50 rounded-xl">
                <div className={`w-11 h-11 rounded-xl flex items-center justify-center text-white text-sm font-bold flex-shrink-0 ${PBG[appt.priority]??'bg-teal-500'}`}>
                  T-{String(appt.tokenNumber).padStart(2,'0')}
                </div>
                <div className="flex-1 min-w-0">
                  <p className="text-sm font-semibold text-gray-700">{appt.doctorName}</p>
                  <p className="text-xs text-gray-400">{appt.doctorSpecialization}</p>
                  <div className="flex items-center gap-1.5 mt-1 flex-wrap"><Badge label={appt.priority}/></div>
                </div>
                <div className="text-right flex-shrink-0 mx-2">
                  <p className="text-base font-bold text-teal-600">{fmtTime(appt.predictedVisitTime)}</p>
                  <p className="text-xs text-gray-400">±{appt.predictionConfidence??'?'} min</p>
                  {appt.estimatedWaitMinutes!=null&&<p className="text-xs text-gray-400">~{appt.estimatedWaitMinutes}m wait</p>}
                </div>
                <div className="flex flex-col gap-1.5">
                  <Button variant="outline" size="sm" onClick={()=>setRescheduleModal(appt)}>Reschedule</Button>
                  <Button variant="danger" size="sm" onClick={()=>setCancelModal(appt.id)}>Cancel</Button>
                </div>
              </div>
            ))}
          </div>
        </Card>
      )}

      {appointments.length===0&&(
        <Card><Empty message="No appointments yet."/>
          <div className="text-center mt-4"><Link to="/book"><Button>Book an appointment</Button></Link></div>
        </Card>
      )}
      {active.length===0&&waiting.length===0&&appointments.length>0&&(
        <Card><div className="text-center py-4"><p className="text-sm text-gray-500 mb-3">No active appointments right now.</p><Link to="/book"><Button variant="outline">Book another</Button></Link></div></Card>
      )}

      {past.length>0&&(
        <Card>
          <CardTitle>History</CardTitle>
          <div className="flex flex-col gap-1.5">
            {past.map(appt=>(
              <div key={appt.id} className="flex items-center gap-3 p-2.5 rounded-lg hover:bg-gray-50">
                <div className="w-8 h-8 rounded-lg bg-gray-100 flex items-center justify-center text-gray-500 text-xs font-bold flex-shrink-0">
                  T-{String(appt.tokenNumber).padStart(2,'0')}
                </div>
                <div className="flex-1 min-w-0">
                  <p className="text-sm text-gray-600 truncate">{appt.doctorName}</p>
                  {appt.cancellationReason&&<p className="text-xs text-gray-400">Reason: {appt.cancellationReason}</p>}
                </div>
                <div className="flex items-center gap-2"><Badge label={appt.status}/><p className="text-xs text-gray-400">{fmtTime(appt.predictedVisitTime)}</p></div>
              </div>
            ))}
          </div>
        </Card>
      )}

      {cancelModal!==null&&<CancelModal apptId={cancelModal} onClose={()=>setCancelModal(null)} onDone={()=>{setCancelModal(null);fetchAll();}}/>}
      {rescheduleModal&&<RescheduleModal appt={rescheduleModal} onClose={()=>setRescheduleModal(null)} onDone={()=>{setRescheduleModal(null);fetchAll();}}/>}
    </div>
  );
};
