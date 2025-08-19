package no.messom.realestate.bacnet;

import com.serotonin.bacnet4j.LocalDevice;
import com.serotonin.bacnet4j.RemoteDevice;
import com.serotonin.bacnet4j.event.DeviceEventAdapter;
import com.serotonin.bacnet4j.npdu.ip.IpNetwork;
import com.serotonin.bacnet4j.npdu.ip.IpNetworkBuilder;
import com.serotonin.bacnet4j.service.confirmed.SubscribeCOVRequest;
import com.serotonin.bacnet4j.service.unconfirmed.WhoIsRequest;
import com.serotonin.bacnet4j.transport.DefaultTransport;
import com.serotonin.bacnet4j.type.constructed.*;
import com.serotonin.bacnet4j.type.enumerated.*;
import com.serotonin.bacnet4j.type.notificationParameters.NotificationParameters;
import com.serotonin.bacnet4j.type.primitive.Boolean;
import com.serotonin.bacnet4j.type.primitive.CharacterString;
import com.serotonin.bacnet4j.type.primitive.ObjectIdentifier;
import com.serotonin.bacnet4j.type.primitive.UnsignedInteger;
import com.serotonin.bacnet4j.util.DiscoveryUtils;
import com.serotonin.bacnet4j.util.RemoteDeviceDiscoverer;
import com.serotonin.bacnet4j.util.RemoteDeviceFinder;
import org.slf4j.Logger;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.slf4j.LoggerFactory.getLogger;

public class BacnetCOVSubscriber {
    private static final Logger log = getLogger(BacnetCOVSubscriber.class);

    private static final int LOCAL_DEVICE_ID = 1235;
    private static final int REMOTE_DEVICE_ID = 2640;
    private static final String REMOTE_IP = "192.168.2.233";
    public static final String LOCAL_IP = "0.0.0.0";
    private static final int BACNET_PORT = 47808;
    private static final int COV_LIFETIME = 3600; // seconds
    private static final ObjectIdentifier TARGET_OBJECT =
            new ObjectIdentifier(ObjectType.analogValue, 40250);

    private LocalDevice localDevice;
    private RemoteDevice remoteDevice;
    private final CountDownLatch shutdownLatch = new CountDownLatch(1);
    private BacnetObjectRepository repository;
    private BacnetObjectDiscoverer discoverer;

