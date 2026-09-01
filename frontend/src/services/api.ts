import axios, { AxiosError, InternalAxiosRequestConfig } from 'axios';

declare global {
  interface ImportMetaEnv {
    readonly VITE_API_BASE_URL: string;
  }
  interface ImportMeta {
    readonly env: ImportMetaEnv;
  }
}

const api = axios.create({
    baseURL: import.meta.env.VITE_API_BASE_URL,
    timeout: 8000,
    headers: {
        "Content-Type": "application/json",
    },
});
api.interceptors.request.use((config: InternalAxiosRequestConfig) => {
  const token = localStorage.getItem('token');
  if (token) config.headers.Authorization = `Bearer ${token}`;
  return config;
});
api.interceptors.response.use(
  response => response,
  (error: AxiosError) => {
    if (error.response?.status === 401) {
      localStorage.removeItem('token');
      localStorage.removeItem('user');
      if (window.location.pathname !== '/login') window.location.href = '/login';
    }
    return Promise.reject(error);
  }
);

// ─── Types ────────────────────────────────────────────────────
export interface AuthResponse { token:string;userId:number;name:string;email:string;role:string; }
export interface AppointmentResponse {
  id:number; patientName:string; doctorName:string; doctorSpecialization:string;
  tokenNumber:number; status:string; priority:string;
  predictedVisitTime:string|null; predictionConfidence:number; estimatedWaitMinutes:number;
  lastPredictionUpdated:string; createdAt:string;
  rescheduleCount:number; cancellationReason:string|null;
  paymentStatus:string|null; paymentRequired:boolean|null;
  appointmentDate:string|null;
}
export interface QueueStatus { queueId:number;queueName:string;doctorName:string;currentToken:number;totalWaiting:number;status:string;appointments:AppointmentResponse[]; }
export interface DoctorResponse { id:number;name:string;specialization:string;avgConsultationTime:number;availabilityStatus:string;roomNumber:string;delayMinutes:number;currentQueueSize:number; }
export interface UserResponse { id:number;name:string;email:string;role:string;phone:string;createdAt:string; }
export interface HourlyThroughput { hour:number;count:number; }
export interface AnalyticsDashboard { avgWaitingTimeToday:number;noShowRateToday:number;busiestDoctor:string;totalCompletedToday:number;totalWaitingNow:number;totalNoShowsToday:number;totalBookingsToday:number;hourlyThroughput:HourlyThroughput[];doctorLoads:{doctorName:string;waitingCount:number;avgWaitMinutes:number}[];priorityBreakdown:Record<string,number>; }
export interface DoctorStatsResponse { doctorId:number;doctorName:string;completedToday:number;waitingNow:number;avgConsultationMinutesToday:number;noShowsToday:number;emergenciesToday:number;hourlyThroughput:HourlyThroughput[]; }
export interface PriorityAuditEntry { id:number;appointmentId:number;tokenNumber:number;patientName:string;changedByName:string;previousPriority:string;newPriority:string;reason:string;changedAt:string; }
export interface PaymentResponse { id:number;appointmentId:number;amount:number;currency:string;status:string;razorpayOrderId:string;razorpayPaymentId:string;paidAt:string; }
export interface HistoricalData {
  dailyStats:{date:string;completed:number;noShows:number;cancelled:number;avgWaitMinutes:number}[];
  doctorPerformance:{doctorName:string;totalCompleted:number;avgConsultationMinutes:number;noShows:number}[];
  weekdayDistribution:Record<string,number>;
  overallAvgWaitMinutes:number;
  totalAppointments:number;
}

// ─── Auth ─────────────────────────────────────────────────────
export const authApi = {
  register:(d:{name:string;email:string;password:string;phone?:string;role?:string})=>api.post<{data:AuthResponse}>('/auth/register',d),
  login:(d:{email:string;password:string})=>api.post<{data:AuthResponse}>('/auth/login',d),
};

// ─── Doctors ──────────────────────────────────────────────────
export const doctorApi = {
  listAll:()=>api.get<{data:DoctorResponse[]}>('/doctors'),
  getById:(id:number)=>api.get<{data:DoctorResponse}>(`/doctors/${id}`),
  getMe:()=>api.get<{data:DoctorResponse}>('/doctors/me'),
};

// ─── Appointments ─────────────────────────────────────────────
export const appointmentApi = {
  book:(d:{doctorId:number;queueId:number;priority?:string})=>api.post<{data:AppointmentResponse}>('/appointments/book',d),
  getMyAppointments:()=>api.get<{data:AppointmentResponse[]}>('/appointments/my'),
  cancel:(id:number,reason?:string)=>api.delete(`/appointments/${id}`,{data:{reason}}),
  reschedule:(id:number,d:{newDoctorId:number;newQueueId:number;priority?:string;reason?:string})=>api.post<{data:AppointmentResponse}>(`/appointments/${id}/reschedule`,d),
};

// ─── Queue ────────────────────────────────────────────────────
export const queueApi = {
  getStatus:(queueId:number)=>api.get<{data:QueueStatus}>(`/queue/status/${queueId}`),
  callNextToken:(queueId:number)=>api.put<{data:AppointmentResponse}>(`/queue/${queueId}/next`),
  createQueue:(d:{queueName:string;doctorId:number;maxCapacity?:number})=>api.post<{data:any}>('/queue/create',d),
};

