package com.medbook.repository;

import com.medbook.entity.Patient;
import com.medbook.entity.Role;
import com.medbook.entity.User;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Transactional
class PatientRepositoryTest {

    @Autowired
    private PatientRepository patientRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private EntityManager entityManager;

    private User createTestPatientUser(String email) {
        Role patientRole = roleRepository.findByName("PATIENT")
                .orElseThrow(() -> new IllegalStateException("PATIENT role not seeded"));

        User user = new User(email, "hashed-password", "Test", "Patient", "+919876543211");
        user.addRole(patientRole);

        return userRepository.save(user);
    }

    @Test
    void findAllExecutesAgainstRealSchema() {

        assertThat(patientRepository.findAll()).isNotNull();
    }

    @Test
    void savedPatientIsRetrievableById() {
        User user = createTestPatientUser("patient1@medbook.test");
        Patient patient = new Patient(user, LocalDate.of(1990, 5, 15));
        patient.setAddressLine1("123 Main Street");
        patient.setCity("Mumbai");

        Patient saved = patientRepository.save(patient);

        assertThat(saved.getId()).isNotNull();

        var reloaded = patientRepository.findById(saved.getId());

        assertThat(reloaded).isPresent();
        assertThat(reloaded.get().getDateOfBirth()).isEqualTo(LocalDate.of(1990, 5, 15));
        assertThat(reloaded.get().getAddressLine1()).isEqualTo("123 Main Street");
    }

    @Test
    void findByUserIdReturnsPatientLinkedToUser() {
        User user = createTestPatientUser("patient2@medbook.test");
        Patient patient = new Patient(user, LocalDate.of(1985, 10, 20));
        patientRepository.save(patient);

        var found = patientRepository.findByUserId(user.getId());

        assertThat(found).isPresent();
        assertThat(found.get().getDateOfBirth()).isEqualTo(LocalDate.of(1985, 10, 20));
    }

    @Test
    void findByActiveTrueReturnsOnlyActivePatients() {
        User user1 = createTestPatientUser("patient3@medbook.test");
        User user2 = createTestPatientUser("patient4@medbook.test");

        Patient activePatient = new Patient(user1, LocalDate.of(1995, 3, 10));
        activePatient.setActive(true);

        Patient inactivePatient = new Patient(user2, LocalDate.of(1980, 7, 25));
        inactivePatient.setActive(false);

        patientRepository.save(activePatient);
        patientRepository.save(inactivePatient);

        java.util.List<Patient> activePatients = patientRepository.findByActiveTrue();

        assertThat(activePatients)
                .extracting(Patient::getDateOfBirth)
                .contains(LocalDate.of(1995, 3, 10))
                .doesNotContain(LocalDate.of(1980, 7, 25));
    }

    @Test
    void patientRelationshipToUserIsNavigable() {

        User user = createTestPatientUser("patient5@medbook.test");
        Patient patient = new Patient(user, LocalDate.of(2000, 1, 1));
        Patient saved = patientRepository.save(patient);
        Long patientId = saved.getId();

        entityManager.clear();

        Patient reloaded = patientRepository.findById(patientId).orElseThrow();

        User loadedUser = reloaded.getUser();

        assertThat(loadedUser).isNotNull();
        assertThat(loadedUser.getEmail()).isEqualTo("patient5@medbook.test");
        assertThat(loadedUser.getFirstName()).isEqualTo("Test");
    }

    @Test
    void savedPatientIsAssignedTimestampsByDatabaseDefaults() {
        User user = createTestPatientUser("patient6@medbook.test");
        Patient patient = new Patient(user, LocalDate.of(1992, 11, 30));
        Patient saved = patientRepository.save(patient);

        patientRepository.flush();
        entityManager.refresh(saved);

        assertThat(saved.getCreatedAt()).as("created_at populated by DB default").isNotNull();
        assertThat(saved.getUpdatedAt()).as("updated_at populated by DB default").isNotNull();
    }

    @Test
    void newPatientDefaultsToActive() {
        User user = createTestPatientUser("patient7@medbook.test");
        Patient patient = new Patient(user, LocalDate.of(1988, 6, 15));
        patientRepository.save(patient);

        var reloaded = patientRepository.findById(patient.getId()).orElseThrow();

        assertThat(reloaded.isActive()).isTrue();
    }

    @Test
    void patientWithOptionalAddressFieldsWorks() {
        User user = createTestPatientUser("patient8@medbook.test");
        Patient patient = new Patient(user, LocalDate.of(1975, 12, 25));
        patient.setAddressLine1("456 Oak Avenue");
        patient.setAddressLine2("Apt 5B");
        patient.setCity("Delhi");
        patient.setStateProvince("Delhi");
        patient.setPostalCode("110001");
        patient.setCountry("India");
        patient.setEmergencyContactName("John Doe");
        patient.setEmergencyContactPhone("+919999999998");

        Patient saved = patientRepository.save(patient);

        var reloaded = patientRepository.findById(saved.getId()).orElseThrow();

        assertThat(reloaded.getAddressLine1()).isEqualTo("456 Oak Avenue");
        assertThat(reloaded.getAddressLine2()).isEqualTo("Apt 5B");
        assertThat(reloaded.getCity()).isEqualTo("Delhi");
        assertThat(reloaded.getStateProvince()).isEqualTo("Delhi");
        assertThat(reloaded.getPostalCode()).isEqualTo("110001");
        assertThat(reloaded.getCountry()).isEqualTo("India");
        assertThat(reloaded.getEmergencyContactName()).isEqualTo("John Doe");
        assertThat(reloaded.getEmergencyContactPhone()).isEqualTo("+919999999998");
    }

    @Test
    void patientWithInsuranceInfoWorks() {
        User user = createTestPatientUser("patient9@medbook.test");
        Patient patient = new Patient(user, LocalDate.of(1982, 4, 8));
        patient.setHasInsurance(true);
        patient.setInsuranceProvider("HealthCare Global");
        patient.setInsurancePolicyNumber("HP-12345678");

        Patient saved = patientRepository.save(patient);

        var reloaded = patientRepository.findById(saved.getId()).orElseThrow();

        assertThat(reloaded.hasInsurance()).isTrue();
        assertThat(reloaded.getInsuranceProvider()).isEqualTo("HealthCare Global");
        assertThat(reloaded.getInsurancePolicyNumber()).isEqualTo("HP-12345678");
    }

    @Test
    void patientWithAllergiesWorks() {
        User user = createTestPatientUser("patient10@medbook.test");
        Patient patient = new Patient(user, LocalDate.of(1998, 9, 12));
        patient.setAllergies("Penicillin, Shellfish, Pollen");

        Patient saved = patientRepository.save(patient);

        var reloaded = patientRepository.findById(saved.getId()).orElseThrow();

        assertThat(reloaded.getAllergies()).isEqualTo("Penicillin, Shellfish, Pollen");
    }

    @Test
    void deleteRemovesPatient() {
        User user = createTestPatientUser("patient11@medbook.test");
        Patient patient = new Patient(user, LocalDate.of(1993, 2, 28));
        Patient saved = patientRepository.save(patient);
        Long savedId = saved.getId();

        patientRepository.delete(saved);

        assertThat(patientRepository.findById(savedId)).isEmpty();
    }
}
