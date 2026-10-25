package com.medbook.repository;

import com.medbook.entity.Doctor;
import com.medbook.entity.DoctorAvailability;
import com.medbook.entity.Role;
import com.medbook.entity.Specialty;
import com.medbook.entity.User;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;







@SpringBootTest
@Transactional
class DoctorAvailabilityRepositoryTest {

    @Autowired
    private DoctorAvailabilityRepository availabilityRepository;

    @Autowired
    private DoctorRepository doctorRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private SpecialtyRepository specialtyRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private EntityManager entityManager;




    private User createTestDoctorUser(String email) {
        Role doctorRole = roleRepository.findByName("DOCTOR")
                .orElseThrow(() -> new IllegalStateException("DOCTOR role not seeded"));
        User user = new User(email, "hashed-password", "Test", "Doctor", "+919876543210");
        user.addRole(doctorRole);
        return userRepository.save(user);
    }




    private Specialty createTestSpecialty(String name) {


        return specialtyRepository.save(new Specialty(name + "-" + System.nanoTime(), "Test specialty"));
    }




    private Doctor createTestDoctor(String email, String specialtyName) {
        User user = createTestDoctorUser(email);
        Specialty specialty = createTestSpecialty(specialtyName);
        Doctor doctor = new Doctor(user, specialty, "LIC-" + System.nanoTime(), 5, new BigDecimal("500.00"));
        return doctorRepository.save(doctor);
    }

    @Test
    void findAllExecutesAgainstRealSchema() {


        assertThat(availabilityRepository.findAll()).isNotNull();
    }

    @Test
    void savedAvailabilityIsRetrievableById() {
        Doctor doctor = createTestDoctor("doctor1@medbook.test", "Cardiology");
        DoctorAvailability availability = new DoctorAvailability(
                doctor,
                (byte) 1,
                LocalTime.of(9, 0),
                LocalTime.of(12, 0)
        );

        DoctorAvailability saved = availabilityRepository.save(availability);

        assertThat(saved.getId()).isNotNull();

        var reloaded = availabilityRepository.findById(saved.getId());

        assertThat(reloaded).isPresent();
        assertThat(reloaded.get().getDayOfWeek()).isEqualTo((byte) 1);
        assertThat(reloaded.get().getStartTime()).isEqualTo(LocalTime.of(9, 0));
        assertThat(reloaded.get().getEndTime()).isEqualTo(LocalTime.of(12, 0));
    }

    @Test
    void findByDoctorIdReturnsAllAvailabilityForDoctor() {
        Doctor doctor = createTestDoctor("doctor2@medbook.test", "Dermatology");


        DoctorAvailability monday = new DoctorAvailability(
                doctor, (byte) 1, LocalTime.of(9, 0), LocalTime.of(12, 0)
        );
        availabilityRepository.save(monday);


        DoctorAvailability mondayPm = new DoctorAvailability(
                doctor, (byte) 1, LocalTime.of(14, 0), LocalTime.of(17, 0)
        );
        availabilityRepository.save(mondayPm);


        DoctorAvailability tuesday = new DoctorAvailability(
                doctor, (byte) 2, LocalTime.of(10, 0), LocalTime.of(13, 0)
        );
        availabilityRepository.save(tuesday);

        List<DoctorAvailability> availability = availabilityRepository.findByDoctorId(doctor.getId());

        assertThat(availability).hasSize(3);
    }

    @Test
    void findByDoctorIdAndDayOfWeekReturnsCorrectSlots() {
        Doctor doctor = createTestDoctor("doctor3@medbook.test", "Neurology");


        availabilityRepository.save(new DoctorAvailability(
                doctor, (byte) 1, LocalTime.of(9, 0), LocalTime.of(12, 0)
        ));
        availabilityRepository.save(new DoctorAvailability(
                doctor, (byte) 1, LocalTime.of(14, 0), LocalTime.of(17, 0)
        ));


        availabilityRepository.save(new DoctorAvailability(
                doctor, (byte) 2, LocalTime.of(10, 0), LocalTime.of(13, 0)
        ));

        List<DoctorAvailability> mondaySlots = availabilityRepository.findByDoctorIdAndDayOfWeek(
                doctor.getId(), (byte) 1
        );

        assertThat(mondaySlots).hasSize(2);
        assertThat(mondaySlots).allMatch(slot -> slot.getDayOfWeek() == 1);
    }

    @Test
    void availabilityRelationshipToDoctorIsNavigable() {
        Doctor doctor = createTestDoctor("doctor4@medbook.test", "Psychiatry");
        DoctorAvailability availability = new DoctorAvailability(
                doctor, (byte) 3, LocalTime.of(10, 0), LocalTime.of(14, 0)
        );

        DoctorAvailability saved = availabilityRepository.save(availability);
        Long availabilityId = saved.getId();

        entityManager.clear();

        DoctorAvailability reloaded = availabilityRepository.findById(availabilityId).orElseThrow();


        Doctor loadedDoctor = reloaded.getDoctor();

        assertThat(loadedDoctor).isNotNull();
        assertThat(loadedDoctor.getId()).isEqualTo(doctor.getId());
        assertThat(loadedDoctor.getMedicalLicenseNumber()).startsWith("LIC-");
    }

    @Test
    void savedAvailabilityIsAssignedTimestampByDatabaseDefault() {
        Doctor doctor = createTestDoctor("doctor5@medbook.test", "Pediatrics");
        DoctorAvailability availability = new DoctorAvailability(
                doctor, (byte) 4, LocalTime.of(8, 0), LocalTime.of(11, 0)
        );

        DoctorAvailability saved = availabilityRepository.save(availability);
        availabilityRepository.flush();
        entityManager.refresh(saved);

        assertThat(saved.getCreatedAt()).as("created_at populated by DB default").isNotNull();
    }

