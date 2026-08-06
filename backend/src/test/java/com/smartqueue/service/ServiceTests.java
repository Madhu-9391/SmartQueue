package com.smartqueue.service;

import com.smartqueue.dto.*;
import com.smartqueue.entity.*;
import com.smartqueue.repository.*;
import com.smartqueue.security.JwtUtil;
import com.smartqueue.websocket.SocketEventPublisher;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.*;
import org.springframework.security.core.userdetails.*;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDateTime;
import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

// ============================================================
//  AUTH SERVICE TESTS
// ============================================================
@ExtendWith(MockitoExtension.class)
@DisplayName("AuthService Tests")
class AuthServiceTest {

    @Mock UserRepository userRepo;
    @Mock PasswordEncoder passwordEncoder;
    @Mock AuthenticationManager authManager;
    @Mock JwtUtil jwtUtil;
    @Mock UserDetailsService userDetailsService;

    @InjectMocks
    AuthService authService;

    @Test
    @DisplayName("register: creates user and returns JWT token")
    void register_createsUserAndReturnsToken() {
        RegisterRequest req = new RegisterRequest("Alice", "alice@test.com", "pass123", "+91 9000", "PATIENT");

        when(userRepo.existsByEmail("alice@test.com")).thenReturn(false);
        when(passwordEncoder.encode("pass123")).thenReturn("hashed");

        User savedUser = User.builder().id(1L).name("Alice").email("alice@test.com")
                .password("hashed").role(User.Role.PATIENT).build();
        when(userRepo.save(any())).thenReturn(savedUser);

        UserDetails mockDetails = org.springframework.security.core.userdetails.User
                .withUsername("alice@test.com").password("hashed").roles("PATIENT").build();
        when(userDetailsService.loadUserByUsername("alice@test.com")).thenReturn(mockDetails);
        when(jwtUtil.generateToken(any())).thenReturn("mock.jwt.token");

        AuthResponse resp = authService.register(req);

        assertThat(resp.getToken()).isEqualTo("mock.jwt.token");
        assertThat(resp.getEmail()).isEqualTo("alice@test.com");
        assertThat(resp.getRole()).isEqualTo("PATIENT");
        verify(userRepo, times(1)).save(any());
    }

    @Test
    @DisplayName("register: throws when email already exists")
    void register_throwsOnDuplicateEmail() {
        RegisterRequest req = new RegisterRequest("Bob", "bob@test.com", "pass", "+91 9", "PATIENT");
        when(userRepo.existsByEmail("bob@test.com")).thenReturn(true);

        assertThatThrownBy(() -> authService.register(req))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("already registered");
    }

    @Test
    @DisplayName("register: defaults to PATIENT role when role is null")
    void register_defaultsToPatientRole() {
        RegisterRequest req = new RegisterRequest("Carol", "carol@test.com", "pass123", null, null);
        when(userRepo.existsByEmail("carol@test.com")).thenReturn(false);
        when(passwordEncoder.encode(any())).thenReturn("hashed");

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        User saved = User.builder().id(2L).name("Carol").email("carol@test.com")
                .password("hashed").role(User.Role.PATIENT).build();
        when(userRepo.save(captor.capture())).thenReturn(saved);

        UserDetails mockDetails = org.springframework.security.core.userdetails.User
                .withUsername("carol@test.com").password("hashed").roles("PATIENT").build();
        when(userDetailsService.loadUserByUsername("carol@test.com")).thenReturn(mockDetails);
        when(jwtUtil.generateToken(any())).thenReturn("tok");

        authService.register(req);

        assertThat(captor.getValue().getRole()).isEqualTo(User.Role.PATIENT);
    }

    @Test
    @DisplayName("login: returns token on valid credentials")
    void login_returnsToken() {
        LoginRequest req = new LoginRequest("alice@test.com", "pass123");

        User user = User.builder().id(1L).name("Alice").email("alice@test.com")
                .password("hashed").role(User.Role.PATIENT).build();
        when(userRepo.findByEmail("alice@test.com")).thenReturn(Optional.of(user));

        UserDetails mockDetails = org.springframework.security.core.userdetails.User
                .withUsername("alice@test.com").password("hashed").roles("PATIENT").build();
        when(userDetailsService.loadUserByUsername("alice@test.com")).thenReturn(mockDetails);
        when(jwtUtil.generateToken(any())).thenReturn("login.jwt");

        AuthResponse resp = authService.login(req);

        assertThat(resp.getToken()).isEqualTo("login.jwt");
        assertThat(resp.getName()).isEqualTo("Alice");
        verify(authManager, times(1)).authenticate(any());
    }

