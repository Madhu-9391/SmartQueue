import React, { useEffect, useState, useCallback } from 'react';
import { notifApi } from '../services/api';
import { Card, CardTitle, Button, Empty, Spinner } from '../components/UI';

interface Notification {
  id: number;
  message: string;
  type: string;
  status: string;
  createdAt: string;
}

const TYPE_ICON: Record<string, string> = {
  TOKEN_CALLED:          '🔔',
  ETA_UPDATED:           '⏰',
  DOCTOR_DELAYED:        '⏳',
  APPOINTMENT_CANCELLED: '❌',
  GENERAL:               '📢',
};

const TYPE_COLOR: Record<string, string> = {
  TOKEN_CALLED:          'border-l-green-500 bg-green-50',
  ETA_UPDATED:           'border-l-blue-500 bg-blue-50',
  DOCTOR_DELAYED:        'border-l-amber-500 bg-amber-50',
  APPOINTMENT_CANCELLED: 'border-l-red-500 bg-red-50',
  GENERAL:               'border-l-teal-500 bg-teal-50',
};

export const NotificationsPage = () => {
  const [notifications, setNotifications] = useState<Notification[]>([]);
  const [loading, setLoading]   = useState(true);
  const [marking, setMarking]   = useState(false);

  const fetchNotifications = useCallback(async () => {
    try {
      const res = await notifApi.getAll();
      setNotifications(res.data?.data ?? []);
    } catch {}
    finally { setLoading(false); }
  }, []);

  useEffect(() => { fetchNotifications(); }, [fetchNotifications]);

  const markAllRead = async () => {
    setMarking(true);
    try {
      await notifApi.markAllRead();
      setNotifications(n => n.map(x => ({ ...x, status: 'READ' })));
    } catch {}
    finally { setMarking(false); }
  };

  const unreadCount = notifications.filter(n => n.status === 'UNREAD').length;

  const fmtTime = (iso: string) => new Date(iso).toLocaleString('en-IN', {
    day: '2-digit', month: 'short', hour: '2-digit', minute: '2-digit',
  });

  if (loading) return <div className="flex justify-center py-20"><Spinner size={32} /></div>;

  return (
    <div className="max-w-2xl mx-auto px-4 sm:px-6 py-6 flex flex-col gap-5">
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-lg font-semibold text-gray-800">Notifications</h1>
          <p className="text-sm text-gray-400 mt-0.5">
            {unreadCount > 0 ? `${unreadCount} unread` : 'All caught up'}
          </p>
        </div>
        {unreadCount > 0 && (
          <Button variant="outline" size="sm" onClick={markAllRead} disabled={marking}>
            {marking ? <Spinner size={12} /> : '✓ Mark all read'}
          </Button>
        )}
      </div>

      <Card>
        {notifications.length === 0 ? (
          <Empty message="No notifications yet." />
        ) : (
          <div className="flex flex-col gap-2">
            {notifications.map(n => (
              <div key={n.id}
                className={`flex gap-3 p-3 rounded-lg border-l-4 transition-all ${
                  TYPE_COLOR[n.type] ?? 'border-l-gray-300 bg-gray-50'
                } ${n.status === 'UNREAD' ? 'opacity-100' : 'opacity-60'}`}>
                <span className="text-xl flex-shrink-0 mt-0.5">
                  {TYPE_ICON[n.type] ?? '📢'}
                </span>
                <div className="flex-1 min-w-0">
                  <p className={`text-sm ${n.status === 'UNREAD' ? 'font-medium text-gray-800' : 'text-gray-600'}`}>
                    {n.message}
                  </p>
                  <p className="text-xs text-gray-400 mt-1">{fmtTime(n.createdAt)}</p>
                </div>
                {n.status === 'UNREAD' && (
                  <div className="w-2 h-2 rounded-full bg-teal-500 flex-shrink-0 mt-2" />
                )}
              </div>
            ))}
          </div>
        )}
      </Card>
    </div>
  );
};
