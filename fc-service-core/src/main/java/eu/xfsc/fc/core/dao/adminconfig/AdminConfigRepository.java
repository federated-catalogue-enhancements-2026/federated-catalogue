package eu.xfsc.fc.core.dao.adminconfig;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface AdminConfigRepository
    extends JpaRepository<AdminConfigEntry, String> {

  List<AdminConfigEntry> findByConfigKeyStartingWith(String prefix);
}
