import React, { useEffect, useState, useCallback } from 'react';
import { paymentApi, appointmentApi, PaymentResponse, AppointmentResponse } from '../services/api';
import { Card, CardTitle, Empty, Spinner, Select } from '../components/UI';
import { useNavigate } from 'react-router-dom';
import jsPDF from 'jspdf';

type Filter = 'today' | '7days' | '30days' | 'all';

const STATUS_STYLE: Record<string, string> = {
  PAID:     'bg-green-100 text-green-700',
  PENDING:  'bg-amber-100 text-amber-700',
  FAILED:   'bg-red-100 text-red-700',
  REFUNDED: 'bg-gray-100 text-gray-600',
};

const downloadReceiptSimple = (p: PaymentResponse) => {
  const doc = new jsPDF();
  const pageW = doc.internal.pageSize.getWidth();
  doc.setFillColor(13, 148, 136);
  doc.rect(0, 0, pageW, 28, 'F');
  doc.setTextColor(255, 255, 255);
  doc.setFontSize(16); doc.setFont('helvetica', 'bold');
  doc.text('SmartQueue — Payment Receipt', 14, 18);
  doc.setTextColor(30, 30, 30);
  let y = 44;
  const rows: [string, string][] = [
    ['Payment ID',    p.razorpayPaymentId ?? `PAY-${p.id}`],
    ['Appointment',   `#${p.appointmentId}`],
    ['Amount',        `₹${p.amount?.toFixed ? p.amount.toFixed(2) : p.amount}`],
    ['Currency',      p.currency ?? 'INR'],
    ['Status',        p.status],
    ['Date',          p.paidAt ? new Date(p.paidAt).toLocaleString('en-IN') : '—'],
  ];
  rows.forEach(([label, value]) => {
    doc.setFontSize(10); doc.setFont('helvetica', 'normal');
    doc.setTextColor(120, 120, 120); doc.text(label + ':', 14, y);
    doc.setFont('helvetica', 'bold'); doc.setTextColor(30, 30, 30);
    doc.text(value, 80, y);
    y += 10;
  });
  doc.setFillColor(13, 148, 136);
  doc.rect(0, doc.internal.pageSize.getHeight() - 16, pageW, 16, 'F');
  doc.setTextColor(255,255,255); doc.setFontSize(8); doc.setFont('helvetica', 'normal');
  doc.text('SmartQueue Hospital System · Computer generated receipt', pageW / 2, doc.internal.pageSize.getHeight() - 5, { align: 'center' });
  doc.save(`Receipt_${p.id}.pdf`);
};