    @Test
    void availabilityDefaultsToRecurring() {
        Doctor doctor = createTestDoctor("doctor6@medbook.test", "Orthopedics");
        DoctorAvailability availability = new DoctorAvailability(
                doctor, (byte) 5, LocalTime.of(13, 0), LocalTime.of(16, 0)
        );

        availabilityRepository.save(availability);

        var reloaded = availabilityRepository.findById(availability.getId()).orElseThrow();

        assertThat(reloaded.isRecurring()).isTrue();
    }

    @Test
    void availabilityWithEffectiveDatesWorks() {
        Doctor doctor = createTestDoctor("doctor7@medbook.test", "Ophthalmology");
        DoctorAvailability availability = new DoctorAvailability(
                doctor, (byte) 6, LocalTime.of(9, 0), LocalTime.of(12, 0)
        );
        availability.setRecurring(false);
        availability.setEffectiveFrom(LocalDate.of(2026, 10, 1));
        availability.setEffectiveUntil(LocalDate.of(2026, 10, 31));

        DoctorAvailability saved = availabilityRepository.save(availability);

        var reloaded = availabilityRepository.findById(saved.getId()).orElseThrow();

        assertThat(reloaded.isRecurring()).isFalse();
        assertThat(reloaded.getEffectiveFrom()).isEqualTo(LocalDate.of(2026, 10, 1));
        assertThat(reloaded.getEffectiveUntil()).isEqualTo(LocalDate.of(2026, 10, 31));
    }

    @Test
    void availabilitySupportsVariousDaysOfWeek() {
        Doctor doctor = createTestDoctor("doctor8@medbook.test", "Urology");


        availabilityRepository.save(new DoctorAvailability(
                doctor, (byte) 0, LocalTime.of(10, 0), LocalTime.of(12, 0)
        ));


        availabilityRepository.save(new DoctorAvailability(
                doctor, (byte) 3, LocalTime.of(14, 0), LocalTime.of(18, 0)
        ));


        availabilityRepository.save(new DoctorAvailability(
                doctor, (byte) 6, LocalTime.of(9, 0), LocalTime.of(13, 0)
        ));

        List<DoctorAvailability> allSlots = availabilityRepository.findByDoctorId(doctor.getId());

        assertThat(allSlots).hasSize(3);
        assertThat(allSlots).extracting(DoctorAvailability::getDayOfWeek)
                .containsExactlyInAnyOrder((byte) 0, (byte) 3, (byte) 6);
    }

    @Test
    void availabilitySupportsVariousTimeRanges() {
        Doctor doctor = createTestDoctor("doctor9@medbook.test", "Gastroenterology");


        DoctorAvailability early = new DoctorAvailability(
                doctor, (byte) 1, LocalTime.of(6, 0), LocalTime.of(9, 0)
        );
        availabilityRepository.save(early);


        DoctorAvailability midday = new DoctorAvailability(
                doctor, (byte) 1, LocalTime.of(12, 30), LocalTime.of(14, 30)
        );
        availabilityRepository.save(midday);


        DoctorAvailability evening = new DoctorAvailability(
                doctor, (byte) 1, LocalTime.of(18, 0), LocalTime.of(21, 0)
        );
        availabilityRepository.save(evening);

        List<DoctorAvailability> slots = availabilityRepository.findByDoctorIdAndDayOfWeek(
                doctor.getId(), (byte) 1
        );

        assertThat(slots).hasSize(3);
        assertThat(slots).extracting(DoctorAvailability::getStartTime)
                .containsExactlyInAnyOrder(
                        LocalTime.of(6, 0),
                        LocalTime.of(12, 30),
                        LocalTime.of(18, 0)
                );
    }

    @Test
    void deleteRemovesAvailability() {
        Doctor doctor = createTestDoctor("doctor10@medbook.test", "Oncology");
        DoctorAvailability availability = new DoctorAvailability(
                doctor, (byte) 2, LocalTime.of(11, 0), LocalTime.of(15, 0)
        );

        DoctorAvailability saved = availabilityRepository.save(availability);
        Long savedId = saved.getId();

        availabilityRepository.delete(saved);

        assertThat(availabilityRepository.findById(savedId)).isEmpty();
    }

    @Test
    void multipleDoctorsCanHaveSeparateAvailability() {
        Doctor doctor1 = createTestDoctor("doctor11@medbook.test", "Rheumatology");
        Doctor doctor2 = createTestDoctor("doctor12@medbook.test", "Endocrinology");


        availabilityRepository.save(new DoctorAvailability(
                doctor1, (byte) 1, LocalTime.of(9, 0), LocalTime.of(12, 0)
        ));
        availabilityRepository.save(new DoctorAvailability(
                doctor1, (byte) 2, LocalTime.of(14, 0), LocalTime.of(17, 0)
        ));


        availabilityRepository.save(new DoctorAvailability(
                doctor2, (byte) 1, LocalTime.of(10, 0), LocalTime.of(13, 0)
        ));

        List<DoctorAvailability> doctor1Slots = availabilityRepository.findByDoctorId(doctor1.getId());
        List<DoctorAvailability> doctor2Slots = availabilityRepository.findByDoctorId(doctor2.getId());

        assertThat(doctor1Slots).hasSize(2);
        assertThat(doctor2Slots).hasSize(1);
    }
}
