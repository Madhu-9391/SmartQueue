import React from 'react';
import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom';
import { AuthProvider, useAuth } from './context/AuthContext';
import { ToastProvider } from './components/UI';
import { Navbar } from './components/Navbar';
import { LoginPage, RegisterPage }    from './pages/AuthPages';
import { DashboardPage }              from './pages/DashboardPage';
import { BookPage }                   from './pages/BookPage';
import { MyQueuePage }                from './pages/MyQueuePage';
import { AdminPage }                  from './pages/AdminPage';
import { AiEnginePage }               from './pages/AiEnginePage';
import { DoctorPortalPage }           from './pages/DoctorPortalPage';
import { HistoricalPage }             from './pages/HistoricalPage';
import { KioskPage }                  from './pages/KioskPage';
import { NotificationsPage }          from './pages/NotificationsPage';
import { PaymentPage }                from './pages/PaymentPage';
import { PaymentHistoryPage }         from './pages/PaymentHistoryPage';

const ProtectedRoute = ({ children, roles }: { children: React.ReactNode; roles?: string[] }) => {
  const { user, loading } = useAuth();
  if (loading) return (
    <div className="min-h-screen flex items-center justify-center">
      <div className="animate-spin w-8 h-8 border-2 border-teal-600 border-t-transparent rounded-full"/>
    </div>
  );
  if (!user) return <Navigate to="/login" replace/>;
  if (roles && !roles.includes(user.role)) return <Navigate to="/dashboard" replace/>;
  return <>{children}</>;
};

const AppLayout = ({ children }: { children: React.ReactNode }) => (
  <div className="min-h-screen">
    <Navbar/>
    <main>{children}</main>
  </div>
);

const AppRoutes = () => {
  const { user } = useAuth();
  return (
    <Routes>
      {/* Public auth routes */}
      <Route path="/login"    element={!user ? <LoginPage/>    : <Navigate to="/dashboard" replace/>}/>
      <Route path="/register" element={!user ? <RegisterPage/> : <Navigate to="/dashboard" replace/>}/>

      {/* Kiosk — no auth (reception tablet) */}
      <Route path="/kiosk" element={<KioskPage/>}/>

      {/* All authenticated roles */}
      <Route path="/dashboard" element={
        <ProtectedRoute><AppLayout><DashboardPage/></AppLayout></ProtectedRoute>
      }/>

      {/* BUG 5 FIX: Notifications route */}
      <Route path="/notifications" element={
        <ProtectedRoute><AppLayout><NotificationsPage/></AppLayout></ProtectedRoute>
      }/>

      {/* Patient only */}
      <Route path="/book" element={
        <ProtectedRoute roles={['PATIENT']}><AppLayout><BookPage/></AppLayout></ProtectedRoute>
      }/>
      <Route path="/my-queue" element={
        <ProtectedRoute roles={['PATIENT']}><AppLayout><MyQueuePage/></AppLayout></ProtectedRoute>
      }/>

      {/* BUG 6 FIX: Payment routes */}
      <Route path="/payment" element={
        <ProtectedRoute roles={['PATIENT']}><PaymentPage/></ProtectedRoute>
      }/>
      <Route path="/payment-history" element={
        <ProtectedRoute roles={['PATIENT']}><AppLayout><PaymentHistoryPage/></AppLayout></ProtectedRoute>
      }/>

      {/* Admin only */}
      <Route path="/admin" element={
        <ProtectedRoute roles={['ADMIN']}><AppLayout><AdminPage/></AppLayout></ProtectedRoute>
      }/>
      <Route path="/historical" element={
        <ProtectedRoute roles={['ADMIN']}><AppLayout><HistoricalPage/></AppLayout></ProtectedRoute>
      }/>

      {/* Admin + Doctor */}
      <Route path="/ai-engine" element={
        <ProtectedRoute roles={['ADMIN','DOCTOR']}><AppLayout><AiEnginePage/></AppLayout></ProtectedRoute>
      }/>
      <Route path="/doctor-portal" element={
        <ProtectedRoute roles={['ADMIN','DOCTOR']}><AppLayout><DoctorPortalPage/></AppLayout></ProtectedRoute>
      }/>

      {/* Fallback */}
      <Route path="/"  element={<Navigate to="/dashboard" replace/>}/>
      <Route path="*"  element={<Navigate to="/dashboard" replace/>}/>
    </Routes>
  );
};

export default function App() {
  return (
    <BrowserRouter>
      <AuthProvider>
        <ToastProvider>
          <AppRoutes/>
        </ToastProvider>
      </AuthProvider>
    </BrowserRouter>
  );
}
