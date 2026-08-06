import React, { useEffect, useState } from 'react';
import { useLocation, useNavigate } from 'react-router-dom';
import { paymentApi, PaymentResponse, AppointmentResponse } from '../services/api';
import { Spinner } from '../components/UI';
import jsPDF from 'jspdf';

/* ─── Types ─────────────────────────────────────────────────── */
type PaymentStage = 'summary' | 'processing' | 'success' | 'failed' | 'cancelled';

interface LocationState {
  appointment: AppointmentResponse;
  razorpayKeyId?: string;
}

/* ─── PDF Receipt ────────────────────────────────────────────── */
const downloadReceipt = (appt: AppointmentResponse, payment: PaymentResponse) => {
  const doc = new jsPDF();
  const pageW = doc.internal.pageSize.getWidth();

  // Header bar
  doc.setFillColor(13, 148, 136);
  doc.rect(0, 0, pageW, 28, 'F');
  doc.setTextColor(255, 255, 255);
  doc.setFontSize(18);
  doc.setFont('helvetica', 'bold');
  doc.text('SmartQueue', 14, 14);
  doc.setFontSize(9);
  doc.setFont('helvetica', 'normal');
  doc.text('Intelligent Hospital Queue System', 14, 21);

  // Receipt title
  doc.setTextColor(30, 30, 30);
  doc.setFontSize(14);
  doc.setFont('helvetica', 'bold');
  doc.text('Payment Receipt', pageW / 2, 42, { align: 'center' });

  // Success badge
  doc.setFillColor(220, 252, 231);
  doc.roundedRect(pageW / 2 - 25, 46, 50, 10, 3, 3, 'F');
  doc.setTextColor(22, 163, 74);
  doc.setFontSize(9);
  doc.setFont('helvetica', 'bold');
  doc.text('✓ PAYMENT SUCCESSFUL', pageW / 2, 53, { align: 'center' });

  // Divider
  doc.setDrawColor(230, 230, 230);
  doc.line(14, 62, pageW - 14, 62);

  // Fields
  doc.setTextColor(30, 30, 30);
  const fields: [string, string][] = [
    ['Payment ID',      payment.razorpayPaymentId ?? payment.id.toString()],
    ['Appointment ID',  appt.id.toString()],
    ['Patient Name',    appt.patientName],
    ['Doctor',          appt.doctorName],
    ['Specialization',  appt.doctorSpecialization ?? '—'],
    ['Amount Paid',     `₹${payment.amount?.toFixed ? payment.amount.toFixed(2) : payment.amount}`],
    ['Currency',        payment.currency ?? 'INR'],
    ['Transaction Date',payment.paidAt ? new Date(payment.paidAt).toLocaleString('en-IN') : new Date().toLocaleString('en-IN')],
  ];

  let y = 74;
  fields.forEach(([label, value]) => {
    doc.setFontSize(9);
    doc.setFont('helvetica', 'normal');
    doc.setTextColor(100, 100, 100);
    doc.text(label, 14, y);
    doc.setFont('helvetica', 'bold');
    doc.setTextColor(30, 30, 30);
    doc.text(value, 90, y);
    y += 9;
  });

  // Amount box
  y += 4;
  doc.setFillColor(13, 148, 136);
  doc.roundedRect(14, y, pageW - 28, 14, 3, 3, 'F');
  doc.setTextColor(255, 255, 255);
  doc.setFontSize(11);
  doc.setFont('helvetica', 'bold');
  doc.text(`Total Paid: ₹${payment.amount?.toFixed ? payment.amount.toFixed(2) : payment.amount}`, pageW / 2, y + 9, { align: 'center' });

  // Footer
  y += 24;
  doc.setDrawColor(230, 230, 230);
  doc.line(14, y, pageW - 14, y);
  doc.setTextColor(130, 130, 130);
  doc.setFontSize(8);
  doc.setFont('helvetica', 'normal');
  doc.text('This is a computer-generated receipt. No signature required.', pageW / 2, y + 7, { align: 'center' });
  doc.text('SmartQueue Hospital System · support@smartqueue.com', pageW / 2, y + 13, { align: 'center' });

  doc.save(`SmartQueue_Receipt_${payment.id}.pdf`);
};

