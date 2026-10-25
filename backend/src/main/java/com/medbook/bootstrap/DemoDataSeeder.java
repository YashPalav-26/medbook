package com.medbook.bootstrap;

import com.medbook.entity.Doctor;
import com.medbook.entity.DoctorAvailability;
import com.medbook.entity.Patient;
import com.medbook.entity.Specialty;
import com.medbook.entity.User;
import com.medbook.repository.AppointmentRepository;
import com.medbook.repository.DoctorAvailabilityRepository;
import com.medbook.repository.DoctorRepository;
import com.medbook.repository.PatientRepository;
import com.medbook.repository.SpecialtyRepository;
import com.medbook.service.AppointmentService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;






















@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
@ConditionalOnProperty(name = "medbook.demo.seed", havingValue = "true")
public class DemoDataSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DemoDataSeeder.class);



    static final String ADMIN_EMAIL = "admin@medbook.test";
    static final String ADMIN_PASSWORD = "Admin@123";

    static final String DOCTOR_EMAIL = "doctor@medbook.test";
    static final String DOCTOR_PASSWORD = "Doctor@123";

    static final String PATIENT_EMAIL = "patient@medbook.test";
    static final String PATIENT_PASSWORD = "Patient@123";

    private final BootstrapAccountService accountService;
    private final SpecialtyRepository specialtyRepository;
    private final DoctorRepository doctorRepository;
    private final PatientRepository patientRepository;
    private final DoctorAvailabilityRepository availabilityRepository;
    private final AppointmentRepository appointmentRepository;
    private final AppointmentService appointmentService;

    public DemoDataSeeder(BootstrapAccountService accountService,
                          SpecialtyRepository specialtyRepository,
                          DoctorRepository doctorRepository,
                          PatientRepository patientRepository,
                          DoctorAvailabilityRepository availabilityRepository,
                          AppointmentRepository appointmentRepository,
                          AppointmentService appointmentService) {
        this.accountService = accountService;
        this.specialtyRepository = specialtyRepository;
        this.doctorRepository = doctorRepository;
        this.patientRepository = patientRepository;
        this.availabilityRepository = availabilityRepository;
        this.appointmentRepository = appointmentRepository;
        this.appointmentService = appointmentService;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (accountService.adminExists()) {
            log.info("Demo seed: an administrator already exists, so demo admin is used only "
                    + "if {} is registered.", ADMIN_EMAIL);
        }
        try {
            accountService.ensureUser(ADMIN_EMAIL, ADMIN_PASSWORD, "Alex", "Morgan",
                    "+10000000001", BootstrapAccountService.ROLE_ADMIN);

            Specialty generalMedicine = ensureSpecialty("General Medicine",
                    "Primary care, check-ups and general consultations");
            ensureSpecialty("Cardiology", "Heart and cardiovascular care");

            Doctor doctor = ensureDoctor(generalMedicine);
            Patient patient = ensurePatient();
            ensureAvailability(doctor);
            ensureAppointments(doctor, patient);

            log.info("Demo seed complete. Demo accounts are documented in docs/demo-accounts.md");
        } catch (RuntimeException ex) {
            log.error("Demo seed failed (development only); continuing startup: {}", ex.getMessage());
        }
    }

    private Specialty ensureSpecialty(String name, String description) {
        return specialtyRepository.findByNameIgnoreCase(name)
                .orElseGet(() -> specialtyRepository.save(new Specialty(name, description)));
    }

    private Doctor ensureDoctor(Specialty specialty) {
        var user = accountService.ensureUser(DOCTOR_EMAIL, DOCTOR_PASSWORD, "Sarah", "Wells",
                        "+10000000002", "DOCTOR")
                .orElseThrow(() -> new IllegalStateException("Doctor account could not be created"));

        return doctorRepository.findByUserId(user.getId()).orElseGet(() -> {
            Doctor doctor = new Doctor(user, specialty, "DEMO-DOC-001", 10, new BigDecimal("500.00"));
            doctor.setBio("Board-certified general physician with a focus on preventive care.");
            return doctorRepository.save(doctor);
        });
    }

    private Patient ensurePatient() {
        User user = accountService.ensureUser(PATIENT_EMAIL, PATIENT_PASSWORD, "John", "Carter",
                        "+10000000003", "PATIENT")
                .orElseThrow(() -> new IllegalStateException("Patient account could not be created"));

        return patientRepository.findByUserId(user.getId())
                .orElseGet(() -> patientRepository.save(new Patient(user, LocalDate.of(1990, 1, 1))));
    }


    private void ensureAvailability(Doctor doctor) {
        if (!availabilityRepository.findByDoctorId(doctor.getId()).isEmpty()) {
            return;
        }
        for (byte day = 0; day <= 6; day++) {
            DoctorAvailability slot = new DoctorAvailability(
                    doctor, day, LocalTime.of(9, 0), LocalTime.of(17, 0));
            availabilityRepository.save(slot);
        }
    }

    private void ensureAppointments(Doctor doctor, Patient patient) {
        if (!appointmentRepository.findByDoctorId(doctor.getId()).isEmpty()) {
            return;
        }
        LocalDate today = LocalDate.now();
        appointmentService.createAppointment(patient.getId(), doctor.getId(),
                today, LocalTime.of(10, 0), LocalTime.of(10, 30), "Routine check-up");
        appointmentService.createAppointment(patient.getId(), doctor.getId(),
                today, LocalTime.of(11, 0), LocalTime.of(11, 30), "Follow-up consultation");
        appointmentService.createAppointment(patient.getId(), doctor.getId(),
                today.plusDays(2), LocalTime.of(9, 0), LocalTime.of(9, 30), "Blood pressure review");
    }
}
