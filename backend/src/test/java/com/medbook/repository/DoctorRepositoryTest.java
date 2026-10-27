package com.medbook.repository;

import com.medbook.entity.Doctor;
import com.medbook.entity.Role;
import com.medbook.entity.Specialty;
import com.medbook.entity.User;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Transactional
class DoctorRepositoryTest {

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

    @Test
    void findAllExecutesAgainstRealSchema() {

        assertThat(doctorRepository.findAll()).isNotNull();
    }

    @Test
    void savedDoctorIsRetrievableById() {
        User user = createTestDoctorUser("doctor1@medbook.test");
        Specialty specialty = createTestSpecialty("Cardiology");

        Doctor doctor = new Doctor(user, specialty, "LIC-12345", 5, new BigDecimal("500.00"));
        Doctor saved = doctorRepository.save(doctor);

        assertThat(saved.getId()).isNotNull();

        var reloaded = doctorRepository.findById(saved.getId());

        assertThat(reloaded).isPresent();
        assertThat(reloaded.get().getMedicalLicenseNumber()).isEqualTo("LIC-12345");
    }

    @Test
    void findByMedicalLicenseNumberReturnsExactMatch() {
        User user = createTestDoctorUser("doctor2@medbook.test");
        Specialty specialty = createTestSpecialty("Dermatology");

        Doctor doctor = new Doctor(user, specialty, "LIC-DERMATO-001", 8, new BigDecimal("450.00"));
        doctorRepository.save(doctor);

        var found = doctorRepository.findByMedicalLicenseNumber("LIC-DERMATO-001");

        assertThat(found).isPresent();
        assertThat(found.get().getYearsExperience()).isEqualTo(8);
    }

    @Test
    void findBySpecialtyNameTraversesNestedProperty() {

        User user1 = createTestDoctorUser("doctor3@medbook.test");
        User user2 = createTestDoctorUser("doctor4@medbook.test");
        Specialty cardiology = createTestSpecialty("Cardiology");
        Specialty orthopedics = createTestSpecialty("Orthopedics");

        Doctor doc1 = new Doctor(user1, cardiology, "LIC-CARD-001", 10, new BigDecimal("600.00"));
        Doctor doc2 = new Doctor(user2, cardiology, "LIC-CARD-002", 7, new BigDecimal("550.00"));
        Doctor doc3 = new Doctor(createTestDoctorUser("doctor5@medbook.test"), orthopedics, "LIC-ORTHO-001", 12, new BigDecimal("500.00"));

        doctorRepository.save(doc1);
        doctorRepository.save(doc2);
        doctorRepository.save(doc3);

        List<Doctor> cardiologists = doctorRepository.findBySpecialtyName(cardiology.getName());

        assertThat(cardiologists)
                .hasSize(2)
                .extracting(Doctor::getMedicalLicenseNumber)
                .containsExactlyInAnyOrder("LIC-CARD-001", "LIC-CARD-002");
    }

    @Test
    void findByActiveTrueReturnsOnlyActiveDoctors() {
        User user1 = createTestDoctorUser("doctor6@medbook.test");
        User user2 = createTestDoctorUser("doctor7@medbook.test");
        Specialty specialty = createTestSpecialty("Neurology");

        Doctor activeDoc = new Doctor(user1, specialty, "LIC-NEURO-001", 6, new BigDecimal("500.00"));
        activeDoc.setActive(true);

        Doctor inactiveDoc = new Doctor(user2, specialty, "LIC-NEURO-002", 4, new BigDecimal("400.00"));
        inactiveDoc.setActive(false);

        doctorRepository.save(activeDoc);
        doctorRepository.save(inactiveDoc);

        List<Doctor> activeDoctors = doctorRepository.findByActiveTrue();

        assertThat(activeDoctors)
                .extracting(Doctor::getMedicalLicenseNumber)
                .contains("LIC-NEURO-001")
                .doesNotContain("LIC-NEURO-002");
    }

