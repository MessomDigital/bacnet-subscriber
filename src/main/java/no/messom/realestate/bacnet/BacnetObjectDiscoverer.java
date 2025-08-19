package no.messom.realestate.bacnet;

import com.serotonin.bacnet4j.LocalDevice;
import com.serotonin.bacnet4j.RemoteDevice;
import com.serotonin.bacnet4j.ServiceFuture;
import com.serotonin.bacnet4j.exception.BACnetException;
import com.serotonin.bacnet4j.service.acknowledgement.AcknowledgementService;
import com.serotonin.bacnet4j.service.acknowledgement.ReadPropertyAck;
import com.serotonin.bacnet4j.service.confirmed.ConfirmedRequestService;
import com.serotonin.bacnet4j.service.confirmed.ReadPropertyMultipleRequest;
import com.serotonin.bacnet4j.service.confirmed.ReadPropertyRequest;
import com.serotonin.bacnet4j.type.Encodable;
import com.serotonin.bacnet4j.type.constructed.PropertyReference;
import com.serotonin.bacnet4j.type.constructed.ReadAccessSpecification;
import com.serotonin.bacnet4j.type.constructed.SequenceOf;
import com.serotonin.bacnet4j.type.enumerated.ObjectType;
import com.serotonin.bacnet4j.type.enumerated.PropertyIdentifier;
import com.serotonin.bacnet4j.type.primitive.ObjectIdentifier;
import com.serotonin.bacnet4j.util.DiscoveryUtils;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.slf4j.LoggerFactory.getLogger;

/**
 * Discoverer for BACnet objekter fra remote device
 */
public class BacnetObjectDiscoverer {
    private static final Logger log = getLogger(BacnetObjectDiscoverer.class);

    private final LocalDevice localDevice;
    private final BacnetObjectRepository repository;

    public BacnetObjectDiscoverer(LocalDevice localDevice, BacnetObjectRepository repository) {
        this.localDevice = localDevice;
        this.repository = repository;
    }