    @Test
    @DisplayName("login: throws when user not found")
    void login_throwsWhenNotFound() {
        LoginRequest req = new LoginRequest("ghost@test.com", "pass");
        when(userRepo.findByEmail("ghost@test.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.login(req))
                .isInstanceOf(RuntimeException.class);
    }
}

// ============================================================
//  QUEUE SERVICE TESTS
// ============================================================
@ExtendWith(MockitoExtension.class)
@DisplayName("QueueService Tests")
class QueueServiceTest {

    @Mock QueueRepository queueRepo;
    @Mock AppointmentRepository appointmentRepo;
    @Mock DoctorRepository doctorRepo;
    @Mock AiPredictionService predictionService;
    @Mock SocketEventPublisher publisher;
    @Mock NotificationService notifService;

    @InjectMocks
    QueueService queueService;

    private Doctor doctor;
    private Queue queue;
    private User user;

    @BeforeEach
    void setUp() {
        doctor = Doctor.builder().id(1L).name("Dr. Nair")
                .avgConsultationTime(15).delayMinutes(0).build();
        queue  = Queue.builder().id(1L).queueName("OPD").doctor(doctor)
                .status(Queue.QueueStatus.ACTIVE).currentToken(0).maxCapacity(50).build();
        user   = User.builder().id(1L).name("Rahul").email("r@test.com").build();
    }

    @Test
    @DisplayName("createQueue: saves and returns new queue")
    void createQueue_savesQueue() {
        when(doctorRepo.findById(1L)).thenReturn(Optional.of(doctor));
        when(queueRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        QueueCreateRequest req = new QueueCreateRequest("New OPD", 1L, null, 30);
        Queue result = queueService.createQueue(req);

        assertThat(result.getQueueName()).isEqualTo("New OPD");
        assertThat(result.getMaxCapacity()).isEqualTo(30);
        assertThat(result.getStatus()).isEqualTo(Queue.QueueStatus.ACTIVE);
        verify(queueRepo, times(1)).save(any());
    }

    @Test
    @DisplayName("createQueue: defaults capacity to 50 when not provided")
    void createQueue_defaultCapacity() {
        when(doctorRepo.findById(1L)).thenReturn(Optional.of(doctor));
        when(queueRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        QueueCreateRequest req = new QueueCreateRequest("OPD", 1L, null, null);
        Queue result = queueService.createQueue(req);

        assertThat(result.getMaxCapacity()).isEqualTo(50);
    }

    @Test
    @DisplayName("createQueue: throws when doctor not found")
    void createQueue_throwsWhenDoctorMissing() {
        when(doctorRepo.findById(99L)).thenReturn(Optional.empty());
        QueueCreateRequest req = new QueueCreateRequest("OPD", 99L, null, null);

        assertThatThrownBy(() -> queueService.createQueue(req))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Doctor not found");
    }

    @Test
    @DisplayName("callNextToken: activates next waiting patient")
    void callNextToken_activatesNextPatient() {
        Appointment waiting = Appointment.builder()
                .id(1L).user(user).doctor(doctor).queue(queue)
                .tokenNumber(1).priority(Appointment.Priority.NORMAL)
                .status(Appointment.AppointmentStatus.WAITING).build();

        when(queueRepo.findById(1L)).thenReturn(Optional.of(queue));
        when(appointmentRepo.findWaitingByQueuePrioritized(1L))
                .thenReturn(List.of(waiting))
                .thenReturn(List.of());  // after activation
        when(appointmentRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(queueRepo.save(any())).thenReturn(queue);
        when(predictionService.recalculateQueuePredictions(1L)).thenReturn(List.of());
        when(appointmentRepo.saveAll(any())).thenReturn(List.of());

        AppointmentResponse resp = queueService.callNextToken(1L);

        assertThat(resp.getStatus()).isEqualTo("ACTIVE");
        assertThat(resp.getTokenNumber()).isEqualTo(1);
        verify(publisher, times(1)).publishTokenCalled(eq(1L), any());
        verify(publisher, times(1)).publishQueueUpdated(1L);
    }

    @Test
    @DisplayName("callNextToken: throws when queue has no waiting patients")
    void callNextToken_throwsWhenEmpty() {
        when(queueRepo.findById(1L)).thenReturn(Optional.of(queue));
        when(appointmentRepo.findWaitingByQueuePrioritized(1L)).thenReturn(List.of());

        assertThatThrownBy(() -> queueService.callNextToken(1L))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("No waiting");
    }

    @Test
    @DisplayName("getQueueStatus: returns correct waiting count")
    void getQueueStatus_returnsCorrectCount() {
        Appointment a1 = Appointment.builder().id(1L).user(user).doctor(doctor).queue(queue)
                .tokenNumber(1).priority(Appointment.Priority.NORMAL)
                .status(Appointment.AppointmentStatus.WAITING)
                .predictedVisitTime(LocalDateTime.now().plusMinutes(15))
                .predictionConfidence(5).build();
        Appointment a2 = Appointment.builder().id(2L).user(user).doctor(doctor).queue(queue)
                .tokenNumber(2).priority(Appointment.Priority.NORMAL)
                .status(Appointment.AppointmentStatus.WAITING)
                .predictedVisitTime(LocalDateTime.now().plusMinutes(30))
                .predictionConfidence(7).build();

        when(queueRepo.findById(1L)).thenReturn(Optional.of(queue));
        when(appointmentRepo.findWaitingByQueuePrioritized(1L)).thenReturn(List.of(a1, a2));

        QueueStatusResponse status = queueService.getQueueStatus(1L);

        assertThat(status.getTotalWaiting()).isEqualTo(2);
        assertThat(status.getAppointments()).hasSize(2);
        assertThat(status.getDoctorName()).isEqualTo("Dr. Nair");
    }
}