    @Test
    void findByUserIdReturnsDoctorLinkedToUser() {

        User user = createTestDoctorUser("doctor8@medbook.test");
        Specialty specialty = createTestSpecialty("Psychiatry");

        Doctor doctor = new Doctor(user, specialty, "LIC-PSY-001", 15, new BigDecimal("700.00"));
        doctorRepository.save(doctor);

        var found = doctorRepository.findByUserId(user.getId());

        assertThat(found).isPresent();
        assertThat(found.get().getMedicalLicenseNumber()).isEqualTo("LIC-PSY-001");
    }

    @Test
    void doctorRelationshipToUserIsNavigable() {

        User user = createTestDoctorUser("doctor9@medbook.test");
        Specialty specialty = createTestSpecialty("Pediatrics");

        Doctor doctor = new Doctor(user, specialty, "LIC-PED-001", 3, new BigDecimal("400.00"));
        Doctor saved = doctorRepository.save(doctor);
        Long doctorId = saved.getId();

        entityManager.clear();

        Doctor reloaded = doctorRepository.findById(doctorId).orElseThrow();

        User loadedUser = reloaded.getUser();

        assertThat(loadedUser).isNotNull();
        assertThat(loadedUser.getEmail()).isEqualTo("doctor9@medbook.test");
        assertThat(loadedUser.getFirstName()).isEqualTo("Test");
    }

    @Test
    void doctorRelationshipToSpecialtyIsNavigable() {

        User user = createTestDoctorUser("doctor10@medbook.test");
        Specialty urology = createTestSpecialty("Urology");

        Doctor doctor = new Doctor(user, urology, "LIC-URO-001", 9, new BigDecimal("550.00"));
        Doctor saved = doctorRepository.save(doctor);
        Long doctorId = saved.getId();

        entityManager.clear();

        Doctor reloaded = doctorRepository.findById(doctorId).orElseThrow();

        Specialty loadedSpecialty = reloaded.getSpecialty();

        assertThat(loadedSpecialty).isNotNull();
        assertThat(loadedSpecialty.getId()).isEqualTo(urology.getId());
        assertThat(loadedSpecialty.getName()).startsWith("Urology-");
    }

    @Test
    void savedDoctorIsAssignedTimestampsByDatabaseDefaults() {

        User user = createTestDoctorUser("doctor11@medbook.test");
        Specialty specialty = createTestSpecialty("Oncology");

        Doctor doctor = new Doctor(user, specialty, "LIC-ONC-001", 11, new BigDecimal("800.00"));
        Doctor saved = doctorRepository.save(doctor);

        doctorRepository.flush();
        entityManager.refresh(saved);

        assertThat(saved.getCreatedAt()).as("created_at populated by DB default").isNotNull();
        assertThat(saved.getUpdatedAt()).as("updated_at populated by DB default").isNotNull();
    }

    @Test
    void newDoctorDefaultsToActive() {
        User user = createTestDoctorUser("doctor12@medbook.test");
        Specialty specialty = createTestSpecialty("Gastroenterology");

        Doctor doctor = new Doctor(user, specialty, "LIC-GASTRO-001", 7, new BigDecimal("500.00"));
        doctorRepository.save(doctor);

        var reloaded = doctorRepository.findById(doctor.getId()).orElseThrow();

        assertThat(reloaded.isActive()).isTrue();
    }

    @Test
    void doctorWithBigDecimalConsultationFeePreservesPrecision() {
        User user = createTestDoctorUser("doctor13@medbook.test");
        Specialty specialty = createTestSpecialty("Ophthalmology");

        BigDecimal fee = new BigDecimal("1234.56");
        Doctor doctor = new Doctor(user, specialty, "LIC-OPHTHAL-001", 13, fee);
        Doctor saved = doctorRepository.save(doctor);

        var reloaded = doctorRepository.findById(saved.getId()).orElseThrow();

        assertThat(reloaded.getConsultationFee()).isEqualTo(new BigDecimal("1234.56"));
    }
}