    /**
     * Henter alle objekter fra remote device asynkront
     */
    public CompletableFuture<Void> discoverAllObjects(RemoteDevice remoteDevice) {
        return CompletableFuture.runAsync(() -> {
            if (remoteDevice == null) {
                log.error("Cannot discover objects - remoteDevice is null");
                throw new IllegalArgumentException("RemoteDevice cannot be null");
            }

            try {
                log.info("Starting object discovery for device: {}", remoteDevice.getInstanceNumber());

                // Først, hent objektlisten fra device
                List<ObjectIdentifier> objectList = getObjectList(remoteDevice);

                if (objectList.isEmpty()) {
                    log.warn("No objects found on device {}", remoteDevice.getInstanceNumber());
                    return;
                }

                log.info("Found {} objects on device {}", objectList.size(), remoteDevice.getInstanceNumber());

                // Deretter, hent properties for hvert objekt
                int successCount = 0;
                int failCount = 0;

                for (ObjectIdentifier objectId : objectList) {
                    try {
                        BacnetObject bacnetObject = createBacnetObject(remoteDevice, objectId);
                        if (bacnetObject != null) {
                            repository.addObject(bacnetObject);
                            successCount++;
                        } else {
                            failCount++;
                            log.warn("Failed to create BacnetObject for {}", objectId);
                        }
                    } catch (Exception e) {
                        failCount++;
                        log.warn("Failed to read object {}: {}", objectId, e.getMessage());
                    }
                }

                log.info("Object discovery completed for device: {} - Success: {}, Failed: {}",
                        remoteDevice.getInstanceNumber(), successCount, failCount);

                repository.logStatistics();

            } catch (Exception e) {
                log.error("Failed to discover objects from device: " + remoteDevice.getInstanceNumber(), e);
                throw new RuntimeException("Object discovery failed", e);
            }
        });
    }
//74355	960.187341748	192.168.2.233	192.168.2.28	BACnet-APDU	411	Complex-ACK     readProperty[  2]  (Message Reassembled)device,2640 object-list device,2640 analog-value,30503 analog-value,30504 analog-value,30505 analog-value,30506 analog-value,30507 analog-value,30508 analog-value,30509 analog-value,30791 analog-value,30792 analog-value,30794 analog-value,30795 analog-value,30811 analog-value,30813 analog-value,30814 analog-value,30836 analog-value,30839 analog-value,30866 analog-value,30867 analog-value,30869 analog-value,30870 analog-value,30872 analog-value,30873 analog-value,30875 analog-value,30876 analog-value,30878 analog-value,30879 analog-value,30881 analog-value,30882 analog-value,30884 analog-value,30885 analog-value,30887 analog-value,30888 analog-value,30889 analog-value,30892 analog-value,30893 analog-value,30894 analog-value,30897 analog-value,30898 analog-value,30899 analog-value,30908 analog-value,30909 analog-value,30914 analog-value,30915 analog-value,30916 analog-value,30917 analog-value,30918 analog-value,30919 analog-value,30976 analog-value,30977 analog-value,30978 analog-value,30979 analog-value,30987 analog-value,30988 analog-value,30989 analog-value,31000 analog-value,31001 analog-value,31002 analog-value,31003 analog-value,31004 analog-value,31009 analog-value,31010 analog-value,31018 analog-value,31029 analog-value,31030 analog-value,31031 analog-value,31032 analog-value,31033 analog-value,31034 analog-value,31054 analog-value,31073 analog-value,31074 analog-value,31090 analog-value,31091 analog-value,40250 analog-value,40251 analog-value,40252 analog-value,40253 analog-value,40254 analog-value,40255 analog-value,40256 analog-value,40257 analog-value,40258 analog-value,40259 analog-value,40260 analog-value,40261 analog-value,40262 analog-value,40263 analog-value,40264 analog-value,40265 analog-value,40266 analog-value,40267 analog-value,40268 analog-value,40269 analog-value,40270 analog-value,40271 analog-value,40272 analog-value,40273 analog-value,40292 analog-value,40318 analog-value,40402 analog-value,40403 analog-value,40404 analog-value,40405 analog-value,40406 analog-value,40407 analog-value,40408 analog-value,40409 analog-value,40410 analog-value,40411 analog-value,40412 analog-value,40413 analog-value,40414 analog-value,40415 analog-value,40416 analog-value,40427 analog-value,40431 analog-value,40432 analog-value,40433 analog-value,40434 analog-value,40435 analog-value,40436 analog-value,40439 analog-value,40440 analog-value,40441 analog-value,40449 analog-value,40450 analog-value,40459 analog-value,40460 analog-value,40461 analog-value,40462 analog-value,40463 analog-value,40464 analog-value,40465 analog-value,40466 analog-value,40467 analog-value,40471 analog-value,40473 analog-value,40474 analog-value,40475 binary-value,10000 binary-value,10001 binary-value,20001 binary-value,20007 binary-value,20008 binary-value,20009 binary-value,20010 binary-value,20011 binary-value,20012 binary-value,20013 binary-value,20014 binary-value,20015 binary-value,20016 binary-value,20017 binary-value,20018 binary-value,20019 binary-value,20020 binary-value,20021 binary-value,20022 binary-value,20023 binary-value,20024 binary-value,20025 binary-value,20026 binary-value,20027 binary-value,20028 binary-value,20029 binary-value,20030 binary-value,20031 binary-value,20032 binary-value,20033 binary-value,20034 binary-value,20035 binary-value,20036 binary-value,20037 binary-value,20038 binary-value,20039 binary-value,20040 binary-value,20041 binary-value,20042 binary-value,20043 binary-value,20044 binary-value,20045 binary-value,20046 binary-value,20047 binary-value,20048 binary-value,20049 binary-value,20050 binary-value,20051 binary-value,20052 binary-value,20053 binary-value,20054 binary-value,20055 binary-value,20056 binary-value,20057 binary-value,20058 binary-value,20059 binary-value,20060 binary-value,20061 binary-value,20062 binary-value,20063 binary-value,20064 binary-value,20065 binary-value,20066 binary-value,20067 binary-value,20068 binary-value,20069 binary-value,20070 binary-value,20071 binary-value,20072 binary-value,20073 binary-value,20074 binary-value,


