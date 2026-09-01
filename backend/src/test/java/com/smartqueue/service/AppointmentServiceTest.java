package com.smartqueue.service;

import com.smartqueue.dto.*;
import com.smartqueue.entity.*;
import com.smartqueue.repository.*;
import com.smartqueue.websocket.SocketEventPublisher;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("AppointmentService Tests")
class AppointmentServiceTest {

    @Mock AppointmentRepository appointmentRepo;
    @Mock PatientQueueRepository queueRepo;
    @Mock UserRepository userRepo;
    @Mock DoctorRepository doctorRepo;
    @Mock AiPredictionService predictionService;
    @Mock NotificationDispatchService notifService;
    @Mock SocketEventPublisher publisher;

    @InjectMocks
    AppointmentService appointmentService;

    private User user;
    private Doctor doctor;
    private PatientQueue queue;

    @BeforeEach
    void setUp() {
        user = User.builder()
                .id(1L).name("Rahul").email("rahul@test.com")
                .role(User.Role.PATIENT).build();

        doctor = Doctor.builder()
                .id(1L).name("Dr. Nair").specialization("Cardiology")
                .avgConsultationTime(15).delayMinutes(0).build();

        queue = PatientQueue.builder()
                .id(1L).queueName("Cardiology OPD").doctor(doctor)
                .status(PatientQueue.QueueStatus.ACTIVE).maxCapacity(50).build();
    }

    @Test
    @DisplayName("bookAppointment: assigns a unique token number after the highest live token")
    void book_assignsCorrectTokenNumber() {
        when(userRepo.findByEmail("rahul@test.com")).thenReturn(Optional.of(user));
        when(doctorRepo.findById(1L)).thenReturn(Optional.of(doctor));
        when(queueRepo.findByIdForUpdate(1L)).thenReturn(Optional.of(queue));
        when(appointmentRepo.countLiveByQueueId(1L)).thenReturn(4L);
        when(appointmentRepo.findMaxLiveTokenByQueueId(1L)).thenReturn(4);

        PredictionResult pred = PredictionResult.builder()
                .predictedVisitTime(LocalDateTime.now().plusMinutes(60))
                .confidenceMinutes(7).estimatedWaitMinutes(60)
                .modelConfidenceScore(0.88).build();
        when(predictionService.predict(any())).thenReturn(pred);

        ArgumentCaptor<Appointment> captor = ArgumentCaptor.forClass(Appointment.class);
        when(appointmentRepo.save(captor.capture())).thenAnswer(inv -> {
            Appointment a = inv.getArgument(0);
            a.setId(99L);
            return a;
        });

        AppointmentRequest req = new AppointmentRequest(1L, 1L, null, "NORMAL");
        AppointmentResponse resp = appointmentService.bookAppointment(req, "rahul@test.com");

        // Token follows the highest live token = 5
        assertThat(captor.getValue().getTokenNumber()).isEqualTo(5);
        assertThat(resp.getTokenNumber()).isEqualTo(5);
    }

    @Test
    @DisplayName("bookAppointment: throws when queue is at max capacity")
    void book_throwsWhenQueueFull() {
        queue.setMaxCapacity(10);
        when(userRepo.findByEmail("rahul@test.com")).thenReturn(Optional.of(user));
        when(doctorRepo.findById(1L)).thenReturn(Optional.of(doctor));
        when(queueRepo.findByIdForUpdate(1L)).thenReturn(Optional.of(queue));
        when(appointmentRepo.countLiveByQueueId(1L)).thenReturn(10L); // at capacity

        AppointmentRequest req = new AppointmentRequest(1L, 1L, null, "NORMAL");

        assertThatThrownBy(() -> appointmentService.bookAppointment(req, "rahul@test.com"))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("capacity");
    }

    @Test
    @DisplayName("bookAppointment: EMERGENCY priority is set correctly")
    void book_setsEmergencyPriority() {
        when(userRepo.findByEmail("rahul@test.com")).thenReturn(Optional.of(user));
        when(doctorRepo.findById(1L)).thenReturn(Optional.of(doctor));
        when(queueRepo.findByIdForUpdate(1L)).thenReturn(Optional.of(queue));
        when(appointmentRepo.countLiveByQueueId(1L)).thenReturn(2L);
        when(appointmentRepo.findMaxLiveTokenByQueueId(1L)).thenReturn(2);

        PredictionResult pred = PredictionResult.builder()
                .predictedVisitTime(LocalDateTime.now().plusMinutes(5))
                .confidenceMinutes(3).estimatedWaitMinutes(5)
                .modelConfidenceScore(0.95).build();
        when(predictionService.predict(any())).thenReturn(pred);

        ArgumentCaptor<Appointment> captor = ArgumentCaptor.forClass(Appointment.class);
        when(appointmentRepo.save(captor.capture())).thenAnswer(inv -> {
            Appointment a = inv.getArgument(0);
            a.setId(10L);
            return a;
        });

        AppointmentRequest req = new AppointmentRequest(1L, 1L, null, "EMERGENCY");
        appointmentService.bookAppointment(req, "rahul@test.com");

        assertThat(captor.getValue().getPriority()).isEqualTo(Appointment.Priority.EMERGENCY);
    }

