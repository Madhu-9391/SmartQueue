// QueueSocket.tsx
import { useSocket } from "../hooks/useSocket";

interface Props {
  queueId: number;
  queueName: string;
  fetchData: () => void;
  addEvent: (msg: string, color: string) => void;
}

export default function QueueSocket({
  queueId,
  queueName,
  fetchData,
  addEvent,
}: Props) {
  useSocket({
    queueId,
    onQueueUpdated: () => {
      fetchData();
      addEvent(`Queue "${queueName}" updated`, "#0d9488");
    },
    onTokenCalled: (d) =>
      addEvent(`Token T-${d.tokenNumber} called — ${d.patientName}`, "#7c3aed"),
    onEtaUpdated: () =>
      addEvent("AI re-predicted ETAs", "#d97706"),
    onDoctorDelayed: (d) =>
      addEvent(`Doctor delayed ${d.delayMinutes} min`, "#dc2626"),
  });

  return null;
}