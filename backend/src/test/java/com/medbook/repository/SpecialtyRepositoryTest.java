package com.medbook.repository;

import com.medbook.entity.Specialty;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Transactional
class SpecialtyRepositoryTest {

    @Autowired
    private SpecialtyRepository specialtyRepository;

    @Autowired
    private EntityManager entityManager;

    private String uniqueName(String prefix) {
        return prefix + "-" + System.nanoTime();
    }

    @Test
    void findAllExecutesAgainstRealSchema() {

        assertThat(specialtyRepository.findAll()).isNotNull();
    }

    @Test
    void findByNameReturnsEmptyWhenNoSpecialtyExists() {
        assertThat(specialtyRepository.findByName("NonExistentSpecialty")).isEmpty();
    }

    @Test
    void savedSpecialtyIsRetrievableById() {

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

        String name = uniqueName("Dermatology");
        Specialty dermatology = new Specialty(name, "Skin specialist");
        specialtyRepository.save(dermatology);

        assertThat(specialtyRepository.findByName(name.toLowerCase())).isPresent();
        assertThat(specialtyRepository.findByName(name)).isPresent();
        assertThat(specialtyRepository.findByName(name.toUpperCase())).isPresent();
    }

    @Test
    void findByNameIgnoreCaseExplicitlyHandlesCase() {

        String name = uniqueName("Neurology");
        Specialty neurology = new Specialty(name, "Nervous system specialist");
        specialtyRepository.save(neurology);

        assertThat(specialtyRepository.findByNameIgnoreCase(name.toLowerCase())).isPresent();
        assertThat(specialtyRepository.findByNameIgnoreCase(name.toUpperCase())).isPresent();
        assertThat(specialtyRepository.findByNameIgnoreCase(name)).isPresent();
    }

    @Test
    void savedSpecialtyIsAssignedTimestampByDatabaseDefault() {

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
