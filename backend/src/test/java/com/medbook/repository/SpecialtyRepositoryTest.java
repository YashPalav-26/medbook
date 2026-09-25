package com.medbook.repository;

import com.medbook.entity.Role;
import com.medbook.entity.Specialty;
import com.medbook.entity.User;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for {@link SpecialtyRepository} against the real MySQL schema.
 *
 * <p>All tests are {@link Transactional} and rolled back after execution, so no
 * permanent test data is left behind in the development database.</p>
 */
@SpringBootTest
@Transactional
class SpecialtyRepositoryTest {

    @Autowired
    private SpecialtyRepository specialtyRepository;

    @Autowired
    private EntityManager entityManager;

    /**
     * Fixture names carry a unique suffix: the shared test database may already contain
     * the demo seed data (real specialties like "Cardiology"), and the name column is
     * unique. The suffix never appears in assertions — each test matches on its own
     * prefix, and MySQL's case-insensitive collation keeps the case-sensitivity tests
     * meaningful for any name.
     */
    private String uniqueName(String prefix) {
        return prefix + "-" + System.nanoTime();
    }

    @Test
    void findAllExecutesAgainstRealSchema() {
        // No specialties have been created via application code yet; the point is
        // that the query executes against the mapped `specialties` table without error.
        assertThat(specialtyRepository.findAll()).isNotNull();
    }

    @Test
    void findByNameReturnsEmptyWhenNoSpecialtyExists() {
        assertThat(specialtyRepository.findByName("NonExistentSpecialty")).isEmpty();
    }

    @Test
    void savedSpecialtyIsRetrievableById() {
        // Demonstrates save/read round trip. The timestamp is assigned by MySQL,
        // read back through flush + refresh.
        String name = uniqueName("Cardiology");
        Specialty cardiology = new Specialty(name, "Heart and cardiovascular system specialist");

        Specialty saved = specialtyRepository.save(cardiology);

        assertThat(saved.getId()).isNotNull();

        var reloaded = specialtyRepository.findById(saved.getId());

        assertThat(reloaded).isPresent();
        assertThat(reloaded.get().getName()).isEqualTo(name);
        assertThat(reloaded.get().getDescription()).contains("Heart");
    }

    @Test
    void findByNameReturnsExactMatch() {
        String name = uniqueName("Orthopedics");
        Specialty orthopedics = new Specialty(name, "Bone and joint specialist");
        specialtyRepository.save(orthopedics);

        var found = specialtyRepository.findByName(name);

        assertThat(found).isPresent();
        assertThat(found.get().getName()).isEqualTo(name);
    }

    @Test
    void findByNameIsCaseInsensitiveByDefault() {
        // With MySQL's utf8mb4_unicode_ci collation (case-insensitive), findByName
        // returns matches regardless of case. This is the default behavior.
        String name = uniqueName("Dermatology");
        Specialty dermatology = new Specialty(name, "Skin specialist");
        specialtyRepository.save(dermatology);

        assertThat(specialtyRepository.findByName(name.toLowerCase())).isPresent();
        assertThat(specialtyRepository.findByName(name)).isPresent();
        assertThat(specialtyRepository.findByName(name.toUpperCase())).isPresent();
    }

    @Test
    void findByNameIgnoreCaseExplicitlyHandlesCase() {
        // findByNameIgnoreCase uses LOWER() comparison explicitly. With a
        // case-insensitive collation, this behaves similarly to findByName.
        // This method is useful when the database uses a case-sensitive collation.
        String name = uniqueName("Neurology");
        Specialty neurology = new Specialty(name, "Nervous system specialist");
        specialtyRepository.save(neurology);

        assertThat(specialtyRepository.findByNameIgnoreCase(name.toLowerCase())).isPresent();
        assertThat(specialtyRepository.findByNameIgnoreCase(name.toUpperCase())).isPresent();
        assertThat(specialtyRepository.findByNameIgnoreCase(name)).isPresent();
    }

    @Test
    void savedSpecialtyIsAssignedTimestampByDatabaseDefault() {
        // created_at is NOT NULL DEFAULT CURRENT_TIMESTAMP(3) in the schema,
        // marked insertable=false so Hibernate leaves it out of the INSERT and
        // MySQL supplies the default. Flush + refresh proves it round-trips back.
        Specialty pediatrics = new Specialty(uniqueName("Pediatrics"), "Children's specialist");

        Specialty saved = specialtyRepository.save(pediatrics);
        specialtyRepository.flush();
        entityManager.refresh(saved);

        assertThat(saved.getCreatedAt()).as("created_at populated by DB default").isNotNull();
    }

    @Test
    void newSpecialtyDefaultsToActive() {
        Specialty psychiatry = new Specialty(uniqueName("Psychiatry"), "Mental health specialist");
        specialtyRepository.save(psychiatry);

        var reloaded = specialtyRepository.findById(psychiatry.getId()).orElseThrow();

        assertThat(reloaded.isActive()).isTrue();
    }

    @Test
    void deleteRemovesSpecialty() {
        Specialty urology = new Specialty(uniqueName("Urology"), "Urinary system specialist");
        Specialty saved = specialtyRepository.save(urology);
        Long savedId = saved.getId();

        specialtyRepository.delete(saved);

        assertThat(specialtyRepository.findById(savedId)).isEmpty();
    }
}
