import React, { useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import { Input, Button, Spinner } from '../components/UI';

// ─── LOGIN PAGE ───────────────────────────────────────────────
export const LoginPage = () => {
  const { login } = useAuth();
  const navigate = useNavigate();
  const [email, setEmail]       = useState('');
  const [password, setPassword] = useState('');
  const [error, setError]       = useState('');
  const [loading, setLoading]   = useState(false);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError(''); setLoading(true);
    try {
      await login(email, password);
      navigate('/dashboard');
    } catch (err: any) {
      setError(err.response?.data?.message ?? 'Invalid email or password.');
    } finally { setLoading(false); }
  };

  return (
    <div className="min-h-screen bg-gradient-to-br from-teal-50 to-gray-50 flex items-center justify-center p-4">
      <div className="w-full max-w-sm">
        <div className="text-center mb-8">
          <div className="inline-flex w-14 h-14 rounded-2xl bg-teal-600 items-center justify-center mb-3">
            <svg className="w-7 h-7 text-white" fill="none" stroke="currentColor" strokeWidth={2.5} viewBox="0 0 24 24">
              <path strokeLinecap="round" d="M22 12h-4l-3 9L9 3l-3 9H2"/>
            </svg>
          </div>
          <h1 className="text-xl font-semibold text-gray-800">SmartQueue</h1>
          <p className="text-sm text-gray-400 mt-1">Intelligent Hospital Queue System</p>
        </div>

        <div className="bg-white rounded-2xl border border-gray-100 shadow-sm p-6">
          <h2 className="text-base font-semibold text-gray-800 mb-4">Sign in</h2>

          {error && (
            <div className="bg-red-50 border border-red-100 text-red-600 text-sm rounded-lg px-3 py-2 mb-4">
              {error}
            </div>
          )}

          <form onSubmit={handleSubmit} className="flex flex-col gap-4">
            <Input label="Email" type="email" value={email} onChange={setEmail}
              placeholder="you@example.com" required />
            <Input label="Password" type="password" value={password} onChange={setPassword}
              placeholder="••••••" required />
            <Button type="submit" disabled={loading} className="w-full mt-1">
              {loading ? <Spinner size={16} /> : 'Sign in'}
            </Button>
          </form>

          {/* Demo accounts hint */}
          <div className="mt-4 p-3 bg-gray-50 rounded-lg text-xs text-gray-500">
            <p className="font-medium text-gray-600 mb-1.5">Demo accounts</p>
            <div className="flex flex-col gap-1">
              <p>🔑 Admin: <span className="font-mono">admin@demo.com</span> / password</p>
              <p>🏥 Patient: <span className="font-mono">patient@demo.com</span> / password</p>
            </div>
          </div>
        </div>

        {/* BUG 9 FIX: Show register link — but registration is PATIENT only */}
        <p className="text-center text-sm text-gray-400 mt-4">
          New patient?{' '}
          <Link to="/register" className="text-teal-600 hover:underline font-medium">
            Register here
          </Link>
        </p>
      </div>
    </div>
  );
};

// ─── REGISTER PAGE (PATIENT ONLY) ─────────────────────────────
export const RegisterPage = () => {
  const { register } = useAuth();
  const navigate = useNavigate();
  const [form, setForm] = useState({ name: '', email: '', password: '', phone: '' });
  const [error, setError]   = useState('');
  const [loading, setLoading] = useState(false);
  const set = (key: string) => (v: string) => setForm(f => ({ ...f, [key]: v }));

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (form.password.length < 6) { setError('Password must be at least 6 characters.'); return; }
    setError(''); setLoading(true);
    try {
      // BUG 9 FIX: Always register as PATIENT — role is forced server-side too
      await register(form.name, form.email, form.password, form.phone, 'PATIENT');
      navigate('/dashboard');
    } catch (err: any) {
      setError(err.response?.data?.message ?? 'Registration failed.');
    } finally { setLoading(false); }
  };

  return (
    <div className="min-h-screen bg-gradient-to-br from-teal-50 to-gray-50 flex items-center justify-center p-4">
      <div className="w-full max-w-sm">
        <div className="text-center mb-6">
          <div className="inline-flex w-14 h-14 rounded-2xl bg-teal-600 items-center justify-center mb-3">
            <svg className="w-7 h-7 text-white" fill="none" stroke="currentColor" strokeWidth={2.5} viewBox="0 0 24 24">
              <path strokeLinecap="round" d="M22 12h-4l-3 9L9 3l-3 9H2"/>
            </svg>
          </div>
          <h1 className="text-xl font-semibold text-gray-800">Patient Registration</h1>
          <p className="text-sm text-gray-400 mt-1">Create your patient account</p>
        </div>

        <div className="bg-white rounded-2xl border border-gray-100 shadow-sm p-6">
          {/* BUG 9 FIX: No role selector — patients only */}
          <div className="flex items-center gap-2 mb-4 px-3 py-2 bg-teal-50 border border-teal-100 rounded-lg">
            <svg className="w-4 h-4 text-teal-600 flex-shrink-0" fill="none" stroke="currentColor" strokeWidth={2} viewBox="0 0 24 24">
              <path strokeLinecap="round" d="M13 16h-1v-4h-1m1-4h.01M21 12a9 9 0 11-18 0 9 9 0 0118 0z"/>
            </svg>
            <p className="text-xs text-teal-700">
              This form is for <strong>patients only</strong>. Doctors and admins are added by the hospital admin.
            </p>
          </div>

          {error && (
            <div className="bg-red-50 border border-red-100 text-red-600 text-sm rounded-lg px-3 py-2 mb-4">
              {error}
            </div>
          )}

          <form onSubmit={handleSubmit} className="flex flex-col gap-3">
            <Input label="Full name" value={form.name} onChange={set('name')}
              placeholder="Your full name" required />
            <Input label="Email" type="email" value={form.email} onChange={set('email')}
              placeholder="you@example.com" required />
            <Input label="Password" type="password" value={form.password} onChange={set('password')}
              placeholder="Min 6 characters" required />
            <Input label="Phone number" value={form.phone} onChange={set('phone')}
              placeholder="+91 98765 43210" />
            <Button type="submit" disabled={loading} className="w-full mt-2">
              {loading ? <Spinner size={16} /> : 'Create patient account'}
            </Button>
          </form>
        </div>

        <p className="text-center text-sm text-gray-400 mt-4">
          Already have an account?{' '}
          <Link to="/login" className="text-teal-600 hover:underline font-medium">Sign in</Link>
        </p>
      </div>
    </div>
  );
};