    @Test
    @DisplayName("bookAppointment: AI prediction is stored on appointment")
    void book_storesPrediction() {
        when(userRepo.findByEmail("rahul@test.com")).thenReturn(Optional.of(user));
        when(doctorRepo.findById(1L)).thenReturn(Optional.of(doctor));
        when(queueRepo.findByIdForUpdate(1L)).thenReturn(Optional.of(queue));
        when(appointmentRepo.countLiveByQueueId(1L)).thenReturn(0L);
        when(appointmentRepo.findMaxLiveTokenByQueueId(1L)).thenReturn(0);

        LocalDateTime predictedTime = LocalDateTime.now().plusMinutes(20);
        PredictionResult pred = PredictionResult.builder()
                .predictedVisitTime(predictedTime)
                .confidenceMinutes(6).estimatedWaitMinutes(20)
                .modelConfidenceScore(0.91).build();
        when(predictionService.predict(any())).thenReturn(pred);

        ArgumentCaptor<Appointment> captor = ArgumentCaptor.forClass(Appointment.class);
        when(appointmentRepo.save(captor.capture())).thenAnswer(inv -> {
            Appointment a = inv.getArgument(0);
            a.setId(5L);
            return a;
        });

        AppointmentRequest req = new AppointmentRequest(1L, 1L, null, "NORMAL");
        AppointmentResponse resp = appointmentService.bookAppointment(req, "rahul@test.com");

        assertThat(resp.getPredictedVisitTime()).isNotNull();
        assertThat(resp.getPredictionConfidence()).isEqualTo(6);
        assertThat(resp.getEstimatedWaitMinutes()).isEqualTo(20);
    }

    @Test
    @DisplayName("bookAppointment: SocketEvent and Notification are published")
    void book_publishesEventsAndNotifications() {
        when(userRepo.findByEmail("rahul@test.com")).thenReturn(Optional.of(user));
        when(doctorRepo.findById(1L)).thenReturn(Optional.of(doctor));
        when(queueRepo.findByIdForUpdate(1L)).thenReturn(Optional.of(queue));
        when(appointmentRepo.countLiveByQueueId(1L)).thenReturn(1L);
        when(appointmentRepo.findMaxLiveTokenByQueueId(1L)).thenReturn(1);
        when(predictionService.predict(any())).thenReturn(PredictionResult.builder()
                .predictedVisitTime(LocalDateTime.now().plusMinutes(15))
                .confidenceMinutes(5).estimatedWaitMinutes(15)
                .modelConfidenceScore(0.89).build());
        when(appointmentRepo.save(any())).thenAnswer(inv -> {
            Appointment a = inv.getArgument(0);
            a.setId(7L);
            return a;
        });

        AppointmentRequest req = new AppointmentRequest(1L, 1L, null, "NORMAL");
        appointmentService.bookAppointment(req, "rahul@test.com");

        verify(publisher, times(1)).publishQueueUpdated(1L);
        verify(notifService, times(1)).notifyUser(
                eq(1L), anyString(), eq(Notification.NotificationType.GENERAL));
    }

    @Test
    @DisplayName("cancelAppointment: sets status to CANCELLED")
    void cancel_setsStatusCancelled() {
        Appointment appt = Appointment.builder()
                .id(1L).user(user).doctor(doctor).queue(queue)
                .tokenNumber(3).priority(Appointment.Priority.NORMAL)
                .status(Appointment.AppointmentStatus.WAITING).build();

        when(appointmentRepo.findById(1L)).thenReturn(Optional.of(appt));
        when(appointmentRepo.save(any())).thenReturn(appt);
        when(predictionService.recalculateQueuePredictions(anyLong())).thenReturn(List.of());

        appointmentService.cancelAppointment(1L, "rahul@test.com", "Patient requested cancellation");

        assertThat(appt.getStatus()).isEqualTo(Appointment.AppointmentStatus.CANCELLED);
        verify(publisher, times(1)).publishQueueUpdated(1L);
    }

    @Test
    @DisplayName("cancelAppointment: throws when appointment not found")
    void cancel_throwsWhenNotFound() {
        when(appointmentRepo.findById(999L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> appointmentService.cancelAppointment(999L, "rahul@test.com", "Patient requested cancellation"))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("not found");
    }

    @Test
    @DisplayName("getMyAppointments: returns list for authenticated user")
    void getMyAppointments_returnsList() {
        Appointment appt = Appointment.builder()
                .id(1L).user(user).doctor(doctor).queue(queue)
                .tokenNumber(1).priority(Appointment.Priority.NORMAL)
                .status(Appointment.AppointmentStatus.WAITING).build();

        when(userRepo.findByEmail("rahul@test.com")).thenReturn(Optional.of(user));
        when(appointmentRepo.findByUserIdOrderByCreatedAtDesc(1L)).thenReturn(List.of(appt));

        List<AppointmentResponse> result = appointmentService.getMyAppointments("rahul@test.com");

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getDoctorName()).isEqualTo("Dr. Nair");
    }
}
