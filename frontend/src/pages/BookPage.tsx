import React, { useEffect, useState, useCallback } from 'react';
import { doctorApi, appointmentApi, analyticsApi, DoctorResponse, AppointmentResponse, QueueStatus } from '../services/api';
import { Card, CardTitle, Button, Select, AiPredictionCard, Badge, Spinner, Empty } from '../components/UI';
import { useToast } from '../components/UI';
import { useNavigate } from "react-router-dom";
const PRIORITY_OPTIONS = [
  { value: 'NORMAL',         label: 'Normal' },
  { value: 'SENIOR_CITIZEN', label: 'Senior Citizen (60+)' },
  { value: 'VIP',            label: 'VIP / Special' },
  { value: 'EMERGENCY',      label: 'Emergency' },
];
const AVAILABILITY_COLOR: Record<string, string> = {
  AVAILABLE: 'bg-green-500', BUSY: 'bg-amber-500', ON_BREAK: 'bg-blue-400', OFFLINE: 'bg-gray-400',
};
const SPEC_EMOJI: Record<string, string> = {
  'Cardiologist': '🫀', 'General Physician': '👨‍⚕️', 'Neurologist': '🧠',
  'Orthopedist': '🦴', 'Dermatologist': '🔬', 'Pediatrician': '👶',
  'Ophthalmologist': '👁️', 'Dentist': '🦷',
};

