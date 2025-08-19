package no.messom.realestate.bacnet;

import com.serotonin.bacnet4j.type.enumerated.ObjectType;
import com.serotonin.bacnet4j.type.primitive.ObjectIdentifier;
import org.slf4j.Logger;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import static org.slf4j.LoggerFactory.getLogger;

/**
 * Repository for BACnet objekter med søk og filter funktionalitet
 */
public class BacnetObjectRepository {
    private static final Logger log = getLogger(BacnetObjectRepository.class);

    private final Map<ObjectIdentifier, BacnetObject> objects = new ConcurrentHashMap<>();

    public void addObject(BacnetObject object) {
        objects.put(object.getObjectIdentifier(), object);
        log.debug("Added object to repository: {}", object);
    }

    public Optional<BacnetObject> findById(ObjectIdentifier objectId) {
        return Optional.ofNullable(objects.get(objectId));
    }

    public List<BacnetObject> findByType(ObjectType objectType) {
        return objects.values().stream()
                .filter(obj -> obj.getObjectType().equals(objectType))
                .collect(Collectors.toList());
    }

    public List<BacnetObject> findByNameContaining(String nameFragment) {
        return objects.values().stream()
                .filter(obj -> obj.getObjectName() != null &&
                        obj.getObjectName().toLowerCase().contains(nameFragment.toLowerCase()))
                .collect(Collectors.toList());
    }

    public List<BacnetObject> findCovSupportedObjects() {
        return objects.values().stream()
                .filter(BacnetObject::isCovSupported)
                .collect(Collectors.toList());
    }

    public List<BacnetObject> getAllObjects() {
        return new ArrayList<>(objects.values());
    }

    public int size() {
        return objects.size();
    }

    public void clear() {
        objects.clear();
        log.info("Repository cleared");
    }

    public Map<ObjectType, List<BacnetObject>> getObjectsByType() {
        return objects.values().stream()
                .collect(Collectors.groupingBy(BacnetObject::getObjectType));
    }

    public void logStatistics() {
        Map<ObjectType, List<BacnetObject>> byType = getObjectsByType();

        log.info("=== Repository Statistics ===");
        log.info("Total objects: {}", size());

        byType.entrySet().stream()
                .sorted(Map.Entry.<ObjectType, List<BacnetObject>>comparingByValue(
                        (a, b) -> Integer.compare(b.size(), a.size())))
                .forEach(entry ->
                        log.info("  {}: {} objects", entry.getKey(), entry.getValue().size()));

        long covSupportedCount = objects.values().stream()
                .mapToLong(obj -> obj.isCovSupported() ? 1 : 0)
                .sum();
        log.info("COV supported objects: {}", covSupportedCount);
        log.info("=== End Statistics ===");
    }
}