    public static void main(String[] args) throws Exception {
        BacnetCOVSubscriber app = new BacnetCOVSubscriber();

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            log.info("Shutting down BACnet subscriber...");
            app.shutdown();
        }));

        app.run();
    }

    public void run() throws Exception {
        try {
            initializeLocalDevice();
            setupRepository();
            setupEventHandling();
            discoverRemoteDevice();
            discoverAllObjects();
            setupCOVSubscription();

            log.info("BACnet COV Subscriber is running. Press Ctrl+C to stop.");
            shutdownLatch.await();

        } catch (Exception e) {
            log.error("Failed to run BACnet subscriber", e);
        } finally {
            shutdown();
        }
    }

    private void setupRepository() {
        repository = new BacnetObjectRepository();
        discoverer = new BacnetObjectDiscoverer(localDevice, repository);
        log.info("Repository and discoverer initialized");
    }

    private void discoverAllObjects() throws Exception {
        if (remoteDevice == null) {
            throw new IllegalStateException("Cannot discover objects - remote device not available");
        }

        log.info("Starting object discovery...");
        DiscoveryUtils.getExtendedDeviceInformation(localDevice, remoteDevice);
        discoverer.discoverAllObjects(remoteDevice)
                .get(30, TimeUnit.SECONDS); // Timeout etter 30 sekunder

        // Vis noen eksempler på fundne objekter
        logDiscoveredObjects();
    }

    private void logDiscoveredObjects() {
        log.info("=== Discovered Objects Examples ===");

        // Vis alle objekttyper
        repository.getObjectsByType().forEach((type, objects) ->
                log.info("Found {} {} objects", objects.size(), type));

        // Vis første 5 objekter med navn
        repository.getAllObjects().stream()
                .filter(obj -> obj.getObjectName() != null)
                .limit(5)
                .forEach(obj -> log.info("Example: {} = {}",
                        obj.getObjectIdentifier(), obj.getObjectName()));

        log.info("=== End Examples ===");
    }

    private void initializeLocalDevice() throws Exception {
        IpNetwork ipNetwork = new IpNetworkBuilder()
                .withLocalBindAddress(LOCAL_IP)
                .withLocalNetworkNumber(LOCAL_DEVICE_ID)
                .withBroadcast("192.168.2.255", 24)
                .build();

        localDevice = new LocalDevice(LOCAL_DEVICE_ID, new DefaultTransport(ipNetwork));
        localDevice.initialize();
        log.info("Local BACnet device initialized with ID: {}", LOCAL_DEVICE_ID);
    }

    private void setupEventHandling() {
        localDevice.getEventHandler().addListener(new DeviceEventAdapter() {



            @Override
            public void covNotificationReceived(UnsignedInteger subscriberProcessIdentifier,
                                                ObjectIdentifier initiatingDevice,
                                                ObjectIdentifier monitoredObjectIdentifier,
                                                UnsignedInteger timeRemaining,
                                                SequenceOf<PropertyValue> listOfValues) {

                String timestamp = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);

                log.info("=== COV Notification Received ===");
                log.info("Timestamp: {}", timestamp);
                log.info("From device: {} ({})",
                        initiatingDevice.getInstanceNumber(),
                        initiatingDevice.getObjectType());
                log.info("Object: {}", monitoredObjectIdentifier);
                log.info("Time remaining: {} seconds", timeRemaining);
                log.info("Subscriber Process ID: {}", subscriberProcessIdentifier);

                // Håndter property values
                if (listOfValues != null) {
                    log.info("Property values:");
                    for (PropertyValue pv : listOfValues) {
                        log.info("  {} = {}",
                                pv.getPropertyIdentifier(),
                                pv.getValue());
                    }
                } else {
                    log.info("No property values in notification");
                }

                // Her kan du legge til egen forretningslogikk for å behandle COV-dataene
                processCOVData(monitoredObjectIdentifier, listOfValues);

                log.info("=== End COV Notification ===");
            }

            @Override
            public void textMessageReceived(ObjectIdentifier textMessageSourceDevice, Choice messageClass, MessagePriority messagePriority, CharacterString message) {
                super.textMessageReceived(textMessageSourceDevice, messageClass, messagePriority, message);
                log.info("Text message received from device: {}",
                        textMessageSourceDevice.getInstanceNumber());
            }

            @Override
            public void eventNotificationReceived(UnsignedInteger processIdentifier, ObjectIdentifier initiatingDeviceIdentifier, ObjectIdentifier eventObjectIdentifier, TimeStamp timeStamp, UnsignedInteger notificationClass, UnsignedInteger priority, EventType eventType, CharacterString messageText, NotifyType notifyType, Boolean ackRequired, EventState fromState, EventState toState, NotificationParameters eventValues) {
                super.eventNotificationReceived(processIdentifier, initiatingDeviceIdentifier, eventObjectIdentifier, timeStamp, notificationClass, priority, eventType, messageText, notifyType, ackRequired, fromState, toState, eventValues);
            }

            @Override
            public void synchronizeTime(Address from, DateTime dateTime, boolean utc) {
                super.synchronizeTime(from, dateTime, utc);
                log.info("Time synchronization request from device: {}-description: {}",
                        from.getNetworkNumber(), from.getDescription());
            }


        });

        log.info("Event handler configured for COV notifications");
    }

    private void processCOVData(ObjectIdentifier objectId, SequenceOf<PropertyValue> listOfValues) {
        // Her implementerer du din egen logikk for å behandle COV-dataene
        // For eksempel:
        // - Lagre data til database
        // - Send data videre til andre systemer
        // - Trigger alerts basert på verdier
        // - osv.

        log.info("Processing COV data for object: {}", objectId);

        if (listOfValues != null) {
            for (PropertyValue pv : listOfValues) {
                if (PropertyIdentifier.presentValue.equals(pv.getPropertyIdentifier())) {
                    log.info("Present Value changed to: {}", pv.getValue());
                    // Eksempel: Send alert hvis verdi er over en terskel
                    // checkThresholdAlert(pv.getValue());
                }
            }
        }
    }

    private void discoverRemoteDevice() throws Exception {
        log.info("Starting device discovery...");

        RemoteDeviceDiscoverer discoverer = new RemoteDeviceDiscoverer(localDevice);
        discoverer.start();

        localDevice.sendGlobalBroadcast(new WhoIsRequest());
        Thread.sleep(10000);

        log.info("Found {} devices on the network", discoverer.getRemoteDevices().size());

        remoteDevice = null;
        for (RemoteDevice rd : discoverer.getRemoteDevices()) {
            log.info("Found device: ID={}, Address={}", rd.getInstanceNumber(), rd.getAddress());
            if (rd.getInstanceNumber() == REMOTE_DEVICE_ID) {
                remoteDevice = rd;
                break;
            }
        }

        discoverer.stop();

        if (remoteDevice == null) {
            log.warn("Target device {} not found via discovery, trying manual approach", REMOTE_DEVICE_ID);
            RemoteDeviceFinder.RemoteDeviceFuture remoteDeviceFuture =
                    localDevice.getRemoteDevice(REMOTE_DEVICE_ID);
            remoteDevice = remoteDeviceFuture.get(5000);

            if (remoteDevice == null) {
                throw new RuntimeException("Failed to find remote device with ID " + REMOTE_DEVICE_ID);
            }
        }

        log.info("Target remote device found: ID={}, Address={}",
                remoteDevice.getInstanceNumber(), remoteDevice.getAddress());
    }

    private void setupCOVSubscription() throws Exception {
        if (remoteDevice == null) {
            throw new IllegalStateException("Cannot setup COV subscription - remote device not available");
        }

        try {
            // Test connectivity
            log.info("Testing connectivity to remote device...");
            Object modelName = remoteDevice.getDeviceProperty(PropertyIdentifier.modelName);
            log.info("Remote device model: {}", modelName);

            Object presentValue = remoteDevice.getObjectProperty(TARGET_OBJECT, PropertyIdentifier.presentValue);
            log.info("Target object present value: {}", presentValue);

            // Subscribe to COV
            log.info("Setting up COV subscription for object: {}", TARGET_OBJECT);
            SubscribeCOVRequest covRequest = new SubscribeCOVRequest(
                    new UnsignedInteger(1), // subscriber process identifier
                    TARGET_OBJECT,
                    Boolean.TRUE, // confirmed notifications
                    new UnsignedInteger(COV_LIFETIME)
            );

            localDevice.send(remoteDevice, covRequest);
            log.info("COV subscription request sent successfully");
            log.info("Waiting for COV notifications...");

        } catch (Exception e) {
            log.error("Failed to setup COV subscription", e);
            throw e;
        }
    }

    private void shutdown() {
        try {
            if (remoteDevice != null && localDevice != null) {
                log.info("Unsubscribing from COV...");
                SubscribeCOVRequest unsubscribeRequest = new SubscribeCOVRequest(
                        new UnsignedInteger(1),
                        TARGET_OBJECT,
                        Boolean.TRUE,
                        new UnsignedInteger(0) // lifetime = 0 means unsubscribe
                );
                localDevice.send(remoteDevice, unsubscribeRequest);
            }
        } catch (Exception e) {
            log.warn("Error during COV unsubscription", e);
        }

        if (localDevice != null) {
            localDevice.terminate();
            log.info("Local device terminated");
        }

        shutdownLatch.countDown();
    }
}