export const PaymentHistoryPage = () => {
  const navigate = useNavigate();
  const [appointments, setAppointments] = useState<AppointmentResponse[]>([]);
  const [payments, setPayments]         = useState<Record<number, PaymentResponse>>({});
  const [filter, setFilter]             = useState<Filter>('all');
  const [search, setSearch]             = useState('');
  const [loading, setLoading]           = useState(true);

  const fetchData = useCallback(async () => {
    setLoading(true);
    try {
      const apptRes = await appointmentApi.getMyAppointments();
      const appts   = apptRes.data.data ?? [];
      setAppointments(appts);

      // Fetch payment for each paid appointment
      const payMap: Record<number, PaymentResponse> = {};
      await Promise.allSettled(
        appts.filter(a => a.paymentRequired || a.paymentStatus === 'PAID').map(async a => {
          try {
            const res = await paymentApi.getByAppointment(a.id);
            if (res.data.data) payMap[a.id] = res.data.data;
          } catch {}
        })
      );
      setPayments(payMap);
    } catch {}
    finally { setLoading(false); }
  }, []);

  useEffect(() => { fetchData(); }, [fetchData]);

  const now = new Date();
  const filtered = appointments.filter(a => {
    const created = new Date(a.createdAt);
    if (filter === 'today')  return created.toDateString() === now.toDateString();
    if (filter === '7days')  return (now.getTime() - created.getTime()) <= 7 * 86400000;
    if (filter === '30days') return (now.getTime() - created.getTime()) <= 30 * 86400000;
    return true;
  }).filter(a => {
    if (!search.trim()) return true;
    const q = search.toLowerCase();
    return a.doctorName?.toLowerCase().includes(q)
      || a.id.toString().includes(q)
      || payments[a.id]?.razorpayPaymentId?.toLowerCase().includes(q);
  });

  const totalPaid   = Object.values(payments).filter(p => p.status === 'PAID').reduce((s, p) => s + (Number(p.amount) || 0), 0);
  const totalPending = appointments.filter(a => a.paymentStatus === 'PENDING').length;

  if (loading) return <div className="flex justify-center py-20"><Spinner size={32} /></div>;

  return (
    <div className="max-w-4xl mx-auto px-4 sm:px-6 py-6 flex flex-col gap-5">
      <div>
        <h1 className="text-lg font-semibold text-gray-800">Payment History</h1>
        <p className="text-sm text-gray-400 mt-0.5">Your appointment payments and receipts</p>
      </div>

      {/* Summary stats */}
      <div className="grid grid-cols-2 sm:grid-cols-3 gap-3">
        <div className="bg-white rounded-xl border border-gray-100 p-4">
          <p className="text-xs text-gray-400">Total paid</p>
          <p className="text-xl font-bold text-green-600 mt-1">₹{totalPaid.toFixed(2)}</p>
        </div>
        <div className="bg-white rounded-xl border border-gray-100 p-4">
          <p className="text-xs text-gray-400">Appointments</p>
          <p className="text-xl font-bold text-gray-800 mt-1">{appointments.length}</p>
        </div>
        <div className="bg-white rounded-xl border border-gray-100 p-4">
          <p className="text-xs text-gray-400">Pending payments</p>
          <p className="text-xl font-bold text-amber-500 mt-1">{totalPending}</p>
        </div>
      </div>

      {/* Filters + Search */}
      <div className="flex flex-col sm:flex-row gap-3">
        <div className="flex gap-1 bg-gray-100 p-1 rounded-xl">
          {(['today','7days','30days','all'] as Filter[]).map(f => (
            <button key={f} onClick={() => setFilter(f)}
              className={`px-3 py-1.5 rounded-lg text-xs font-medium transition-all whitespace-nowrap ${
                filter === f ? 'bg-white text-gray-800 shadow-sm' : 'text-gray-500 hover:text-gray-700'
              }`}>
              {f === 'today' ? 'Today' : f === '7days' ? 'Last 7 days' : f === '30days' ? 'Last 30 days' : 'All'}
            </button>
          ))}
        </div>
        <input value={search} onChange={e => setSearch(e.target.value)}
          placeholder="Search by doctor, payment ID..."
          className="flex-1 px-3 py-2 text-sm border border-gray-200 rounded-xl focus:outline-none focus:ring-2 focus:ring-teal-500"/>
      </div>

      {/* Table */}
      <Card>
        {filtered.length === 0
          ? <Empty message="No payment records found." />
          : (
          <div className="overflow-x-auto">
            <table className="w-full text-sm">
              <thead>
                <tr className="text-xs text-gray-400 border-b border-gray-100">
                  <th className="text-left pb-3 pr-4 font-medium">Appointment</th>
                  <th className="text-left pb-3 pr-4 font-medium">Doctor</th>
                  <th className="text-left pb-3 pr-4 font-medium">Date</th>
                  <th className="text-right pb-3 pr-4 font-medium">Amount</th>
                  <th className="text-left pb-3 pr-4 font-medium">Status</th>
                  <th className="text-left pb-3 font-medium">Receipt</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-gray-50">
                {filtered.map(appt => {
                  const pay = payments[appt.id];
                  const status = pay?.status ?? (appt.paymentRequired ? 'PENDING' : 'PAID');
                  return (
                    <tr key={appt.id} className="hover:bg-gray-50">
                      <td className="py-3 pr-4">
                        <span className="font-mono text-xs bg-gray-100 px-2 py-0.5 rounded">
                          #{appt.id} · T-{String(appt.tokenNumber).padStart(2,'0')}
                        </span>
                      </td>
                      <td className="py-3 pr-4 font-medium text-gray-700">{appt.doctorName}</td>
                      <td className="py-3 pr-4 text-gray-500 text-xs whitespace-nowrap">
                        {new Date(appt.createdAt).toLocaleDateString('en-IN', { day:'2-digit', month:'short', year:'numeric' })}
                      </td>
                      <td className="py-3 pr-4 text-right font-semibold text-gray-800">
                        {pay ? `₹${Number(pay.amount).toFixed(2)}` : '₹200.00'}
                      </td>
                      <td className="py-3 pr-4">
                        <span className={`inline-flex px-2 py-0.5 rounded-full text-xs font-semibold ${STATUS_STYLE[status] ?? 'bg-gray-100 text-gray-600'}`}>
                          {status}
                        </span>
                      </td>
                      <td className="py-3">
                        {pay && pay.status === 'PAID' ? (
                          <button onClick={() => downloadReceiptSimple(pay)}
                            className="text-xs text-teal-600 hover:underline font-medium flex items-center gap-1">
                            <svg className="w-3.5 h-3.5" fill="none" stroke="currentColor" strokeWidth={2} viewBox="0 0 24 24">
                              <path strokeLinecap="round" d="M12 10v6m0 0l-3-3m3 3l3-3m2 8H7a2 2 0 01-2-2V5a2 2 0 012-2h5.586a1 1 0 01.707.293l5.414 5.414a1 1 0 01.293.707V19a2 2 0 01-2 2z"/>
                            </svg>
                            PDF
                          </button>
                        ) : status === 'PENDING' ? (
                          <button onClick={() => navigate('/payment', { state: { appointment: appt } })}
                            className="text-xs bg-amber-100 text-amber-700 px-2 py-1 rounded-lg hover:bg-amber-200 font-medium">
                            Pay Now
                          </button>
                        ) : (
                          <span className="text-xs text-gray-300">—</span>
                        )}
                      </td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          </div>
        )}
      </Card>
    </div>
  );
};