/* ─── Payment Summary Card ────────────────────────────────────── */
const PaymentSummaryCard = ({ appt }: { appt: AppointmentResponse }) => (
  <div className="bg-white rounded-2xl border border-gray-100 shadow-sm overflow-hidden">
    {/* Doctor header */}
    <div className="bg-gradient-to-r from-teal-600 to-teal-500 px-6 py-5 text-white">
      <div className="flex items-center gap-4">
        <div className="w-14 h-14 rounded-xl bg-white/20 flex items-center justify-center text-2xl">
          🩺
        </div>
        <div>
          <p className="font-semibold text-lg leading-tight">{appt.doctorName}</p>
          <p className="text-teal-100 text-sm">{appt.doctorSpecialization}</p>
          <p className="text-teal-100 text-xs mt-0.5">SmartQueue Hospital</p>
        </div>
      </div>
    </div>

    {/* Appointment details */}
    <div className="px-6 py-4 flex flex-col gap-3">
      {[
        { label: 'Queue Token',        value: `T-${String(appt.tokenNumber).padStart(2,'0')}` },
        { label: 'Appointment Date',   value: appt.appointmentDate ? new Date(appt.appointmentDate).toLocaleDateString('en-IN', { weekday:'long', day:'2-digit', month:'long', year:'numeric' }) : 'Today' },
        { label: 'Expected Time',      value: appt.predictedVisitTime ? new Date(appt.predictedVisitTime).toLocaleTimeString('en-IN',{hour:'2-digit',minute:'2-digit'}) : '—' },
        { label: 'Priority',           value: appt.priority },
      ].map(({ label, value }) => (
        <div key={label} className="flex justify-between items-center py-1.5 border-b border-gray-50 last:border-0">
          <span className="text-sm text-gray-500">{label}</span>
          <span className="text-sm font-semibold text-gray-800">{value}</span>
        </div>
      ))}
    </div>

    {/* Amount breakdown */}
    <div className="px-6 py-4 bg-gray-50 border-t border-gray-100">
      <p className="text-xs font-semibold text-gray-400 uppercase tracking-wide mb-3">Payment Summary</p>
      {[
        { label: 'Consultation Fee', value: '₹190.00' },
        { label: 'Platform Fee',     value: '₹10.00'  },
        { label: 'GST (18%)',        value: '₹0.00'   },
        { label: 'Discount',         value: '-₹0.00'  },
      ].map(({ label, value }) => (
        <div key={label} className="flex justify-between text-sm py-1">
          <span className="text-gray-500">{label}</span>
          <span className="text-gray-700">{value}</span>
        </div>
      ))}
      <div className="flex justify-between text-base font-bold text-gray-900 pt-3 mt-1 border-t border-gray-200">
        <span>Total Amount</span>
        <span className="text-teal-600">₹200.00</span>
      </div>
    </div>
  </div>
);

/* ─── Processing Screen ────────────────────────────────────────── */
const ProcessingScreen = () => (
  <div className="min-h-screen bg-gray-50 flex items-center justify-center">
    <div className="text-center flex flex-col items-center gap-5">
      <div className="relative w-20 h-20">
        <div className="absolute inset-0 rounded-full border-4 border-teal-100"/>
        <div className="absolute inset-0 rounded-full border-4 border-teal-600 border-t-transparent animate-spin"/>
      </div>
      <div>
        <p className="text-lg font-semibold text-gray-800">Processing your payment...</p>
        <p className="text-sm text-gray-400 mt-1">Please do not close this window.</p>
      </div>
      <div className="flex items-center gap-2 text-xs text-gray-400">
        <svg className="w-4 h-4 text-green-500" fill="currentColor" viewBox="0 0 20 20">
          <path fillRule="evenodd" d="M5 9V7a5 5 0 0110 0v2a2 2 0 012 2v5a2 2 0 01-2 2H5a2 2 0 01-2-2v-5a2 2 0 012-2zm8-2v2H7V7a3 3 0 016 0z" clipRule="evenodd"/>
        </svg>
        256-bit SSL encrypted · Secured by Razorpay
      </div>
    </div>
  </div>
);

