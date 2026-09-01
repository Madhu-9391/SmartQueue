package com.smartqueue.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartqueue.dto.*;
import com.smartqueue.entity.*;
import com.smartqueue.repository.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.*;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("Controller Integration Tests")
class ControllerIntegrationTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired UserRepository userRepo;
    @Autowired DoctorRepository doctorRepo;
    @Autowired PatientQueueRepository queueRepo;
    @Autowired PasswordEncoder passwordEncoder;

    private static String patientToken;
    private static String adminToken;
    private static Long queueId;

    @BeforeEach
    void seedOnce() {
        if (userRepo.count() > 0) return;

        userRepo.save(User.builder().name("Test Patient").email("tp@test.com")
                .password(passwordEncoder.encode("pass123")).role(User.Role.PATIENT).build());
        userRepo.save(User.builder().name("Test Admin").email("ta@test.com")
                .password(passwordEncoder.encode("pass123")).role(User.Role.ADMIN).build());

        Doctor doc = doctorRepo.save(Doctor.builder().name("Dr. Test")
                .specialization("General").avgConsultationTime(10).delayMinutes(0)
                .availabilityStatus(Doctor.AvailabilityStatus.AVAILABLE).build());

        PatientQueue q = queueRepo.save(PatientQueue.builder().queueName("Test OPD").doctor(doc)
                .status(PatientQueue.QueueStatus.ACTIVE).maxCapacity(50).currentToken(0).build());
        queueId = q.getId();
    }

    // ─── AUTH ─────────────────────────────────────────────────

    @Test @Order(1)
    @DisplayName("POST /api/auth/register → 200 with token")
    void registerReturnsToken() throws Exception {
        RegisterRequest req = new RegisterRequest(
                "New User", "newuser@test.com", "pass123", "+91 9", "PATIENT");

        mvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.token").isNotEmpty())
                .andExpect(jsonPath("$.data.role").value("PATIENT"));
    }

    @Test @Order(2)
    @DisplayName("POST /api/auth/register duplicate email → 409")
    void registerDuplicateEmail_returns409() throws Exception {
        RegisterRequest req = new RegisterRequest(
                "Dup User", "tp@test.com", "pass123", null, "PATIENT");

        mvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(req)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test @Order(3)
    @DisplayName("POST /api/auth/login → returns JWT and user info")
    void loginReturnsJwt() throws Exception {
        LoginRequest req = new LoginRequest("tp@test.com", "pass123");

        MvcResult result = mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.token").isNotEmpty())
                .andExpect(jsonPath("$.data.email").value("tp@test.com"))
                .andReturn();

        String body = result.getResponse().getContentAsString();
        patientToken = mapper.readTree(body).path("data").path("token").asText();

        // Admin token
        LoginRequest adminReq = new LoginRequest("ta@test.com", "pass123");
        MvcResult adminResult = mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(adminReq)))
                .andExpect(status().isOk()).andReturn();
        String adminBody = adminResult.getResponse().getContentAsString();
        adminToken = mapper.readTree(adminBody).path("data").path("token").asText();
    }

    @Test @Order(4)
    @DisplayName("POST /api/auth/login bad credentials → 401")
    void loginBadCredentials_returns401() throws Exception {
        LoginRequest req = new LoginRequest("tp@test.com", "wrongpass");

        mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(req)))
                .andExpect(status().isUnauthorized());
    }

    // ─── QUEUE ────────────────────────────────────────────────

    @Test @Order(5)
    @DisplayName("GET /api/queue/status/{id} → returns queue info")
    void getQueueStatus_returnsData() throws Exception {
        if (patientToken == null) return; // login test skipped

        mvc.perform(get("/api/queue/status/" + queueId)
                        .header("Authorization", "Bearer " + patientToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.queueId").value(queueId))
                .andExpect(jsonPath("$.data.doctorName").isNotEmpty());
    }

    @Test @Order(6)
    @DisplayName("GET /api/queue/status without token → 401")
    void getQueueStatus_unauthorized() throws Exception {
        mvc.perform(get("/api/queue/status/" + queueId))
                .andExpect(status().isUnauthorized());
    }

    // ─── APPOINTMENTS ─────────────────────────────────────────

    @Test @Order(7)
    @DisplayName("POST /api/appointments/book → books with AI prediction")
    void bookAppointment_returnsWithPrediction() throws Exception {
        if (patientToken == null || queueId == null) return;

        Doctor doc = doctorRepo.findAll().get(0);
        AppointmentRequest req = new AppointmentRequest(doc.getId(), queueId, null, "NORMAL");

        mvc.perform(post("/api/appointments/book")
                        .header("Authorization", "Bearer " + patientToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.tokenNumber").isNumber())
                .andExpect(jsonPath("$.data.status").value("WAITING"));
    }

    @Test @Order(8)
    @DisplayName("GET /api/appointments/my → returns patient appointments")
    void getMyAppointments_returnsList() throws Exception {
        if (patientToken == null) return;

        mvc.perform(get("/api/appointments/my")
                        .header("Authorization", "Bearer " + patientToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isArray());
    }

    // ─── ADMIN ────────────────────────────────────────────────

    @Test @Order(9)
    @DisplayName("GET /api/admin/analytics/dashboard → admin only returns data")
    void adminDashboard_returnsAnalytics() throws Exception {
        if (adminToken == null) return;

        mvc.perform(get("/api/admin/analytics/dashboard")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isNotEmpty());
    }

    @Test @Order(10)
    @DisplayName("GET /api/admin/analytics/dashboard as patient → 403")
    void adminDashboard_patientForbidden() throws Exception {
        if (patientToken == null) return;

        mvc.perform(get("/api/admin/analytics/dashboard")
                        .header("Authorization", "Bearer " + patientToken))
                .andExpect(status().isForbidden());
    }
}
