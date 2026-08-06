import React,{useEffect,useState,useCallback} from 'react';
import {doctorApi,analyticsApi,adminApi,DoctorResponse,QueueStatus,AppointmentResponse} from '../services/api';
import {Spinner} from '../components/UI';

const PRIORITY_OPTS=[
  {value:'NORMAL',label:'Normal visit',desc:'Regular consultation',icon:'👤'},
  {value:'SENIOR_CITIZEN',label:'Senior Citizen (60+)',desc:'Priority seating',icon:'🧓'},
  {value:'EMERGENCY',label:'Emergency / Urgent',desc:'Immediate attention needed',icon:'🚨'},
];
type Step='welcome'|'form'|'confirm'|'done';

export const KioskPage=()=>{
  const[step,setStep]=useState<Step>('welcome');
  const[doctors,setDoctors]=useState<DoctorResponse[]>([]);
  const[queues,setQueues]=useState<QueueStatus[]>([]);
  const[loading,setLoading]=useState(true);
  const[submitting,setSubmitting]=useState(false);
  const[result,setResult]=useState<AppointmentResponse|null>(null);
  const[error,setError]=useState('');
  const[countdown,setCountdown]=useState(15);
  const[form,setForm]=useState({name:'',phone:'',priority:'NORMAL',doctorId:'',queueId:''});
  const set=(k:string)=>(v:string)=>setForm(f=>({...f,[k]:v}));

  const fetchData=useCallback(async()=>{
    try{
      const[dRes,qRes]=await Promise.all([doctorApi.listAll(),analyticsApi.getAllQueues()]);
      const avail=(dRes.data.data??[]).filter(d=>d.availabilityStatus!=='OFFLINE');
      setDoctors(avail);setQueues(qRes.data.data??[]);
      if(avail.length>0)setForm(f=>({...f,doctorId:String(avail[0].id)}));
    }catch{}finally{setLoading(false);}
  },[]);
  useEffect(()=>{fetchData();},[fetchData]);

  useEffect(()=>{
    if(!form.doctorId)return;
    const doc=doctors.find(d=>d.id===parseInt(form.doctorId));
    const q=queues.find(q=>q.doctorName===doc?.name&&q.status==='ACTIVE');
    setForm(f=>({...f,queueId:q?String(q.queueId):''}));
  },[form.doctorId,doctors,queues]);

  useEffect(()=>{
    if(step!=='done')return;
    setCountdown(15);
    const t=setInterval(()=>setCountdown(c=>{if(c<=1){clearInterval(t);resetKiosk();return 0;}return c-1;}),1000);
    return()=>clearInterval(t);
  },[step]);

  const resetKiosk=()=>{setStep('welcome');setResult(null);setError('');setForm({name:'',phone:'',priority:'NORMAL',doctorId:String(doctors[0]?.id??''),queueId:''});};

  const submit=async()=>{
    if(!form.name.trim()||!form.phone.trim()){setError('Name and phone are required.');return;}
    if(!form.queueId){setError('No active queue for this doctor. Please ask staff.');return;}
    setSubmitting(true);setError('');
    try{
      const res=await adminApi.kioskRegister({name:form.name.trim(),phone:form.phone.trim(),priority:form.priority,doctorId:parseInt(form.doctorId),queueId:parseInt(form.queueId)});
      setResult(res.data.data);setStep('done');
    }catch(e:any){setError(e.response?.data?.message??'Registration failed. Please ask staff.');}
    finally{setSubmitting(false);}
  };

  const selDoc=doctors.find(d=>d.id===parseInt(form.doctorId));
  const selQ=queues.find(q=>q.queueId===parseInt(form.queueId));

  if(loading)return(<div className="min-h-screen bg-teal-600 flex items-center justify-center"><Spinner size={48}/></div>);

  return(
    <div className="min-h-screen bg-gradient-to-br from-teal-600 to-teal-800 flex items-center justify-center p-6">
      <div className="w-full max-w-lg">
        <div className="text-center mb-8">
          <div className="w-20 h-20 rounded-2xl bg-white/20 flex items-center justify-center mx-auto mb-4">
            <svg className="w-10 h-10 text-white" fill="none" stroke="currentColor" strokeWidth={2} viewBox="0 0 24 24"><path strokeLinecap="round" d="M22 12h-4l-3 9L9 3l-3 9H2"/></svg>
          </div>
          <h1 className="text-3xl font-bold text-white">SmartQueue</h1>
          <p className="text-teal-200 mt-1">Patient Self-Registration Kiosk</p>
        </div>

        {step==='welcome'&&(
          <div className="bg-white rounded-2xl p-8 text-center shadow-2xl">
            <p className="text-2xl font-bold text-gray-800 mb-2">Welcome!</p>
            <p className="text-gray-500 mb-8">Touch below to join the queue</p>
            <button onClick={()=>setStep('form')} className="w-full py-5 bg-teal-600 text-white text-xl font-bold rounded-2xl hover:bg-teal-700 active:scale-95 transition-all">
              Start Registration →
            </button>
            <p className="text-xs text-gray-400 mt-4">Walk-in patients only · For prior bookings see the counter</p>
          </div>
        )}

        {step==='form'&&(
          <div className="bg-white rounded-2xl p-8 shadow-2xl flex flex-col gap-5">
            <h2 className="text-xl font-bold text-gray-800">Your details</h2>
            {error&&<div className="bg-red-50 border border-red-200 text-red-700 text-sm px-4 py-3 rounded-xl">{error}</div>}

            <div className="flex flex-col gap-1.5">
              <label className="text-sm font-medium text-gray-600">Full name *</label>
              <input value={form.name} onChange={e=>set('name')(e.target.value)} placeholder="Enter your full name"
                className="w-full px-4 py-3.5 text-lg border-2 border-gray-200 rounded-xl focus:outline-none focus:border-teal-500 transition-colors"/>
            </div>

            <div className="flex flex-col gap-1.5">
              <label className="text-sm font-medium text-gray-600">Mobile number *</label>
              <input value={form.phone} onChange={e=>set('phone')(e.target.value)} placeholder="+91 98765 43210" type="tel"
                className="w-full px-4 py-3.5 text-lg border-2 border-gray-200 rounded-xl focus:outline-none focus:border-teal-500 transition-colors"/>
            </div>

            <div className="flex flex-col gap-1.5">
              <label className="text-sm font-medium text-gray-600">Visit type</label>
              <div className="grid grid-cols-1 gap-2">
                {PRIORITY_OPTS.map(opt=>(
                  <button key={opt.value} onClick={()=>set('priority')(opt.value)}
                    className={`w-full py-3 px-4 rounded-xl border-2 text-left transition-all flex items-center gap-3 ${form.priority===opt.value?'border-teal-500 bg-teal-50':'border-gray-200 hover:border-gray-300'}`}>
                    <span className="text-2xl">{opt.icon}</span>
                    <div><p className={`font-semibold ${form.priority===opt.value?'text-teal-700':'text-gray-700'}`}>{opt.label}</p><p className="text-xs text-gray-500">{opt.desc}</p></div>
                    {form.priority===opt.value&&<svg className="w-5 h-5 text-teal-600 ml-auto" fill="currentColor" viewBox="0 0 20 20"><path fillRule="evenodd" d="M10 18a8 8 0 100-16 8 8 0 000 16zm3.707-9.293a1 1 0 00-1.414-1.414L9 10.586 7.707 9.293a1 1 0 00-1.414 1.414l2 2a1 1 0 001.414 0l4-4z" clipRule="evenodd"/></svg>}
                  </button>
                ))}
              </div>
            </div>

            <div className="flex flex-col gap-1.5">
              <label className="text-sm font-medium text-gray-600">Select doctor</label>
              <div className="flex flex-col gap-2 max-h-48 overflow-y-auto">
                {doctors.map(doc=>(
                  <button key={doc.id} onClick={()=>set('doctorId')(String(doc.id))}
                    className={`w-full py-3 px-4 rounded-xl border-2 text-left transition-all ${String(doc.id)===form.doctorId?'border-teal-500 bg-teal-50':'border-gray-200 hover:border-gray-300'}`}>
                    <p className={`font-semibold ${String(doc.id)===form.doctorId?'text-teal-700':'text-gray-800'}`}>{doc.name}</p>
                    <p className="text-xs text-gray-500">{doc.specialization} · {doc.currentQueueSize} waiting · {doc.roomNumber||'OPD'}</p>
                  </button>
                ))}
              </div>
            </div>

            <div className="flex gap-3 mt-2">
              <button onClick={()=>setStep('welcome')} className="flex-1 py-3 border-2 border-gray-200 text-gray-600 font-medium rounded-xl hover:bg-gray-50 transition-colors">← Back</button>
              <button onClick={()=>{if(form.name&&form.phone)setStep('confirm');else setError('Please fill all fields.');}}
                className="flex-1 py-3 bg-teal-600 text-white font-bold rounded-xl hover:bg-teal-700 active:scale-95 transition-all">Review →</button>
            </div>
          </div>
        )}

        {step==='confirm'&&(
          <div className="bg-white rounded-2xl p-8 shadow-2xl flex flex-col gap-5">
            <h2 className="text-xl font-bold text-gray-800">Confirm registration</h2>
            {error&&<div className="bg-red-50 border border-red-200 text-red-700 text-sm px-4 py-3 rounded-xl">{error}</div>}
            <div className="bg-gray-50 rounded-xl p-5 flex flex-col gap-3">
              {[
                {label:'Name',value:form.name},{label:'Phone',value:form.phone},
                {label:'Visit type',value:PRIORITY_OPTS.find(o=>o.value===form.priority)?.label},
                {label:'Doctor',value:selDoc?.name},{label:'Queue',value:selQ?.queueName??(form.queueId?'—':'⚠️ No active queue')},
                {label:'Waiting now',value:selQ?`${selQ.totalWaiting} patients`:'—'},
              ].map(row=>(
                <div key={row.label} className="flex justify-between text-sm">
                  <span className="text-gray-500">{row.label}</span>
                  <span className="font-semibold text-gray-800">{row.value??'—'}</span>
                </div>
              ))}
            </div>
            <div className="flex gap-3">
              <button onClick={()=>setStep('form')} className="flex-1 py-3 border-2 border-gray-200 text-gray-600 font-medium rounded-xl hover:bg-gray-50">← Edit</button>
              <button onClick={submit} disabled={submitting||!form.queueId}
                className="flex-1 py-3 bg-teal-600 text-white font-bold rounded-xl hover:bg-teal-700 active:scale-95 disabled:opacity-50 transition-all flex items-center justify-center gap-2">
                {submitting?<><Spinner size={16}/>Registering...</>:'Confirm & Join Queue'}
              </button>
            </div>
          </div>
        )}

        {step==='done'&&result&&(
          <div className="bg-white rounded-2xl p-8 shadow-2xl text-center flex flex-col gap-5">
            <div className="w-20 h-20 rounded-full bg-green-100 flex items-center justify-center mx-auto">
              <svg className="w-10 h-10 text-green-600" fill="none" stroke="currentColor" strokeWidth={2.5} viewBox="0 0 24 24"><path strokeLinecap="round" strokeLinejoin="round" d="M5 13l4 4L19 7"/></svg>
            </div>
            <div><p className="text-2xl font-bold text-gray-800">You're in the queue!</p><p className="text-gray-500 mt-1">Please take a seat and wait to be called.</p></div>
            <div className="bg-teal-600 text-white rounded-2xl p-6">
              <p className="text-sm text-teal-200">Your token number</p>
              <p className="text-6xl font-bold mt-1">T-{String(result.tokenNumber).padStart(2,'0')}</p>
              <p className="text-teal-200 text-sm mt-2">{result.doctorName}</p>
            </div>
            <div className="bg-amber-50 rounded-xl p-4 text-sm text-amber-700">
              Listen for <strong>Token T-{String(result.tokenNumber).padStart(2,'00')}</strong> to be announced.
            </div>
            <p className="text-xs text-gray-400">Screen resets in {countdown} seconds</p>
            <button onClick={resetKiosk} className="w-full py-3 border-2 border-gray-200 text-gray-600 font-medium rounded-xl hover:bg-gray-50">
              Register another patient
            </button>
          </div>
        )}
      </div>
    </div>
  );
};