// ─── Analytics ────────────────────────────────────────────────
export const analyticsApi = {
  getDashboard:()=>api.get<{data:AnalyticsDashboard}>('/analytics/dashboard'),
  getAllQueues:()=>api.get<{data:QueueStatus[]}>('/analytics/queues'),
};

// ─── Payment ──────────────────────────────────────────────────
export const paymentApi = {
  createOrder:(appointmentId:number)=>api.post<{data:PaymentResponse}>('/payments/create-order',{appointmentId}),
  verify:(d:{razorpayOrderId:string;razorpayPaymentId:string;razorpaySignature:string;appointmentId:number})=>api.post<{data:PaymentResponse}>('/payments/verify',d),
  getByAppointment:(id:number)=>api.get<{data:PaymentResponse}>(`/payments/appointment/${id}`),
};

// ─── Doctor Portal ────────────────────────────────────────────
export const doctorPortalApi = {
  getMyQueue:(doctorId:number)=>api.get<{data:AppointmentResponse[]}>(`/doctor-portal/my-queue/${doctorId}`),
  getStats:(doctorId:number)=>api.get<{data:DoctorStatsResponse}>(`/doctor-portal/stats/${doctorId}`),
  callNext:(doctorId:number)=>api.put<{data:AppointmentResponse}>(`/doctor-portal/${doctorId}/next`),
  markDone:(doctorId:number,apptId:number)=>api.put<{data:AppointmentResponse}>(`/doctor-portal/${doctorId}/appointments/${apptId}/done`),
  markNoShow:(doctorId:number,apptId:number)=>api.put(`/doctor-portal/${doctorId}/appointments/${apptId}/no-show`),
  updateAvailability:(doctorId:number,status:string)=>api.put(`/doctor-portal/${doctorId}/availability?status=${status}`),
};

// ─── Audit ────────────────────────────────────────────────────
export const auditApi = {
  getPriorityLog:(days?:number)=>api.get<{data:PriorityAuditEntry[]}>(`/admin/audit/priority?days=${days??7}`),
  getHistorical:(days?:number)=>api.get<{data:HistoricalData}>(`/admin/audit/historical?days=${days??7}`),
};

// ─── Admin ────────────────────────────────────────────────────
export const adminApi = {
  getDashboard:()=>api.get<{data:AnalyticsDashboard}>('/admin/analytics/dashboard'),
  listDoctors:()=>api.get<{data:DoctorResponse[]}>('/admin/doctors'),
  createDoctor:(d:any)=>api.post<{data:DoctorResponse}>('/admin/doctors',d),
  updateDoctor:(id:number,d:any)=>api.put<{data:DoctorResponse}>(`/admin/doctors/${id}`,d),
  deleteDoctor:(id:number)=>api.delete(`/admin/doctors/${id}`),
  updateAvailability:(id:number,status:string)=>api.put(`/admin/doctors/${id}/availability?status=${status}`),
  updateDoctorDelay:(d:{doctorId:number;delayMinutes:number;reason?:string})=>api.put('/admin/doctors/delay',d),
  listUsers:()=>api.get<{data:UserResponse[]}>('/admin/users'),
  updateRole:(d:{userId:number;role:string})=>api.put('/admin/users/role',d),
  deleteUser:(id:number)=>api.delete(`/admin/users/${id}`),
  resetPassword:(id:number,newPassword:string)=>api.put(`/admin/users/${id}/reset-password`,{newPassword}),
  listQueues:()=>api.get<{data:QueueStatus[]}>('/admin/queues'),
  updateQueue:(id:number,d:any)=>api.put(`/admin/queues/${id}`,d),
  deleteQueue:(id:number)=>api.delete(`/admin/queues/${id}`),
  resetQueue:(id:number)=>api.post(`/admin/queues/${id}/reset`),
  listAppointments:(status?:string,doctorId?:number)=>api.get<{data:any[]}>('/admin/appointments',{params:{status,doctorId}}),
  updatePriority:(id:number,priority:string,reason?:string)=>api.put(`/admin/appointments/${id}/priority?priority=${priority}${reason?`&reason=${encodeURIComponent(reason)}`:''}`),
  completeAppointment:(id:number)=>api.put(`/admin/appointments/${id}/complete`),
  cancelAppointment:(id:number,reason?:string)=>api.put(`/admin/appointments/${id}/cancel`,{reason}),
  markNoShow:(id:number)=>api.put(`/admin/appointments/${id}/no-show`),
  broadcast:(queueId:number,message:string)=>api.post(`/admin/notify/broadcast?queueId=${queueId}&message=${encodeURIComponent(message)}`),
  kioskRegister:(d:{name:string;phone:string;priority:string;doctorId:number;queueId:number})=>api.post<{data:AppointmentResponse}>('/kiosk/register',d),
};

// ─── Notifications ────────────────────────────────────────────
export const notifApi = {
  getAll:()=>api.get('/notifications'),
  markRead:(id:number)=>api.put(`/notifications/${id}/read`),
  markAllRead:()=>api.put('/notifications/read-all'),
};

export default api;

// ─── Payment admin stats ───────────────────────────────────────
// Already defined in main api.ts - this is just a reminder
// GET /api/payments/admin/stats → adminApi area
