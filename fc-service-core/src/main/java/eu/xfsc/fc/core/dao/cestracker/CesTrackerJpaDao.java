package eu.xfsc.fc.core.dao.cestracker;

import org.springframework.stereotype.Component;

import eu.xfsc.fc.core.dao.CesTrackerDao;
import eu.xfsc.fc.core.service.pubsub.ces.CesTracking;
import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class CesTrackerJpaDao implements CesTrackerDao {

  private final CesTrackerRepository repository;

  @Override
  public void insert(CesTracking event) {
    repository.save(CesTrackerEntityMapper.toEntity(event));
  }

  @Override
  public CesTracking select(String cesId) {
    return repository.findById(cesId)
        .map(CesTrackerEntityMapper::toTracking)
        .orElse(null);
  }

  @Override
  public CesTracking selectLatest() {
    return repository.findFirstByOrderByCreatedAtDesc()
        .map(CesTrackerEntityMapper::toTracking)
        .orElse(null);
  }
}