    @SuppressWarnings("unchecked")
    private List<ObjectIdentifier> getObjectList(RemoteDevice remoteDevice) throws BACnetException {
        log.debug("Reading object list from device: {}", remoteDevice.getInstanceNumber());

        try {
            CompletableFuture<Object> future = new CompletableFuture<>();

            ReadPropertyRequest request = new ReadPropertyRequest(
                    remoteDevice.getObjectIdentifier(),
                    PropertyIdentifier.objectList
            );

            // Send request med callback
            ServiceFuture theFuture = localDevice.send(remoteDevice, request);
            AcknowledgementService response = theFuture.get();
            Object objectListArray = ((ReadPropertyAck) response).getValue();
            log.debug("Received class {}, objectListArray: {}", objectListArray.getClass(),objectListArray);
            if (objectListArray instanceof SequenceOf) {
                SequenceOf<ObjectIdentifier> objectList = (SequenceOf<ObjectIdentifier>) objectListArray;
                log.debug("Object list size: {}", objectList.getValues().size());
                future.complete(objectList);
            }

            /*
            localDevice.send(remoteDevice, request).whenComplete((response, throwable) -> {
                if (throwable != null) {
                    future.completeExceptionally(throwable);
                } else {
                    future.complete(response);
                }
            });

             */

            // Vent på svar med timeout
//            Object objectListProperty = future.get(10, TimeUnit.SECONDS);

            if (objectListArray == null) {
                log.warn("Object list property is null for device: {}", remoteDevice.getInstanceNumber());
                return new ArrayList<>();
            }

            if (objectListArray instanceof SequenceOf) {
                SequenceOf<ObjectIdentifier> objectList = (SequenceOf<ObjectIdentifier>) objectListArray;

                if (objectList.getValues() == null || objectList.getValues().isEmpty()) {
                    log.warn("Object list is empty for device: {}", remoteDevice.getInstanceNumber());
                    return new ArrayList<>();
                }

                List<ObjectIdentifier> result = new ArrayList<>(objectList.getValues());
                log.debug("Successfully read {} objects from device object list", result.size());

                // Oppdater RemoteDevice cache
                remoteDevice.setDeviceProperty(PropertyIdentifier.objectList, objectList);

                return result;

            } else {
                log.warn("Unexpected object list format for device {}: {} (expected SequenceOf<ObjectIdentifier>)",
                        remoteDevice.getInstanceNumber(),
                        objectListArray.getClass().getSimpleName());
                return new ArrayList<>();
            }

        } catch (Exception e) {
            log.error("Error reading object list from device: {}", remoteDevice.getInstanceNumber());
            throw new BACnetException("Failed to read object list", e);
        }
    }

    private BacnetObject createBacnetObject(RemoteDevice remoteDevice, ObjectIdentifier objectId) {
        if (objectId == null) {
            log.warn("Cannot create BacnetObject - objectId is null");
            return null;
        }

        BacnetObject bacnetObject = new BacnetObject(objectId);
        log.debug("Creating BacnetObject for: {}", objectId);

        // Lese objektnavn - dette er ofte påkrevd så vi prøver hardere
        readObjectProperty(remoteDevice, objectId, PropertyIdentifier.objectName)
                .ifPresentOrElse(
                        value -> bacnetObject.setObjectName(value.toString()),
                        () -> log.debug("No object name available for {}", objectId)
                );

        // Lese beskrivelse
        readObjectProperty(remoteDevice, objectId, PropertyIdentifier.description)
                .ifPresent(value -> bacnetObject.setDescription(value.toString()));

        // Lese present value
        readObjectProperty(remoteDevice, objectId, PropertyIdentifier.presentValue)
                .ifPresent(bacnetObject::setPresentValue);

        // Lese units
        readObjectProperty(remoteDevice, objectId, PropertyIdentifier.units)
                .ifPresent(value -> bacnetObject.setUnits(value.toString()));

        // Sjekke om objektet støtter COV
        bacnetObject.setCovSupported(supportsCOV(objectId.getObjectType()));

        return bacnetObject;
    }


    /**
     * Helper metode for å trygt lese properties med proper error handling
     */
    private Optional<Object> readObjectProperty(RemoteDevice remoteDevice, ObjectIdentifier objectId, PropertyIdentifier propertyId) {
        if (remoteDevice == null || objectId == null || propertyId == null) {
            return Optional.empty();
        }

        try {
            Object value = remoteDevice.getObjectProperty(objectId, propertyId);
            return Optional.ofNullable(value);
        } catch (Exception e) {
            log.debug("Unexpected error reading {} for {}: {}", propertyId, objectId, e.getMessage());
            return Optional.empty();
        }
    }

    private boolean supportsCOV(ObjectType objectType) {
        // Objekttyper som typisk støtter COV
        return objectType.equals(ObjectType.analogInput) ||
                objectType.equals(ObjectType.analogOutput) ||
                objectType.equals(ObjectType.analogValue) ||
                objectType.equals(ObjectType.binaryInput) ||
                objectType.equals(ObjectType.binaryOutput) ||
                objectType.equals(ObjectType.binaryValue) ||
                objectType.equals(ObjectType.multiStateInput) ||
                objectType.equals(ObjectType.multiStateOutput) ||
                objectType.equals(ObjectType.multiStateValue);
    }
}