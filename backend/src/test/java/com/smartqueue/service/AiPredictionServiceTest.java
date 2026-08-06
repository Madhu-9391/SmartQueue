package com.smartqueue.service;

import com.smartqueue.dto.PredictionResult;
import com.smartqueue.entity.*;
import com.smartqueue.repository.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("AiPredictionService Tests")
class AiPredictionServiceTest {

    @Mock ConsultationHistoryRepository historyRepo;
    @Mock AppointmentRepository appointmentRepo;
    @Mock DoctorRepository doctorRepo;

    @InjectMocks
    AiPredictionService predictionService;

    private Doctor doctor;
    private Queue queue;
    private User user;
    private Appointment appointment;

    @BeforeEach
    void setUp() {
        doctor = Doctor.builder()
                .id(1L).name("Dr. Test").specialization("Cardiology")
                .avgConsultationTime(15).delayMinutes(0).build();

        queue = Queue.builder()
                .id(1L).queueName("Test Queue")
                .doctor(doctor).status(Queue.QueueStatus.ACTIVE).build();

        user = User.builder()
                .id(1L).name("Test Patient").email("test@test.com").build();

        appointment = Appointment.builder()
                .id(1L).user(user).doctor(doctor).queue(queue)
                .tokenNumber(5).priority(Appointment.Priority.NORMAL)
                .status(Appointment.AppointmentStatus.WAITING).build();
    }

    // ─── predict() ────────────────────────────────────────────

    @Test
    @DisplayName("predict: returns non-null result with future time")
    void predict_returnsValidResult() {
        when(appointmentRepo.findWaitingByQueuePrioritized(1L))
                .thenReturn(List.of(appointment));
        when(historyRepo.avgDurationByDoctorAndTimeSlot(anyLong(), anyInt(), anyInt()))
                .thenReturn(null);
        when(historyRepo.countEmergencyInterruptionsSince(anyLong(), any()))
                .thenReturn(0L);

        PredictionResult result = predictionService.predict(appointment);

        assertThat(result).isNotNull();
        assertThat(result.getPredictedVisitTime()).isAfter(LocalDateTime.now());
        assertThat(result.getEstimatedWaitMinutes()).isGreaterThan(0);
        assertThat(result.getConfidenceMinutes()).isGreaterThanOrEqualTo(3);
        assertThat(result.getModelConfidenceScore()).isBetween(0.5, 1.0);
    }

    @Test
    @DisplayName("predict: emergency priority at position 1 gives minimal wait")
    void predict_emergencyPositionOne_minimalWait() {
        appointment.setPriority(Appointment.Priority.EMERGENCY);

        Appointment emergAppt = Appointment.builder()
                .id(1L).user(user).doctor(doctor).queue(queue)
                .tokenNumber(1).priority(Appointment.Priority.EMERGENCY)
                .status(Appointment.AppointmentStatus.WAITING).build();

        when(appointmentRepo.findWaitingByQueuePrioritized(1L))
                .thenReturn(List.of(emergAppt));
        when(historyRepo.avgDurationByDoctorAndTimeSlot(anyLong(), anyInt(), anyInt()))
                .thenReturn(null);
        when(historyRepo.countEmergencyInterruptionsSince(anyLong(), any()))
                .thenReturn(0L);

        PredictionResult result = predictionService.predict(emergAppt);

        // position=1 → base wait = 0 × speed = 0, so wait should be minimal
        assertThat(result.getEstimatedWaitMinutes()).isLessThanOrEqualTo(10);
    }

    @Test
    @DisplayName("predict: doctor delay is added to prediction")
    void predict_doctorDelay_addsToWait() {
        when(appointmentRepo.findWaitingByQueuePrioritized(1L))
                .thenReturn(List.of(appointment));
        when(historyRepo.avgDurationByDoctorAndTimeSlot(anyLong(), anyInt(), anyInt()))
                .thenReturn(null);
        when(historyRepo.countEmergencyInterruptionsSince(anyLong(), any()))
                .thenReturn(0L);

        // Baseline
        PredictionResult noDelay = predictionService.predict(appointment);

        // Add delay
        doctor.setDelayMinutes(20);
        PredictionResult withDelay = predictionService.predict(appointment);

        assertThat(withDelay.getEstimatedWaitMinutes())
                .isGreaterThan(noDelay.getEstimatedWaitMinutes());
    }

    @Test
    @DisplayName("predict: historical avg overrides doctor avg consultation time")
    void predict_historicalAvgUsedWhenAvailable() {
        // Historical avg is much lower than doctor default (15m)
        when(historyRepo.avgDurationByDoctorAndTimeSlot(anyLong(), anyInt(), anyInt()))
                .thenReturn(7.0);
        when(historyRepo.countEmergencyInterruptionsSince(anyLong(), any()))
                .thenReturn(0L);
        when(appointmentRepo.findWaitingByQueuePrioritized(1L))
                .thenReturn(List.of(appointment));

        PredictionResult result = predictionService.predict(appointment);

        // With historicalAvg=7 and position=1 → baseWait=0, so very short
        assertThat(result.getPredictionBasis()).contains("7.0");
    }

    @Test
    @DisplayName("predict: emergencies in queue add penalty time")
    void predict_emergencyInterruptions_addPenalty() {
        when(appointmentRepo.findWaitingByQueuePrioritized(1L))
                .thenReturn(List.of(appointment));
        when(historyRepo.avgDurationByDoctorAndTimeSlot(anyLong(), anyInt(), anyInt()))
                .thenReturn(null);

        when(historyRepo.countEmergencyInterruptionsSince(anyLong(), any()))
                .thenReturn(0L);
        PredictionResult noEmerg = predictionService.predict(appointment);

        when(historyRepo.countEmergencyInterruptionsSince(anyLong(), any()))
                .thenReturn(3L);
        PredictionResult withEmerg = predictionService.predict(appointment);

        assertThat(withEmerg.getEstimatedWaitMinutes())
                .isGreaterThan(noEmerg.getEstimatedWaitMinutes());
    }

