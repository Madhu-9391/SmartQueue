package com.smartqueue.config;

import com.smartqueue.entity.*;
import com.smartqueue.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.*;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDateTime;
import java.util.List;

@Configuration @RequiredArgsConstructor @Slf4j
public class DataSeeder {

    private final PasswordEncoder passwordEncoder;

    @Bean
    @Profile("!prod")
    public CommandLineRunner seedData(
            UserRepository userRepo, DoctorRepository doctorRepo,
            DepartmentRepository departmentRepo, PatientQueueRepository queueRepo,
            AppointmentRepository apptRepo) {
        return args -> {
            if (userRepo.count() > 0) { log.info("Data already seeded."); return; }
            log.info("Seeding demo data...");

            userRepo.save(User.builder().name("Admin User").email("admin@demo.com").password(passwordEncoder.encode("password")).role(User.Role.ADMIN).phone("+91 99000 00001").build());
            User p1 = userRepo.save(User.builder().name("Rahul Sharma").email("patient@demo.com").password(passwordEncoder.encode("password")).role(User.Role.PATIENT).phone("+91 98765 43210").build());
            User p2 = userRepo.save(User.builder().name("Priya Singh").email("priya@demo.com").password(passwordEncoder.encode("password")).role(User.Role.PATIENT).phone("+91 98765 43211").build());
            User p3 = userRepo.save(User.builder().name("Arjun Mehta").email("arjun@demo.com").password(passwordEncoder.encode("password")).role(User.Role.PATIENT).phone("+91 98765 43212").build());

            Department cardio = departmentRepo.save(Department.builder().name("Cardiology").hospitalName("City Hospital").floor("2nd").build());
            Department general = departmentRepo.save(Department.builder().name("General").hospitalName("City Hospital").floor("Ground").build());

            Doctor dr1 = doctorRepo.save(Doctor.builder().name("Dr. Priya Nair").specialization("Cardiologist").avgConsultationTime(14).roomNumber("OPD-3").availabilityStatus(Doctor.AvailabilityStatus.AVAILABLE).delayMinutes(0).build());
            Doctor dr2 = doctorRepo.save(Doctor.builder().name("Dr. Arun Mehta").specialization("General Physician").avgConsultationTime(8).roomNumber("OPD-1").availabilityStatus(Doctor.AvailabilityStatus.AVAILABLE).delayMinutes(0).build());
            Doctor dr3 = doctorRepo.save(Doctor.builder().name("Dr. Sunita Rao").specialization("Neurologist").avgConsultationTime(18).roomNumber("OPD-5").availabilityStatus(Doctor.AvailabilityStatus.AVAILABLE).delayMinutes(0).build());
            Doctor dr4 = doctorRepo.save(Doctor.builder().name("Dr. Kiran Patel").specialization("Orthopedist").avgConsultationTime(12).roomNumber("OPD-2").availabilityStatus(Doctor.AvailabilityStatus.AVAILABLE).delayMinutes(0).build());

            PatientQueue q1 = queueRepo.save(PatientQueue.builder().queueName("Cardiology OPD").doctor(dr1).department(cardio).status(PatientQueue.QueueStatus.ACTIVE).currentToken(0).maxCapacity(50).build());
            PatientQueue q2 = queueRepo.save(PatientQueue.builder().queueName("General OPD").doctor(dr2).department(general).status(PatientQueue.QueueStatus.ACTIVE).currentToken(0).maxCapacity(50).build());
            PatientQueue q3 = queueRepo.save(PatientQueue.builder().queueName("Neurology OPD").doctor(dr3).status(PatientQueue.QueueStatus.ACTIVE).currentToken(0).maxCapacity(30).build());
            PatientQueue q4 = queueRepo.save(PatientQueue.builder().queueName("Ortho OPD").doctor(dr4).status(PatientQueue.QueueStatus.ACTIVE).currentToken(0).maxCapacity(30).build());

            LocalDateTime now = LocalDateTime.now();
            apptRepo.saveAll(List.of(
                Appointment.builder().user(p1).doctor(dr1).queue(q1).tokenNumber(1).priority(Appointment.Priority.EMERGENCY).status(Appointment.AppointmentStatus.ACTIVE).appointmentDate(now).predictedVisitTime(now.plusMinutes(2)).predictionConfidence(3).lastPredictionUpdated(now).paymentRequired(false).paymentStatus(Payment.PaymentStatus.PAID).rescheduleCount(0).actualStartTime(now).build(),
                Appointment.builder().user(p2).doctor(dr1).queue(q1).tokenNumber(2).priority(Appointment.Priority.VIP).status(Appointment.AppointmentStatus.WAITING).appointmentDate(now).predictedVisitTime(now.plusMinutes(16)).predictionConfidence(5).lastPredictionUpdated(now).paymentRequired(false).paymentStatus(Payment.PaymentStatus.PAID).rescheduleCount(0).build(),
                Appointment.builder().user(p3).doctor(dr1).queue(q1).tokenNumber(3).priority(Appointment.Priority.NORMAL).status(Appointment.AppointmentStatus.WAITING).appointmentDate(now).predictedVisitTime(now.plusMinutes(30)).predictionConfidence(7).lastPredictionUpdated(now).paymentRequired(false).paymentStatus(Payment.PaymentStatus.PAID).rescheduleCount(0).build()
            ));

            log.info("✅ Demo data seeded:");
            log.info("  admin@demo.com / password  (role: ADMIN)");
            log.info("  patient@demo.com / password (role: PATIENT)");
            log.info("  Queues: Cardiology, General, Neurology, Ortho");
        };
    }
}
