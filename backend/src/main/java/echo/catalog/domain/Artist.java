package echo.catalog.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "artists")
public class Artist {

    @Id
    @Column(name = "id", columnDefinition = "UUID")
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "provider_id", nullable = false)
    private MusicProvider provider;

    @Column(name = "provider_external_id", nullable = false)
    private String providerExternalId;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "normalised_name", nullable = false)
    private String normalisedName;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public Artist() {
        this.id = UUID.randomUUID();
    }

    public Artist(UUID id) {
        this.id = id;
    }

    public UUID getId() {
        return id;
    }

    public MusicProvider getProvider() {
        return provider;
    }

    public void setProvider(MusicProvider provider) {
        this.provider = provider;
    }

    public String getProviderExternalId() {
        return providerExternalId;
    }

    public void setProviderExternalId(String providerExternalId) {
        this.providerExternalId = providerExternalId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getNormalisedName() {
        return normalisedName;
    }

    public void setNormalisedName(String normalisedName) {
        this.normalisedName = normalisedName;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    @PrePersist
    protected void onPersist() {
        if (id == null) {
            id = UUID.randomUUID();
        }
        if (createdAt == null) {
            createdAt = Instant.now();
        }
        if (updatedAt == null) {
            updatedAt = Instant.now();
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }
}