    @Test
    @DisplayName("predict: confidence grows with queue depth")
    void predict_confidenceGrowsWithQueueDepth() {
        // Position 1
        Appointment earlyAppt = Appointment.builder()
                .id(10L).user(user).doctor(doctor).queue(queue)
                .tokenNumber(1).priority(Appointment.Priority.NORMAL)
                .status(Appointment.AppointmentStatus.WAITING).build();

        when(appointmentRepo.findWaitingByQueuePrioritized(1L))
                .thenReturn(List.of(earlyAppt));
        when(historyRepo.avgDurationByDoctorAndTimeSlot(anyLong(), anyInt(), anyInt()))
                .thenReturn(null);
        when(historyRepo.countEmergencyInterruptionsSince(anyLong(), any()))
                .thenReturn(0L);

        PredictionResult early = predictionService.predict(earlyAppt);

        // Position 15 (deep in queue)
        Appointment lateAppt = Appointment.builder()
                .id(11L).user(user).doctor(doctor).queue(queue)
                .tokenNumber(15).priority(Appointment.Priority.NORMAL)
                .status(Appointment.AppointmentStatus.WAITING).build();

        when(appointmentRepo.findWaitingByQueuePrioritized(1L))
                .thenReturn(List.of(earlyAppt, earlyAppt, earlyAppt,
                        earlyAppt, earlyAppt, earlyAppt, earlyAppt,
                        earlyAppt, earlyAppt, earlyAppt, earlyAppt,
                        earlyAppt, earlyAppt, earlyAppt, lateAppt));

        PredictionResult late = predictionService.predict(lateAppt);

        assertThat(late.getConfidenceMinutes())
                .isGreaterThanOrEqualTo(early.getConfidenceMinutes());
    }

    @Test
    @DisplayName("predict: result never returns negative wait minutes")
    void predict_neverNegativeWait() {
        // Even if no-show saving is large, result clamped to ≥2
        when(appointmentRepo.findWaitingByQueuePrioritized(1L))
                .thenReturn(List.of(appointment));
        when(historyRepo.avgDurationByDoctorAndTimeSlot(anyLong(), anyInt(), anyInt()))
                .thenReturn(1.0); // very fast doctor
        when(historyRepo.countEmergencyInterruptionsSince(anyLong(), any()))
                .thenReturn(0L);

        PredictionResult result = predictionService.predict(appointment);
        assertThat(result.getEstimatedWaitMinutes()).isGreaterThanOrEqualTo(2);
    }

    // ─── recalculateQueuePredictions() ────────────────────────

    @Test
    @DisplayName("recalculate: updates predictedVisitTime on all waiting appointments")
    void recalculate_updatesAllWaiting() {
        Appointment a1 = Appointment.builder().id(1L).user(user).doctor(doctor).queue(queue)
                .tokenNumber(1).priority(Appointment.Priority.NORMAL)
                .status(Appointment.AppointmentStatus.WAITING).build();
        Appointment a2 = Appointment.builder().id(2L).user(user).doctor(doctor).queue(queue)
                .tokenNumber(2).priority(Appointment.Priority.NORMAL)
                .status(Appointment.AppointmentStatus.WAITING).build();

        when(appointmentRepo.findWaitingByQueuePrioritized(1L))
                .thenReturn(List.of(a1, a2));
        when(historyRepo.avgDurationByDoctorAndTimeSlot(anyLong(), anyInt(), anyInt()))
                .thenReturn(null);
        when(historyRepo.countEmergencyInterruptionsSince(anyLong(), any()))
                .thenReturn(0L);

        List<Appointment> updated = predictionService.recalculateQueuePredictions(1L);

        assertThat(updated).hasSize(2);
        updated.forEach(a -> {
            assertThat(a.getPredictedVisitTime()).isNotNull();
            assertThat(a.getPredictionConfidence()).isNotNull();
            assertThat(a.getLastPredictionUpdated()).isNotNull();
        });
    }

    @Test
    @DisplayName("recalculate: later tokens get later predicted times")
    void recalculate_laterTokensGetLaterTimes() {
        Appointment a1 = Appointment.builder().id(1L).user(user).doctor(doctor).queue(queue)
                .tokenNumber(1).priority(Appointment.Priority.NORMAL)
                .status(Appointment.AppointmentStatus.WAITING).build();
        Appointment a2 = Appointment.builder().id(2L).user(user).doctor(doctor).queue(queue)
                .tokenNumber(2).priority(Appointment.Priority.NORMAL)
                .status(Appointment.AppointmentStatus.WAITING).build();

        when(appointmentRepo.findWaitingByQueuePrioritized(1L))
                .thenReturn(List.of(a1, a2));
        when(historyRepo.avgDurationByDoctorAndTimeSlot(anyLong(), anyInt(), anyInt()))
                .thenReturn(null);
        when(historyRepo.countEmergencyInterruptionsSince(anyLong(), any()))
                .thenReturn(0L);

        List<Appointment> updated = predictionService.recalculateQueuePredictions(1L);

        assertThat(updated.get(1).getPredictedVisitTime())
                .isAfterOrEqualTo(updated.get(0).getPredictedVisitTime());
    }
}
