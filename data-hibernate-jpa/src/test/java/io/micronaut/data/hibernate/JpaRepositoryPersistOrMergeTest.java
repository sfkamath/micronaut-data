/*
 * Copyright 2017-2026 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.data.hibernate;

import io.micronaut.data.tck.entities.Student;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

@H2DBProperties
@MicronautTest(transactional = true)
final class JpaRepositoryPersistOrMergeTest {

    @Inject
    JpaStudentRepository jpaStudentRepository;

    @Test
    void persistOrMergePersistsWhenIdAndVersionAreNull() {
        Student student = new Student("PersistOrMerge");
        Student persisted = jpaStudentRepository.persistOrMerge(student);

        Assertions.assertSame(student, persisted);
        Assertions.assertNotNull(persisted.getId());
        Assertions.assertNotNull(persisted.getVersion());
    }

    @Test
    void persistOrMergeMergesWhenVersionIsPresent() {
        Student student = new Student("PersistOrMerge2");
        Student persisted = jpaStudentRepository.persistOrMerge(student);
        Long id = persisted.getId();
        Long version = persisted.getVersion();

        Student detached = new Student(persisted.getName());
        detached.setId(id);
        detached.setVersion(version);

        Student merged = jpaStudentRepository.persistOrMerge(detached);
        Assertions.assertNotSame(detached, merged);
        Assertions.assertEquals(id, merged.getId());
    }
}