export const BookPage = () => {
  const navigate=useNavigate();
  const toast = useToast();
  const [doctors, setDoctors]             = useState<DoctorResponse[]>([]);
  const [selectedDoctor, setSelectedDoctor] = useState<DoctorResponse | null>(null);
  const [activeQueue, setActiveQueue]     = useState<QueueStatus | null>(null);
  const [priority, setPriority]           = useState('NORMAL');
  const [loading, setLoading]             = useState(true);
  const [booking, setBooking]             = useState(false);
  const [result, setResult]               = useState<AppointmentResponse | null>(null);

  const loadDoctors = useCallback(async () => {
    try {
      const res = await doctorApi.listAll();
      const list = res.data.data ?? [];
      
      setDoctors(list);
      if (list.length > 0) setSelectedDoctor(list[0]);
    } catch { toast('Could not load doctors', 'error'); }
    finally { setLoading(false); }
  }, []);

  // Load queue info for selected doctor
  const loadQueueForDoctor = useCallback(async (doctor: DoctorResponse) => {
    setActiveQueue(null);
    try {
      // Find queue by checking all queues - use analytics endpoint
      const qRes = await analyticsApi.getAllQueues();
      const queues: QueueStatus[] = qRes.data.data ?? [];
      const match = queues.find(q => q.doctorName === doctor.name && q.status === 'ACTIVE');
      setActiveQueue(match ?? null);
    } catch {}
  }, []);

  useEffect(() => { loadDoctors(); }, [loadDoctors]);
  useEffect(() => { if (selectedDoctor) loadQueueForDoctor(selectedDoctor); }, [selectedDoctor, loadQueueForDoctor]);

  const handleBook = async () => {
    if (!selectedDoctor) { toast('Please select a doctor', 'error'); return; }
    if (!activeQueue)    { toast('No active queue for this doctor', 'error'); return; }
    if (selectedDoctor.availabilityStatus === 'OFFLINE') {
      toast('Doctor is offline. Please select another.', 'error'); return;
    }
    setBooking(true);
    try {
      const res = await appointmentApi.book({
        doctorId: selectedDoctor.id,
        queueId:  activeQueue.queueId,
        priority,
      });
      const appointment = res.data.data;

setResult(appointment);

toast(appointment.paymentRequired ? "Reservation created — complete payment to confirm." : "Appointment booked!", appointment.paymentRequired ? "info" : "success");
if (!appointment.paymentRequired) await loadQueueForDoctor(selectedDoctor);

if (appointment.paymentRequired) {
  navigate("/payment", { state: { appointment } });
} else {
  navigate("/my-queue");
}
    } catch (err: any) {
      toast(err.response?.data?.message ?? 'Booking failed. Try again.', 'error');
    } finally { setBooking(false); }
  };

  if (loading) return <div className="flex justify-center py-20"><Spinner size={32} /></div>;

  return (
    <div className="max-w-3xl mx-auto px-4 sm:px-6 py-6 flex flex-col gap-5">
      <div>
        <h1 className="text-lg font-semibold text-gray-800">Book Appointment</h1>
        <p className="text-sm text-gray-400 mt-0.5">Select a doctor — AI will predict your exact wait time.</p>
      </div>

      {/* Doctor grid — dynamic from backend */}
      <Card>
        <CardTitle>Choose a doctor</CardTitle>
        {doctors.length === 0
          ? <Empty message="No doctors available. Please check back later." />
          : (
          <div className="grid grid-cols-1 sm:grid-cols-2 gap-2.5">
            {doctors.map(doc => (
              <div key={doc.id} onClick={() => doc.availabilityStatus !== 'OFFLINE' && setSelectedDoctor(doc)}
                className={`p-3 rounded-xl border-2 transition-all ${
                  doc.availabilityStatus === 'OFFLINE' ? 'opacity-40 cursor-not-allowed border-gray-100 bg-gray-50'
                  : selectedDoctor?.id === doc.id ? 'border-teal-500 bg-teal-50 cursor-pointer'
                  : 'border-gray-100 hover:border-gray-200 bg-gray-50 cursor-pointer'
                }`}>
                <div className="flex items-start gap-3">
                  <div className="w-10 h-10 rounded-xl bg-white flex items-center justify-center text-xl shadow-sm flex-shrink-0">
                    {SPEC_EMOJI[doc.specialization] ?? '🩺'}
                  </div>
                  <div className="flex-1 min-w-0">
                    <div className="flex items-center gap-1.5">
                      <p className="text-sm font-semibold text-gray-800 truncate">{doc.name}</p>
                      <span className={`w-2 h-2 rounded-full flex-shrink-0 ${AVAILABILITY_COLOR[doc.availabilityStatus] ?? 'bg-gray-400'}`} />
                    </div>
                    <p className="text-xs text-gray-500">{doc.specialization}</p>
                    <div className="flex items-center gap-2 mt-1.5 flex-wrap">
                      <span className="text-xs text-gray-400">{doc.avgConsultationTime}m avg</span>
                      <span className="text-gray-300">·</span>
                      <span className={`text-xs px-1.5 py-0.5 rounded-full font-medium ${
                        doc.currentQueueSize === 0 ? 'bg-green-100 text-green-700'
                        : doc.currentQueueSize < 8 ? 'bg-amber-100 text-amber-700'
                        : 'bg-red-100 text-red-700'
                      }`}>{doc.currentQueueSize} waiting</span>
                      {doc.roomNumber && <><span className="text-gray-300">·</span>
                      <span className="text-xs text-gray-400">{doc.roomNumber}</span></>}
                      {doc.delayMinutes > 0 && (
                        <span className="text-xs text-amber-600 font-medium">+{doc.delayMinutes}m delay</span>
                      )}
                    </div>
                  </div>
                  {selectedDoctor?.id === doc.id && (
                    <svg className="w-4 h-4 text-teal-600 flex-shrink-0" fill="currentColor" viewBox="0 0 20 20">
                      <path fillRule="evenodd" d="M10 18a8 8 0 100-16 8 8 0 000 16zm3.707-9.293a1 1 0 00-1.414-1.414L9 10.586 7.707 9.293a1 1 0 00-1.414 1.414l2 2a1 1 0 001.414 0l4-4z" clipRule="evenodd"/>
                    </svg>
                  )}
                </div>
              </div>
            ))}
          </div>
        )}
      </Card>

      {/* Queue status for selected doctor */}
      {selectedDoctor && (
        <Card>
          <div className="flex items-center justify-between flex-wrap gap-3">
            <div>
              <CardTitle>Queue info — {selectedDoctor.name}</CardTitle>
              {activeQueue ? (
                <div className="flex gap-2 items-center">
                  <Badge label={activeQueue.status} />
                  <span className="text-xs text-gray-400">
                    {activeQueue.totalWaiting} waiting · Current token #{activeQueue.currentToken}
                  </span>
                </div>
              ) : (
                <p className="text-xs text-amber-600">No active queue for this doctor yet.</p>
              )}
            </div>
          </div>
        </Card>
      )}

      {/* Booking form */}
      <Card>
        <CardTitle>Appointment details</CardTitle>
        <div className="flex flex-col sm:flex-row gap-3">
          <div className="flex-1">
            <Select label="Patient type / Priority" value={priority} onChange={setPriority} options={PRIORITY_OPTIONS} />
          </div>
          <div className="flex items-end">
            <Button onClick={handleBook} disabled={booking || !activeQueue || !selectedDoctor} className="w-full sm:w-auto">
              {booking ? <><Spinner size={14} /> Predicting...</> : '🤖 Book with AI Prediction'}
            </Button>
          </div>
        </div>
        {priority === 'EMERGENCY' && (
          <div className="mt-3 bg-red-50 border border-red-100 rounded-lg px-3 py-2 text-xs text-red-700">
            ⚠️ Emergency patients are placed at the front of the queue immediately.
          </div>
        )}
        {!activeQueue && selectedDoctor && (
          <div className="mt-3 bg-amber-50 border border-amber-100 rounded-lg px-3 py-2 text-xs text-amber-700">
            ℹ️ This doctor has no active queue. Contact the admin to create one.
          </div>
        )}
      </Card>

      {/* AI Prediction result */}
      {result && (
        <div className="flex flex-col gap-3">
          <AiPredictionCard
            predictedTime={result.predictedVisitTime
              ? new Date(result.predictedVisitTime).toLocaleTimeString('en-IN',{hour:'2-digit',minute:'2-digit'})
              : '—'}
            confidence={result.predictionConfidence ?? 7}
            waitMinutes={result.estimatedWaitMinutes ?? 0}
            token={result.tokenNumber}
          />
          <Card>
            <CardTitle>Booking confirmation</CardTitle>
            <div className="grid grid-cols-2 sm:grid-cols-4 gap-3">
              {[
                { label: 'Doctor',        value: result.doctorName },
                { label: 'Specialization', value: result.doctorSpecialization },
                { label: 'Priority',      value: <Badge label={result.priority} /> },
                { label: 'Status',        value: <Badge label={result.status} /> },
              ].map(item => (
                <div key={item.label} className="bg-gray-50 rounded-lg p-2.5">
                  <p className="text-xs text-gray-400">{item.label}</p>
                  <div className="mt-0.5 text-sm font-medium text-gray-700">{item.value}</div>
                </div>
              ))}
            </div>
          </Card>
        </div>
      )}
    </div>
  );
};
