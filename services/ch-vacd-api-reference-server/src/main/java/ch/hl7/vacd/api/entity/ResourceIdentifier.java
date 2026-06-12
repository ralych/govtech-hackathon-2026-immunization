package ch.hl7.vacd.api.entity;

import org.hibernate.envers.AuditTable;
import org.hibernate.envers.Audited;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "fhir_resource_identifier")
@Audited
@AuditTable(value = "fhir_resource_identifier_audit")
public class ResourceIdentifier {

	@Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
	/** The system of the identifier (e.g., OID, URL). */
	@Column(nullable = false)
	private String idSystem;
	/** The value of the identifier. */
	@Column(nullable = false)
	private String idValue;

	@ManyToOne
	@JoinColumn(name = "resourceId")
	private ResourceEntity resourceEntity;

	/**
	 * Method to get
	 * @return the id
	 */
	public Long getId() {
		return id;
	}

	/**
	 * Method to get
	 * @return the idSystem
	 */
	public String getIdSystem() {
		return idSystem;
	}

	/**
	 * Method to get
	 * @return the idValue
	 */
	public String getIdValue() {
		return idValue;
	}

	/**
	 * Method to get
	 * @return the resourceEntity
	 */
	public ResourceEntity getResourceEntity() {
		return resourceEntity;
	}

	/**
	 * Method to set
	 * @param id the id
	 */
	public ResourceIdentifier setId(Long id) {
		this.id = id;
		return this;
	}

	/**
	 * Method to set
	 * @param idSystem the idSystem
	 */
	public ResourceIdentifier setIdSystem(String idSystem) {
		this.idSystem = idSystem;
		return this;
	}

	/**
	 * Method to set
	 * @param idValue the idValue
	 */
	public ResourceIdentifier setIdValue(String idValue) {
		this.idValue = idValue;
		return this;
	}
	
	/**
	 * Method to set
	 * @param resourceEntity the resourceEntity
	 */
	public ResourceIdentifier setResourceEntity(ResourceEntity resourceEntity) {
		this.resourceEntity = resourceEntity;
		return this;
	}
	
	
	
}
