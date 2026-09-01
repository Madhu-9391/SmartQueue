import React, { useCallback, useEffect, useState } from 'react';
import { notifApi } from '../services/api';
import { Scene, GlassPanel, HoloMetric } from '../components/ThreeD';
import { Button, Empty, Spinner } from '../components/UI';

interface Notification {
  id: number;
  message: string;
  type: string;
  status: 'UNREAD' | 'READ';
  createdAt: string;
}

const ICON: Record<string, string> = {
  TOKEN_CALLED: '🔔',
  ETA_UPDATED: '⏰',
  DOCTOR_DELAYED: '⏳',
  APPOINTMENT_CANCELLED: '✕',
  GENERAL: '✦',
};

const formatSmartTime = (value: string) => {
  const date = new Date(value);

  if (Number.isNaN(date.getTime())) {
    return value;
  }

  return new Intl.DateTimeFormat('en-IN', {
    timeZone: 'Asia/Kolkata',
    day: '2-digit',
    month: 'short',
    year: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
    hour12: true,
  }).format(date);
};

export const NotificationsPage = () => {
  const [notifications, setNotifications] = useState<Notification[]>([]);
  const [loading, setLoading] = useState(true);
  const [marking, setMarking] = useState(false);
  const [busyId, setBusyId] = useState<number | null>(null);

  const fetchNotifications = useCallback(async () => {
    try {
      const res = await notifApi.getAll();
      const data = res.data?.data ?? [];

      setNotifications(data);
      return true;
    } catch (error) {
      console.error('Failed to fetch notifications:', error);
      return false;
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    void fetchNotifications();
  }, [fetchNotifications]);

  const markOneRead = async (id: number) => {
    setBusyId(id);

    try {
      await notifApi.markRead(id);

      // Always reload persisted server state.
      await fetchNotifications();
    } catch (error) {
      console.error(`Failed to mark notification ${id} as read:`, error);
    } finally {
      setBusyId(null);
    }
  };

  const markAllRead = async () => {
    setMarking(true);

    try {
      await notifApi.markAllRead();

      // Always reload persisted server state.
      await fetchNotifications();
    } catch (error) {
      console.error('Failed to mark all notifications as read:', error);
    } finally {
      setMarking(false);
    }
  };

  const unreadCount = notifications.filter(
    (n) => n.status === 'UNREAD'
  ).length;

  const readCount = notifications.length - unreadCount;

  if (loading) {
    return (
      <div className="min-h-[70vh] flex items-center justify-center">
        <Spinner size={32} />
      </div>
    );
  }

  return (
    <div className="max-w-6xl mx-auto px-4 sm:px-6 py-6 space-y-6">
      <Scene className="min-h-[190px]">
        <div className="relative z-10 flex flex-col lg:flex-row lg:items-end lg:justify-between gap-6">
          <div>
            <p className="text-[11px] uppercase tracking-[0.22em] text-cyan-200/70">
              SMARTQUEUE / SIGNAL CENTER
            </p>

            <h1 className="mt-2 text-3xl sm:text-4xl font-black tracking-tight">
              Notifications
            </h1>

            <p className="mt-2 text-sm text-slate-300 max-w-xl">
              Every queue event, delay and payment confirmation in one
              persistent timeline.
            </p>
          </div>

          <div className="grid grid-cols-2 gap-3 w-full lg:w-auto lg:min-w-[380px]">
            <HoloMetric
              label="Unread"
              value={unreadCount}
              detail={unreadCount ? 'Needs attention' : 'All clear'}
              tone="teal"
            />

            <HoloMetric
              label="Read"
              value={readCount}
              detail="Already processed"
              tone="blue"
            />
          </div>
        </div>
      </Scene>

      <GlassPanel className="p-4 sm:p-5 rounded-3xl">
        <div className="flex items-center justify-between gap-4 mb-4">
          <div>
            <h2 className="font-bold text-slate-900">
              Activity stream
            </h2>

            <p className="text-xs text-slate-500 mt-1">
              Read state is stored server-side and survives refreshes.
            </p>
          </div>

          {unreadCount > 0 && (
            <Button
              variant="outline"
              size="sm"
              onClick={markAllRead}
              disabled={marking}
            >
              {marking ? (
                <Spinner size={12} />
              ) : (
                '✓ Mark all read'
              )}
            </Button>
          )}
        </div>

        {notifications.length === 0 ? (
          <Empty message="No notifications yet." />
        ) : (
          <div className="space-y-3">
            {notifications.map((n) => {
              const unread = n.status === 'UNREAD';

              return (
                <article
                  key={n.id}
                  className={`notif-card ${
                    unread ? 'notif-unread' : 'notif-read'
                  }`}
                >
                  <div
                    className={`w-11 h-11 rounded-2xl flex items-center justify-center text-lg ${
                      unread
                        ? 'bg-cyan-400/15 text-cyan-700'
                        : 'bg-slate-100 text-slate-400'
                    }`}
                  >
                    {ICON[n.type] ?? '✦'}
                  </div>

                  <div className="min-w-0 flex-1">
                    <div className="flex flex-wrap items-center gap-2">
                      <span
                        className={`text-[10px] font-bold uppercase tracking-wider ${
                          unread
                            ? 'text-cyan-700'
                            : 'text-slate-400'
                        }`}
                      >
                        {n.type.replaceAll('_', ' ')}
                      </span>

                      {unread && (
                        <span className="inline-flex w-2 h-2 rounded-full bg-cyan-500 shadow-[0_0_10px_rgba(6,182,212,.65)]" />
                      )}
                    </div>

                    <p
                      className={`mt-1 text-sm ${
                        unread
                          ? 'font-semibold text-slate-900'
                          : 'text-slate-600'
                      }`}
                    >
                      {n.message}
                    </p>

                    <p className="mt-1.5 text-xs text-slate-400">
                      {formatSmartTime(n.createdAt)} · IST
                    </p>
                  </div>

                  {unread && (
                    <button
                      onClick={() => markOneRead(n.id)}
                      disabled={busyId === n.id}
                      className="self-start text-xs font-semibold text-cyan-700 hover:text-cyan-900 disabled:opacity-50"
                    >
                      {busyId === n.id ? '...' : 'Mark read'}
                    </button>
                  )}

                  {!unread && (
                    <span className="text-[10px] font-semibold uppercase tracking-wider text-slate-400">
                      Read
                    </span>
                  )}
                </article>
              );
            })}
          </div>
        )}
      </GlassPanel>
    </div>
  );
};

