import React, { ReactNode } from 'react';

// ─── BADGE ────────────────────────────────────────────────────
const priorityClasses: Record<string, string> = {
  EMERGENCY:       'bg-red-100 text-red-700 border border-red-200',
  VIP:             'bg-purple-100 text-purple-700 border border-purple-200',
  SENIOR_CITIZEN:  'bg-amber-100 text-amber-700 border border-amber-200',
  NORMAL:          'bg-gray-100 text-gray-600 border border-gray-200',
  WAITING:         'bg-blue-100 text-blue-700 border border-blue-200',
  ACTIVE:          'bg-green-100 text-green-700 border border-green-200',
  COMPLETED:       'bg-gray-100 text-gray-500 border border-gray-200',
  CANCELLED:       'bg-red-50 text-red-400 border border-red-100',
  NO_SHOW:         'bg-orange-100 text-orange-600 border border-orange-200',
};

export const Badge = ({ label }: { label: string }) => (
  <span className={`inline-flex items-center px-2 py-0.5 rounded-full text-xs font-medium ${priorityClasses[label] ?? 'bg-gray-100 text-gray-600'}`}>
    {label.replace('_', ' ')}
  </span>
);

// ─── CARD ─────────────────────────────────────────────────────
export const Card = ({ children, className = '' }: { children: ReactNode; className?: string }) => (
  <div className={`bg-white rounded-xl border border-gray-100 shadow-sm p-4 ${className}`}>
    {children}
  </div>
);

export const CardTitle = ({ children }: { children: ReactNode }) => (
  <h3 className="text-sm font-medium text-gray-500 mb-3 flex items-center gap-2">{children}</h3>
);

// ─── STAT CARD ────────────────────────────────────────────────
export const StatCard = ({ label, value, sub, color = 'teal' }: {
  label: string; value: string | number; sub?: string; color?: string;
}) => {
  const colors: Record<string, string> = {
    teal: 'text-teal-600', blue: 'text-blue-600', red: 'text-red-600',
    amber: 'text-amber-600', green: 'text-green-600', purple: 'text-purple-600',
  };
  return (
    <div className="bg-gray-50 rounded-lg p-3">
      <p className="text-xs text-gray-500 mb-1">{label}</p>
      <p className={`text-2xl font-semibold ${colors[color] ?? colors.teal}`}>{value}</p>
      {sub && <p className="text-xs text-gray-400 mt-0.5">{sub}</p>}
    </div>
  );
};

// ─── BUTTON ───────────────────────────────────────────────────
interface BtnProps {
  children: ReactNode; onClick?: () => void; disabled?: boolean;
  variant?: 'primary' | 'outline' | 'danger' | 'ghost';
  size?: 'sm' | 'md'; type?: 'button' | 'submit'; className?: string;
}
export const Button = ({ children, onClick, disabled, variant = 'primary', size = 'md', type = 'button', className = '' }: BtnProps) => {
  const base = 'inline-flex items-center justify-center gap-1.5 font-medium rounded-lg transition-all disabled:opacity-50 disabled:cursor-not-allowed';
  const sizes = { sm: 'px-3 py-1.5 text-xs', md: 'px-4 py-2 text-sm' };
  const variants = {
    primary: 'bg-teal-600 text-white hover:bg-teal-700 active:bg-teal-800',
    outline: 'bg-white text-gray-700 border border-gray-200 hover:bg-gray-50',
    danger:  'bg-white text-red-600 border border-red-200 hover:bg-red-50',
    ghost:   'bg-transparent text-gray-600 hover:bg-gray-100',
  };
  return (
    <button type={type} onClick={onClick} disabled={disabled}
      className={`${base} ${sizes[size]} ${variants[variant]} ${className}`}>
      {children}
    </button>
  );
};

// ─── INPUT ────────────────────────────────────────────────────
interface InputProps {
  label?: string; id?: string; type?: string; value: string;
  onChange: (v: string) => void; placeholder?: string; required?: boolean;
}
export const Input = ({ label, id, type = 'text', value, onChange, placeholder, required }: InputProps) => (
  <div className="flex flex-col gap-1">
    {label && <label htmlFor={id} className="text-xs font-medium text-gray-600">{label}</label>}
    <input id={id} type={type} value={value} placeholder={placeholder} required={required}
      onChange={e => onChange(e.target.value)}
      className="px-3 py-2 text-sm border border-gray-200 rounded-lg bg-white focus:outline-none focus:ring-2 focus:ring-teal-500 focus:border-transparent transition-all w-full"
    />
  </div>
);

