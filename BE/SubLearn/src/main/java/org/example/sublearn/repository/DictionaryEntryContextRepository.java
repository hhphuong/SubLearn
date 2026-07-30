package org.example.sublearn.repository;

import org.example.sublearn.domain.DictionaryEntryContext;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DictionaryEntryContextRepository extends JpaRepository<DictionaryEntryContext, Long> {
}
