package ch.hl7.vacd.api.repo;

import ch.hl7.vacd.api.entity.ResourceEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ResourceRepository extends JpaRepository<ResourceEntity, Long> {
    List<ResourceEntity> findByResourceType(String resourceType);
    List<ResourceEntity> findByResourceTypeAndResourceId(String resourceType, String resourceId);
}