// ─── SELECT ───────────────────────────────────────────────────
interface SelectProps {
  label?: string; value: string; onChange: (v: string) => void;
  options: { value: string; label: string }[];
}
export const Select = ({ label, value, onChange, options }: SelectProps) => (
  <div className="flex flex-col gap-1">
    {label && <label className="text-xs font-medium text-gray-600">{label}</label>}
    <select value={value} onChange={e => onChange(e.target.value)}
      className="px-3 py-2 text-sm border border-gray-200 rounded-lg bg-white focus:outline-none focus:ring-2 focus:ring-teal-500 transition-all w-full cursor-pointer">
      {options.map(o => <option key={o.value} value={o.value}>{o.label}</option>)}
    </select>
  </div>
);

// ─── LOADING SPINNER ──────────────────────────────────────────
export const Spinner = ({ size = 20 }: { size?: number }) => (
  <svg width={size} height={size} viewBox="0 0 24 24" fill="none" className="animate-spin text-teal-600">
    <circle cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="2" opacity=".25"/>
    <path d="M12 2a10 10 0 0 1 10 10" stroke="currentColor" strokeWidth="2" strokeLinecap="round"/>
  </svg>
);

// ─── LIVE DOT ─────────────────────────────────────────────────
export const LiveDot = () => (
  <span className="inline-flex items-center gap-1.5 text-xs font-medium text-red-600 bg-red-50 px-2 py-0.5 rounded-full">
    <span className="w-1.5 h-1.5 rounded-full bg-red-500 animate-pulse" />
    Live
  </span>
);

// ─── AI PREDICTION CARD ───────────────────────────────────────
export const AiPredictionCard = ({ predictedTime, confidence, waitMinutes, token }: {
  predictedTime: string; confidence: number; waitMinutes: number; token: number;
}) => (
  <div className="relative overflow-hidden rounded-xl bg-gradient-to-br from-teal-600 to-teal-700 text-white p-5">
    <div className="absolute top-0 right-0 w-32 h-32 rounded-full bg-white/5 -translate-y-1/2 translate-x-1/2" />
    <div className="absolute bottom-0 right-8 w-16 h-16 rounded-full bg-white/5 translate-y-1/2" />
    <div className="relative flex items-start justify-between">
      <div>
        <p className="text-xs text-teal-100 mb-1">AI predicted consultation time</p>
        <p className="text-3xl font-semibold">{predictedTime}</p>
        <p className="text-xs text-teal-200 mt-1">Confidence window: ± {confidence} min</p>
      </div>
      <div className="bg-white/15 rounded-lg px-4 py-2 text-center">
        <p className="text-xs text-teal-100">Token</p>
        <p className="text-2xl font-semibold">T-{String(token).padStart(2,'0')}</p>
      </div>
    </div>
    <div className="relative mt-4 grid grid-cols-2 gap-2">
      <div className="bg-white/10 rounded-lg px-3 py-2">
        <p className="text-xs text-teal-200">Est. wait</p>
        <p className="text-sm font-semibold">{waitMinutes} min</p>
      </div>
      <div className="bg-white/10 rounded-lg px-3 py-2">
        <p className="text-xs text-teal-200">Model</p>
        <p className="text-sm font-semibold">Random Forest</p>
      </div>
    </div>
  </div>
);

// ─── EMPTY STATE ──────────────────────────────────────────────
export const Empty = ({ message }: { message: string }) => (
  <div className="text-center py-8 text-sm text-gray-400">{message}</div>
);

// ─── TOAST ────────────────────────────────────────────────────
export const ToastContext = React.createContext<(msg: string, type?: 'success' | 'error' | 'info') => void>(() => {});

export const useToast = () => React.useContext(ToastContext);

export const ToastProvider = ({ children }: { children: ReactNode }) => {
  const [toasts, setToasts] = React.useState<{ id: number; msg: string; type: string }[]>([]);

  const show = (msg: string, type: 'success' | 'error' | 'info' = 'success') => {
    const id = Date.now();
    setToasts(p => [...p, { id, msg, type }]);
    setTimeout(() => setToasts(p => p.filter(t => t.id !== id)), 3500);
  };

  const colors = { success: 'bg-teal-600', error: 'bg-red-600', info: 'bg-blue-600' };

  return (
    <ToastContext.Provider value={show}>
      {children}
      <div className="fixed bottom-5 right-5 flex flex-col gap-2 z-50">
        {toasts.map(t => (
          <div key={t.id}
            className={`${colors[t.type as keyof typeof colors] ?? colors.success} text-white text-sm font-medium px-4 py-2.5 rounded-lg shadow-lg animate-slide-up`}>
            {t.msg}
          </div>
        ))}
      </div>
    </ToastContext.Provider>
  );
};
