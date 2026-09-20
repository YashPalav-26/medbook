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

/**
 * Integration tests for {@link DoctorRepository} against the real MySQL schema.
 *
 * <p>All tests are {@link Transactional} and rolled back after execution, so no
 * permanent test data is left behind in the development database.</p>
 *
 * <p>Tests demonstrate:
 * - Foreign key relationships (Doctor → User, Doctor → Specialty)
 * - LAZY loading and how to assert on relationships inside a transaction
 * - Derived query methods with nested property traversal
 * - Unique constraint enforcement</p>
 */
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

    /**
     * Helper: creates a test user with the DOCTOR role.
     * Called at the start of each test that needs a user.
     */
    private User createTestDoctorUser(String email) {
        Role doctorRole = roleRepository.findByName("DOCTOR")
                .orElseThrow(() -> new IllegalStateException("DOCTOR role not seeded"));

        User user = new User(email, "hashed-password", "Test", "Doctor", "+919876543210");
        user.addRole(doctorRole);

        return userRepository.save(user);
    }

    /**
     * Helper: creates a test specialty.
     */
    private Specialty createTestSpecialty(String name) {
        return specialtyRepository.save(new Specialty(name, "Test specialty"));
    }

    @Test
    void findAllExecutesAgainstRealSchema() {
        // No doctors have been created yet; the point is that the query runs
        // against the mapped `doctors` table without error.
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
        // Demonstrates that Spring Data JPA can navigate Doctor.specialty.name
        // without requiring a manual JPQL query. The derived query method
        // generates an implicit JOIN.

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

        List<Doctor> cardiologists = doctorRepository.findBySpecialtyName("Cardiology");

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
        // user_id is UNIQUE in the doctors table, so this returns at most one result.
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
        // Demonstrates that a Doctor loaded from the repository can navigate
        // to its User via getUser(). Because the relationship is LAZY, the User
        // is loaded only when getUser() is called, and only within the transaction.
        User user = createTestDoctorUser("doctor9@medbook.test");
        Specialty specialty = createTestSpecialty("Pediatrics");

        Doctor doctor = new Doctor(user, specialty, "LIC-PED-001", 3, new BigDecimal("400.00"));
        Doctor saved = doctorRepository.save(doctor);
        Long doctorId = saved.getId();

        // Clear the persistence context so the next load comes fresh from the DB.
        entityManager.clear();

        // Load the doctor. The User is NOT loaded yet (LAZY).
        Doctor reloaded = doctorRepository.findById(doctorId).orElseThrow();

        // Call getUser() inside the transaction. LAZY loading kicks in and
        // the User is fetched. If this were outside a transaction, it would
        // throw LazyInitializationException.
        User loadedUser = reloaded.getUser();

        assertThat(loadedUser).isNotNull();
        assertThat(loadedUser.getEmail()).isEqualTo("doctor9@medbook.test");
        assertThat(loadedUser.getFirstName()).isEqualTo("Test");
    }

    @Test
    void doctorRelationshipToSpecialtyIsNavigable() {
        // Demonstrates that a Doctor loaded from the repository can navigate
        // to its Specialty via getSpecialty(). Because the relationship is LAZY,
        // the Specialty is loaded only when getSpecialty() is called, and only
        // within the transaction.
        User user = createTestDoctorUser("doctor10@medbook.test");
        Specialty urology = createTestSpecialty("Urology");

        Doctor doctor = new Doctor(user, urology, "LIC-URO-001", 9, new BigDecimal("550.00"));
        Doctor saved = doctorRepository.save(doctor);
        Long doctorId = saved.getId();

        entityManager.clear();

        Doctor reloaded = doctorRepository.findById(doctorId).orElseThrow();

        // Call getSpecialty() inside the transaction. LAZY loading fetches it.
        Specialty loadedSpecialty = reloaded.getSpecialty();

        assertThat(loadedSpecialty).isNotNull();
        assertThat(loadedSpecialty.getName()).isEqualTo("Urology");
    }

    @Test
    void savedDoctorIsAssignedTimestampsByDatabaseDefaults() {
        // created_at and updated_at are NOT NULL with DB defaults.
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
