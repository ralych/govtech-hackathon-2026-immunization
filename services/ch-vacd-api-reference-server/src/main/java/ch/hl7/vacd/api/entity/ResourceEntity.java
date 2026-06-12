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
import jakarta.persistence.PreUpdate;
import jakarta.persistence.PrePersist;
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

    public void setResourceType(String resourceType) {
        this.resourceType = resourceType;
    }

    public String getResourceId() {
        return resourceId;
    }

    public void setResourceId(String resourceId) {
        this.resourceId = resourceId;
    }

    public String getJson() {
        return json;
    }

    public void setJson(String json) {
        this.json = json;
    }

	/**
	 * Method to get
	 * @return the version
	 */
	public int getVersion() {
		return version;
	}

	/**
	 * Method to set
	 * @param version the version
	 */
	public void setVersion(int version) {
		this.version = version;
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
	public void setLastUpdate(Calendar lastUpdate) {
		this.lastUpdate = lastUpdate;
	}
}
