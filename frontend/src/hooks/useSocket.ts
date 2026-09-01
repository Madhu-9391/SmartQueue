import { useEffect, useMemo, useRef, useCallback } from 'react';
import { Client, IMessage } from '@stomp/stompjs';
import SockJS from 'sockjs-client';

declare global {
  interface ImportMetaEnv {
    readonly VITE_WS_BASE_URL: string;
  }
  interface ImportMeta {
    readonly env: ImportMetaEnv;
  }
}

interface UseSocketOptions {
  queueId?: number;
  queueIds?: number[];
  doctorId?: number;
  userId?: number;
  onQueueUpdated?: (data: any) => void;
  onTokenCalled?: (data: any) => void;
  onEtaUpdated?: (data: any) => void;
  onDoctorDelayed?: (data: any) => void;
  onNotification?: (data: any) => void;
}

export const useSocket = (options: UseSocketOptions) => {
  const clientRef = useRef<Client | null>(null);
  const callbacksRef = useRef(options);
  callbacksRef.current = options;

  const queueIds = useMemo(() => {
    const ids = options.queueIds ?? (options.queueId != null ? [options.queueId] : []);
    return [...new Set(ids.filter((id): id is number => Number.isFinite(id)))].sort((a, b) => a - b);
  }, [options.queueId, options.queueIds?.join(',')]);
  const queueKey = queueIds.join(',');

  const connect = useCallback(() => {
    const wsBaseUrl = import.meta.env.VITE_WS_BASE_URL;

if (!wsBaseUrl) {
  throw new Error('VITE_WS_BASE_URL is not configured');
}

const client = new Client({
  webSocketFactory: () => new SockJS(wsBaseUrl),
  reconnectDelay: 3000,
  heartbeatIncoming: 10000,
  heartbeatOutgoing: 10000,
  debug: () => {},

      onConnect: () => {
        const parse = (msg: IMessage) => {
          try { return JSON.parse(msg.body)?.payload; } catch { return undefined; }
        };
        const current = callbacksRef.current;

        queueIds.forEach((queueId) => {
          client.subscribe(`/topic/queue/${queueId}`, (msg) => current.onQueueUpdated?.(parse(msg)));
          client.subscribe(`/topic/token-called/${queueId}`, (msg) => current.onTokenCalled?.(parse(msg)));
          client.subscribe(`/topic/eta-updated/${queueId}`, (msg) => current.onEtaUpdated?.(parse(msg)));
        });

        if (current.doctorId != null) {
          client.subscribe(`/topic/doctor-delayed/${current.doctorId}`, (msg) => current.onDoctorDelayed?.(parse(msg)));
        }
        if (current.userId != null) {
          client.subscribe(`/topic/notifications/${current.userId}`, (msg) => current.onNotification?.(parse(msg)));
        }
      },
      onStompError: (frame) => console.error('STOMP error', frame.headers?.message ?? 'unknown'),
    });

    client.activate();
    clientRef.current = client;
  }, [queueKey, options.doctorId, options.userId]);

  useEffect(() => {
    connect();
    return () => {
      const client = clientRef.current;
      clientRef.current = null;
      void client?.deactivate();
    };
  }, [connect]);

  return { client: clientRef.current };
};
