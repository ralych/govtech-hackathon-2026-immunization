package ch.hl7.vacd.api.entity;

import java.util.Calendar;

import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.envers.AuditTable;
import org.hibernate.envers.Audited;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Temporal;
import jakarta.persistence.TemporalType;

@Entity
@Table(name = "fhir_resource_reference")
@Audited
@AuditTable(value = "fhir_resource_reference_audit")
public class ResourceReferenceEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;
	@ManyToOne
	@JoinColumn(name = "sourceId")
	private ResourceEntity sourceEntity;
	@Column(nullable = false)
	private String sourceField;
	@Column(nullable = false)
	private String targetType;
	@Column(nullable = false)
	private String targetId;

	@PreUpdate
	public void onUpdate() {
		this.lastUpdate = Calendar.getInstance();
	}

	@UpdateTimestamp
	@Temporal(TemporalType.TIMESTAMP)
	private Calendar lastUpdate;

	/**
	 * Returns the primary key identifier of this reference record.
	 *
	 * @return the id value, may be null for transient (unsaved) entities
	 */
	public Long getId() {
		return id;
	}

	/**
	 * Sets the primary key identifier for this reference record.
	 *
	 * Note: the id is normally managed by the persistence provider and should only
	 * be set manually in exceptional cases (e.g. tests).
	 *
	 * @param id the identifier to set
	 */
	public ResourceReferenceEntity setId(Long id) {
		this.id = id;
		return this;
	}

	/**
	 * Returns the source resource entity that holds the reference.
	 *
	 * @return the {@link ResourceEntity} that is the source, may be null
	 */
	public ResourceEntity getSourceEntity() {
		return sourceEntity;
	}

	/**
	 * Sets the source resource entity that holds the reference.
	 *
	 * @param sourceEntity the source {@link ResourceEntity} to set
	 */
	public ResourceReferenceEntity setSourceEntity(ResourceEntity sourceEntity) {
		this.sourceEntity = sourceEntity;
		return this;
	}

	/**
	 * Returns the field name on the source resource that contains the reference.
	 *
	 * @return the source field name, never null when persisted
	 */
	public String getSourceField() {
		return sourceField;
	}

	/**
	 * Sets the field name on the source resource that contains the reference.
	 *
	 * @param sourceField the name of the source field, must not be null when
	 *                    persisting
	 */
	public ResourceReferenceEntity setSourceField(String sourceField) {
		this.sourceField = sourceField;
		return this;
	}

	/**
	 * Returns the target resource type of the reference (e.g. "Patient").
	 *
	 * @return the target resource type, never null when persisted
	 */
	public String getTargetType() {
		return targetType;
	}

	/**
	 * Sets the target resource type of the reference (e.g. "Patient").
	 *
	 * @param targetType the target resource type to set
	 */
	public ResourceReferenceEntity setTargetType(String targetType) {
		this.targetType = targetType;
		return this;
	}

	/**
	 * Returns the identifier of the target resource that is referenced.
	 *
	 * @return the target resource id, never null when persisted
	 */
	public String getTargetId() {
		return targetId;
	}

	/**
	 * Sets the identifier of the target resource that is referenced.
	 *
	 * @param targetId the target resource id to set
	 */
	public ResourceReferenceEntity setTargetId(String targetId) {
		this.targetId = targetId;
		return this;
	}

	/**
	 * Returns the timestamp of the last update to this entity.
	 * <p>
	 * This value is maintained automatically by Hibernate via
	 * {@code @UpdateTimestamp} and by the {@link #onUpdate()} lifecycle callback.
	 *
	 * @return the last update timestamp, may be null for newly created/transient
	 *         entities
	 */
	public Calendar getLastUpdate() {
		return lastUpdate;
	}

	/**
	 * Sets the last update timestamp.
	 * <p>
	 * Typically managed by the persistence provider; setting manually is allowed
	 * for special cases such as tests.
	 *
	 * @param lastUpdate the timestamp to set, may be null
	 */
	public ResourceReferenceEntity setLastUpdate(Calendar lastUpdate) {
		this.lastUpdate = lastUpdate;
		return this;
	}

	@Override
	public String toString() {
		StringBuilder builder = new StringBuilder();
		builder.append("ResourceReferenceEntity [\n    id=").append(id).append("\n    sourceEntity=")
				.append(sourceEntity.getResourceId()).append("\n    sourceField=").append(sourceField).append("\n    targetType=")
				.append(targetType).append("\n    targetId=").append(targetId).append("\n    lastUpdate=")
				.append(lastUpdate).append("\n]");
		return builder.toString();
	}

}