/* ─── Success Screen ───────────────────────────────────────────── */
const SuccessScreen = ({ appt, payment, onDownload, onViewAppt, onDashboard }: {
  appt: AppointmentResponse;
  payment: PaymentResponse;
  onDownload: () => void;
  onViewAppt: () => void;
  onDashboard: () => void;
}) => (
  <div className="min-h-screen bg-gray-50 flex items-center justify-center p-4">
    <div className="w-full max-w-md">
      <div className="bg-white rounded-2xl border border-gray-100 shadow-sm overflow-hidden">
        {/* Success header */}
        <div className="bg-gradient-to-br from-green-500 to-green-600 px-6 py-8 text-center text-white">
          <div className="inline-flex w-20 h-20 rounded-full bg-white/20 items-center justify-center mb-4">
            <svg className="w-10 h-10 text-white" fill="none" stroke="currentColor" strokeWidth={2.5} viewBox="0 0 24 24">
              <path strokeLinecap="round" strokeLinejoin="round" d="M5 13l4 4L19 7"/>
            </svg>
          </div>
          <h1 className="text-2xl font-bold">Payment Successful!</h1>
          <p className="text-green-100 mt-1">Appointment Confirmed</p>
        </div>

        {/* Details */}
        <div className="px-6 py-5 flex flex-col gap-3">
          {[
            { label: 'Payment ID',     value: payment.razorpayPaymentId ?? `PAY-${payment.id}` },
            { label: 'Appointment ID', value: `#${appt.id}` },
            { label: 'Doctor',         value: appt.doctorName },
            { label: 'Patient',        value: appt.patientName },
            { label: 'Token',          value: `T-${String(appt.tokenNumber).padStart(2,'0')}` },
            { label: 'Amount Paid',    value: `₹${payment.amount?.toFixed ? payment.amount.toFixed(2) : '200.00'}` },
            { label: 'Transaction Time', value: payment.paidAt ? new Date(payment.paidAt).toLocaleString('en-IN') : new Date().toLocaleString('en-IN') },
          ].map(({ label, value }) => (
            <div key={label} className="flex justify-between py-1.5 border-b border-gray-50 last:border-0">
              <span className="text-sm text-gray-500">{label}</span>
              <span className="text-sm font-semibold text-gray-800">{value}</span>
            </div>
          ))}
        </div>

        {/* Actions */}
        <div className="px-6 pb-6 flex flex-col gap-2">
          <button onClick={onDownload}
            className="w-full py-3 bg-teal-600 text-white font-semibold rounded-xl hover:bg-teal-700 active:scale-95 transition-all flex items-center justify-center gap-2">
            <svg className="w-4 h-4" fill="none" stroke="currentColor" strokeWidth={2} viewBox="0 0 24 24">
              <path strokeLinecap="round" d="M12 10v6m0 0l-3-3m3 3l3-3m2 8H7a2 2 0 01-2-2V5a2 2 0 012-2h5.586a1 1 0 01.707.293l5.414 5.414a1 1 0 01.293.707V19a2 2 0 01-2 2z"/>
            </svg>
            Download Receipt (PDF)
          </button>
          <button onClick={onViewAppt}
            className="w-full py-3 border border-teal-200 text-teal-700 font-medium rounded-xl hover:bg-teal-50 transition-all">
            View My Queue
          </button>
          <button onClick={onDashboard}
            className="w-full py-2.5 text-gray-400 text-sm hover:text-gray-600 transition-colors">
            Back to Dashboard
          </button>
        </div>
      </div>
    </div>
  </div>
);

/* ─── Failed Screen ────────────────────────────────────────────── */
const FailedScreen = ({ reason, onRetry, onBack }: {
  reason?: string; onRetry: () => void; onBack: () => void;
}) => (
  <div className="min-h-screen bg-gray-50 flex items-center justify-center p-4">
    <div className="w-full max-w-sm text-center">
      <div className="bg-white rounded-2xl border border-gray-100 shadow-sm p-8 flex flex-col items-center gap-4">
        <div className="w-20 h-20 rounded-full bg-red-50 flex items-center justify-center">
          <svg className="w-10 h-10 text-red-500" fill="none" stroke="currentColor" strokeWidth={2} viewBox="0 0 24 24">
            <path strokeLinecap="round" d="M6 18L18 6M6 6l12 12"/>
          </svg>
        </div>
        <div>
          <h2 className="text-xl font-bold text-gray-800">Payment Failed</h2>
          <p className="text-sm text-gray-500 mt-1">{reason ?? 'Something went wrong. Please try again.'}</p>
        </div>
        <div className="w-full flex flex-col gap-2 mt-2">
          <button onClick={onRetry}
            className="w-full py-3 bg-teal-600 text-white font-semibold rounded-xl hover:bg-teal-700 transition-all">
            Retry Payment
          </button>
          <button onClick={onBack}
            className="w-full py-2.5 border border-gray-200 text-gray-600 rounded-xl hover:bg-gray-50 transition-all">
            Go Back
          </button>
        </div>
      </div>
    </div>
  </div>
);

