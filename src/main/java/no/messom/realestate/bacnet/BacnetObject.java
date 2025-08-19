package no.messom.realestate.bacnet;

import com.serotonin.bacnet4j.type.primitive.ObjectIdentifier;
import com.serotonin.bacnet4j.type.enumerated.ObjectType;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * Representerer et BACnet objekt med metadata
 */
public class BacnetObject {
    private final ObjectIdentifier objectIdentifier;
    private final ObjectType objectType;
    private final int instanceNumber;
    private String objectName;
    private String description;
    private Object presentValue;
    private String units;
    private LocalDateTime lastUpdated;
    private boolean covSupported;

    public BacnetObject(ObjectIdentifier objectIdentifier) {
        this.objectIdentifier = objectIdentifier;
        this.objectType = objectIdentifier.getObjectType();
        this.instanceNumber = objectIdentifier.getInstanceNumber();
        this.lastUpdated = LocalDateTime.now();
    }

    // Getters
    public ObjectIdentifier getObjectIdentifier() { return objectIdentifier; }
    public ObjectType getObjectType() { return objectType; }
    public int getInstanceNumber() { return instanceNumber; }
    public String getObjectName() { return objectName; }
    public String getDescription() { return description; }
    public Object getPresentValue() { return presentValue; }
    public String getUnits() { return units; }
    public LocalDateTime getLastUpdated() { return lastUpdated; }
    public boolean isCovSupported() { return covSupported; }

    // Setters
    public void setObjectName(String objectName) {
        this.objectName = objectName;
        this.lastUpdated = LocalDateTime.now();
    }

    public void setDescription(String description) {
        this.description = description;
        this.lastUpdated = LocalDateTime.now();
    }

    public void setPresentValue(Object presentValue) {
        this.presentValue = presentValue;
        this.lastUpdated = LocalDateTime.now();
    }

    public void setUnits(String units) {
        this.units = units;
        this.lastUpdated = LocalDateTime.now();
    }

    public void setCovSupported(boolean covSupported) {
        this.covSupported = covSupported;
        this.lastUpdated = LocalDateTime.now();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        BacnetObject that = (BacnetObject) o;
        return Objects.equals(objectIdentifier, that.objectIdentifier);
    }

    @Override
    public int hashCode() {
        return Objects.hash(objectIdentifier);
    }

    @Override
    public String toString() {
        return String.format("BacnetObject{%s:%d, name='%s', description='%s', value=%s}",
                objectType, instanceNumber, objectName, description, presentValue);
    }
}