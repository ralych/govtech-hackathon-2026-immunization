package ch.hl7.vacd.api.entity;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.envers.AuditTable;
import org.hibernate.envers.Audited;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Temporal;
import jakarta.persistence.TemporalType;

@Entity
@Table(name = "fhir_resource")
@Audited
@AuditTable(value = "fhir_resource_audit")
public class ResourceEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "resource_type", nullable = false)
	private String resourceType;

	@Column(name = "resource_id")
	private String resourceId;

	@Column(name = "version")
	private int version = 1;

	@Column(name = "json", columnDefinition = "TEXT")
	private String json;

	@OneToMany(cascade = CascadeType.ALL, mappedBy = "resourceEntity")
	@OnDelete(action = OnDeleteAction.CASCADE)
	private List<ResourceIdentifier> identifiers = new ArrayList<>();

	@UpdateTimestamp
	@Temporal(TemporalType.TIMESTAMP)
//	@Column(columnDefinition = "TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP")
	private Calendar lastUpdate;

	public Long getId() {
		return id;
	}

	public void setId(Long id) {
		this.id = id;
	}

	public String getResourceType() {
		return resourceType;
	}

	public ResourceEntity setResourceType(String resourceType) {
		this.resourceType = resourceType;
		return this;
	}

	public String getResourceId() {
		return resourceId;
	}

	public ResourceEntity setResourceId(String resourceId) {
		this.resourceId = resourceId;
		return this;
	}

	public String getJson() {
		return json;
	}

	public ResourceEntity setJson(String json) {
		this.json = json;
		return this;
	}

	/**
	 * Method to get
	 * 
	 * @return the version
	 */
	public int getVersion() {
		return version;
	}

	/**
	 * Method to set
	 * 
	 * @param version the version
	 */
	public ResourceEntity setVersion(int version) {
		this.version = version;
		return this;
	}

	@PreUpdate
	public void onUpdate() {
		this.version++;
		this.lastUpdate = Calendar.getInstance();
	}

	/**
	 * Method get the last update
	 * 
	 * @return the last update date
	 */
	public Calendar getLastUpdate() {
		return lastUpdate;
	}

	/**
	 * Method to set the last update
	 * 
	 * @param lastUpdate the lastUpdate to set
	 */
	public ResourceEntity setLastUpdate(Calendar lastUpdate) {
		this.lastUpdate = lastUpdate;
		return this;
	}

	/**
	 * Method to get
	 * 
	 * @return the identifiers
	 */
	public List<ResourceIdentifier> getIdentifiers() {
		if (identifiers == null) {
			identifiers = new ArrayList<>();
		}
		return identifiers;
	}

	/**
	 * Method to set
	 * 
	 * @param identifiers the identifiers
	 */
	public ResourceEntity setIdentifiers(List<ResourceIdentifier> identifiers) {
		this.identifiers = identifiers;
		return this;
	}

	public ResourceEntity addIdentifier(ResourceIdentifier identifier) {
		this.getIdentifiers().add(identifier);
		return this;
	}

}