/* ─── Cancelled Screen ─────────────────────────────────────────── */
const CancelledScreen = ({ onRetry, onBack }: { onRetry: () => void; onBack: () => void }) => (
  <div className="min-h-screen bg-gray-50 flex items-center justify-center p-4">
    <div className="w-full max-w-sm text-center">
      <div className="bg-white rounded-2xl border border-gray-100 shadow-sm p-8 flex flex-col items-center gap-4">
        <div className="w-20 h-20 rounded-full bg-amber-50 flex items-center justify-center">
          <svg className="w-10 h-10 text-amber-500" fill="none" stroke="currentColor" strokeWidth={2} viewBox="0 0 24 24">
            <path strokeLinecap="round" d="M6 18L18 6M6 6l12 12"/>
          </svg>
        </div>
        <div>
          <h2 className="text-xl font-bold text-gray-800">Payment Cancelled</h2>
          <p className="text-sm text-gray-500 mt-1">Your appointment is still pending payment. You can retry anytime.</p>
        </div>
        <div className="w-full flex flex-col gap-2 mt-2">
          <button onClick={onRetry}
            className="w-full py-3 bg-teal-600 text-white font-semibold rounded-xl hover:bg-teal-700 transition-all">
            Retry Payment
          </button>
          <button onClick={onBack}
            className="w-full py-2.5 border border-gray-200 text-gray-600 rounded-xl hover:bg-gray-50 transition-all">
            Go Back
          </button>
        </div>
      </div>
    </div>
  </div>
);

