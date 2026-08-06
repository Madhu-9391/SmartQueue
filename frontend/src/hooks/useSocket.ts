import { useEffect, useRef, useCallback } from 'react';
import { Client, IMessage } from '@stomp/stompjs';
import SockJS from 'sockjs-client';

interface UseSocketOptions {
  queueId?: number;
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

  const connect = useCallback(() => {
    const client = new Client({
      webSocketFactory: () => new SockJS('/ws'),
      reconnectDelay: 5000,
      onConnect: () => {
        console.log('WebSocket connected');

        if (options.queueId) {
          client.subscribe(`/topic/queue/${options.queueId}`, (msg: IMessage) => {
            const event = JSON.parse(msg.body);
            options.onQueueUpdated?.(event.payload);
          });

          client.subscribe(`/topic/token-called/${options.queueId}`, (msg: IMessage) => {
            const event = JSON.parse(msg.body);
            options.onTokenCalled?.(event.payload);
          });

          client.subscribe(`/topic/eta-updated/${options.queueId}`, (msg: IMessage) => {
            const event = JSON.parse(msg.body);
            options.onEtaUpdated?.(event.payload);
          });
        }

        if (options.doctorId) {
          client.subscribe(`/topic/doctor-delayed/${options.doctorId}`, (msg: IMessage) => {
            const event = JSON.parse(msg.body);
            options.onDoctorDelayed?.(event.payload);
          });
        }

        if (options.userId) {
          client.subscribe(`/topic/notifications/${options.userId}`, (msg: IMessage) => {
            const event = JSON.parse(msg.body);
            options.onNotification?.(event.payload);
          });
        }
      },
      onDisconnect: () => console.log('WebSocket disconnected'),
      onStompError: (frame) => console.error('STOMP error:', frame),
    });

    client.activate();
    clientRef.current = client;
  }, [options.queueId, options.doctorId, options.userId]);

  useEffect(() => {
    connect();
    return () => { clientRef.current?.deactivate(); };
  }, [connect]);

  return { client: clientRef.current };
};