/* ─── MAIN PAYMENT PAGE ─────────────────────────────────────────── */
export const PaymentPage = () => {
  const location = useLocation();
  const navigate  = useNavigate();
  const state = location.state as LocationState | null;

  const [stage, setStage]     = useState<PaymentStage>('summary');
  const [payment, setPayment] = useState<PaymentResponse | null>(null);
  const [error, setError]     = useState('');
  const [loading, setLoading] = useState(false);

  const appt = state?.appointment;

  useEffect(() => {
    if (!appt) navigate('/my-queue');
  }, [appt, navigate]);

  if (!appt) return null;

  // Load Razorpay checkout script
  const loadRazorpay = () => new Promise<boolean>(resolve => {
    if ((window as any).Razorpay) { resolve(true); return; }
    const s = document.createElement('script');
    s.src = 'https://checkout.razorpay.com/v1/checkout.js';
    s.onload  = () => resolve(true);
    s.onerror = () => resolve(false);
    document.body.appendChild(s);
  });

  const handlePayNow = async () => {
    setLoading(true);
    setStage('processing');
    try {
      const ok = await loadRazorpay();
      if (!ok) throw new Error('Failed to load payment gateway');

      const orderRes = await paymentApi.createOrder(appt.id);
      const order    = orderRes.data.data;
      const razorpayKey = "rzp_test_TImlLdiSZFlYxq";

if (!razorpayKey) {
    throw new Error("Razorpay Key ID is missing");
}
      const options = {
        key: razorpayKey,
        amount:      (order.amount as any) ?? 200,
        currency:    order.currency ?? 'INR',
        name:        'SmartQueue Hospital',
        description: `Consultation with ${appt.doctorName}`,
        order_id:    order.razorpayOrderId,
        prefill:     { name: appt.patientName },
        theme:       { color: '#0d9488' },
        handler: async (response: any) => {
          try {
            setStage('processing');
            const verifyRes = await paymentApi.verify({
              razorpayOrderId:   response.razorpay_order_id,
              razorpayPaymentId: response.razorpay_payment_id,
              razorpaySignature: response.razorpay_signature,
              appointmentId:     appt.id,
            });
            setPayment(verifyRes.data.data);
            setStage('success');
          } catch (e: any) {
            setError(e.response?.data?.message ?? 'Verification failed');
            setStage('failed');
          }
        },
        modal: {
          ondismiss: () => setStage('cancelled'),
        },
      };

      const rz = new (window as any).Razorpay(options);
      rz.on('payment.failed', (resp: any) => {
        setError(resp.error?.description ?? 'Payment failed');
        setStage('failed');
      });
      rz.open();
    } catch (e: any) {
      setError(e.message ?? 'Could not initiate payment');
      setStage('failed');
    } finally {
      setLoading(false);
    }
  };

  if (stage === 'processing') return <ProcessingScreen />;

  if (stage === 'success' && payment) return (
    <SuccessScreen
      appt={appt} payment={payment}
      onDownload={() => downloadReceipt(appt, payment)}
      onViewAppt={() => navigate('/my-queue')}
      onDashboard={() => navigate('/dashboard')}
    />
  );

  if (stage === 'failed') return (
    <FailedScreen
      reason={error}
      onRetry={() => { setStage('summary'); setError(''); }}
      onBack={() => navigate('/my-queue')}
    />
  );

  if (stage === 'cancelled') return (
    <CancelledScreen
      onRetry={() => setStage('summary')}
      onBack={() => navigate('/my-queue')}
    />
  );

  // Summary stage
  return (
    <div className="min-h-screen bg-gray-50 py-8 px-4">
      <div className="max-w-md mx-auto flex flex-col gap-5">
        <div>
          <h1 className="text-lg font-semibold text-gray-800">Complete Payment</h1>
          <p className="text-sm text-gray-400 mt-0.5">Review your appointment details before paying</p>
        </div>

        <PaymentSummaryCard appt={appt} />

        {/* Pay button */}
        <div className="bg-white rounded-2xl border border-gray-100 shadow-sm p-5 flex flex-col gap-4">
          <button
            onClick={handlePayNow}
            disabled={loading}
            className="w-full py-4 bg-teal-600 text-white text-base font-bold rounded-xl hover:bg-teal-700 active:scale-95 disabled:opacity-50 transition-all flex items-center justify-center gap-2">
            {loading ? <Spinner size={18} /> : (
              <>
                <svg className="w-5 h-5" fill="none" stroke="currentColor" strokeWidth={2} viewBox="0 0 24 24">
                  <path strokeLinecap="round" d="M12 15v2m-6 4h12a2 2 0 002-2v-6a2 2 0 00-2-2H6a2 2 0 00-2 2v6a2 2 0 002 2zm10-10V7a4 4 0 00-8 0v4h8z"/>
                </svg>
                Pay ₹200.00 Securely
              </>
            )}
          </button>

          {/* Trust badges */}
          <div className="flex items-center justify-center gap-4 text-xs text-gray-400">
            <div className="flex items-center gap-1">
              <svg className="w-3.5 h-3.5 text-green-500" fill="currentColor" viewBox="0 0 20 20">
                <path fillRule="evenodd" d="M5 9V7a5 5 0 0110 0v2a2 2 0 012 2v5a2 2 0 01-2 2H5a2 2 0 01-2-2v-5a2 2 0 012-2zm8-2v2H7V7a3 3 0 016 0z" clipRule="evenodd"/>
              </svg>
              SSL Secured
            </div>
            <div className="flex items-center gap-1">
              <svg className="w-3.5 h-3.5 text-green-500" fill="currentColor" viewBox="0 0 20 20">
                <path fillRule="evenodd" d="M10 18a8 8 0 100-16 8 8 0 000 16zm3.707-9.293a1 1 0 00-1.414-1.414L9 10.586 7.707 9.293a1 1 0 00-1.414 1.414l2 2a1 1 0 001.414 0l4-4z" clipRule="evenodd"/>
              </svg>
              Razorpay Secure
            </div>
            <div className="flex items-center gap-1">
              <svg className="w-3.5 h-3.5 text-green-500" fill="currentColor" viewBox="0 0 20 20">
                <path d="M9 2a1 1 0 000 2h2a1 1 0 100-2H9z"/>
                <path fillRule="evenodd" d="M4 5a2 2 0 012-2 3 3 0 003 3h2a3 3 0 003-3 2 2 0 012 2v11a2 2 0 01-2 2H6a2 2 0 01-2-2V5zm3 4a1 1 0 000 2h.01a1 1 0 100-2H7zm3 0a1 1 0 000 2h3a1 1 0 100-2h-3zm-3 4a1 1 0 100 2h.01a1 1 0 100-2H7zm3 0a1 1 0 100 2h3a1 1 0 100-2h-3z" clipRule="evenodd"/>
              </svg>
              100% Safe
            </div>
          </div>
        </div>

        <button onClick={() => navigate('/my-queue')}
          className="text-center text-sm text-gray-400 hover:text-gray-600 transition-colors">
          ← Back to My Queue
        </button>
      </div>
    </div>
  );
};
