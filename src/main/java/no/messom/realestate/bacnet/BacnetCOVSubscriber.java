package no.messom.realestate.bacnet;

import com.serotonin.bacnet4j.LocalDevice;
import com.serotonin.bacnet4j.RemoteDevice;
import com.serotonin.bacnet4j.event.DeviceEventAdapter;
import com.serotonin.bacnet4j.npdu.ip.IpNetwork;
import com.serotonin.bacnet4j.npdu.ip.IpNetworkBuilder;
import com.serotonin.bacnet4j.service.confirmed.SubscribeCOVRequest;
import com.serotonin.bacnet4j.service.unconfirmed.WhoIsRequest;
import com.serotonin.bacnet4j.transport.DefaultTransport;
import com.serotonin.bacnet4j.type.enumerated.ObjectType;
import com.serotonin.bacnet4j.type.enumerated.PropertyIdentifier;
import com.serotonin.bacnet4j.type.primitive.Boolean;
import com.serotonin.bacnet4j.type.primitive.ObjectIdentifier;
import com.serotonin.bacnet4j.type.primitive.UnsignedInteger;
import com.serotonin.bacnet4j.util.RemoteDeviceDiscoverer;
import com.serotonin.bacnet4j.util.RemoteDeviceFinder;
import org.apache.commons.lang3.mutable.MutableObject;
import org.slf4j.Logger;

import java.util.concurrent.CountDownLatch;

import static org.slf4j.LoggerFactory.getLogger;

public class BacnetCOVSubscriber {
    private static final Logger log = getLogger(BacnetCOVSubscriber.class);

    private static final int LOCAL_DEVICE_ID = 1235;
    private static final int REMOTE_DEVICE_ID = 2640;
    private static final String REMOTE_IP = "192.168.2.233";
    public static final String LOCAL_IP = "0.0.0.0"; //"192.168.2.28";
    private static final int BACNET_PORT = 47808;
    private static final int COV_LIFETIME = 60; // seconds
    private static final ObjectIdentifier TARGET_OBJECT =
            new ObjectIdentifier(ObjectType.analogValue,40250);

    private LocalDevice localDevice;
    private RemoteDevice remoteDevice;
    private final CountDownLatch shutdownLatch = new CountDownLatch(1);

    public static void main(String[] args) throws Exception {
        BacnetCOVSubscriber app = new BacnetCOVSubscriber();

        // Add shutdown hook for graceful cleanup
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            log.info("Shutting down BACnet subscriber...");
            app.shutdown();
        }));

        app.run();
    }

    public void run() throws Exception {
        try {
            initializeLocalDevice();
            discoverRemoteDevice();
            setupCOVSubscription();

            log.info("BACnet COV Subscriber is running. Press Ctrl+C to stop.");
            shutdownLatch.await(); // Keep the application running

        } catch (Exception e) {
            log.error("Failed to run BACnet subscriber", e);
        } finally {
            shutdown();
        }
    }

    private void initializeLocalDevice() throws Exception {

        IpNetwork ipNetwork = new IpNetworkBuilder()
                .withLocalBindAddress(LOCAL_IP)
                .withLocalNetworkNumber(LOCAL_DEVICE_ID)
                .withBroadcast("192.168.2.255", 24) // Fixed subnet mask
                .build();

        localDevice = new LocalDevice(LOCAL_DEVICE_ID, new DefaultTransport(ipNetwork));
        /*
        localDevice.getEventHandler().addListener(deviceEventListener -> {
            log.info("Device event received: {}", deviceEventListener);
        });

        // Add event listener to handle incoming BACnet events
        localDevice.getEventHandler().addListener(new DeviceEventAdapter() {
            @Override
            public void covNotificationReceived(UnsignedInteger subscriberProcessIdentifier,
                                                RemoteDevice initiatingDevice,
                                                ObjectIdentifier monitoredObjectIdentifier,
                                                UnsignedInteger timeRemaining,
                                                COVNotificationRequest request) {
                log.info("COV Notification received!");
                log.info("  From device: {}", initiatingDevice.getInstanceNumber());
                log.info("  Object: {}", monitoredObjectIdentifier);
                log.info("  Time remaining: {} seconds", timeRemaining);
                log.info("  Property values: {}", request.getListOfValues());
            }

            @Override
            public void textMessageReceived(RemoteDevice textMessageSourceDevice,
                                            COVNotificationRequest request) {
                log.info("Text message received from device: {}", textMessageSourceDevice.getInstanceNumber());
            }
        });

         */


        localDevice.initialize();
        log.info("Local BACnet device initialized with ID: {}", LOCAL_DEVICE_ID);
    }

    private void discoverRemoteDevice() throws Exception {
        log.info("Starting device discovery...");

        RemoteDeviceDiscoverer discoverer = new RemoteDeviceDiscoverer(localDevice);
        discoverer.start();

        // Send Who-Is request
        localDevice.sendGlobalBroadcast(new WhoIsRequest());

        // Wait longer for discovery
        Thread.sleep(10000);

        log.info("Found {} devices on the network", discoverer.getRemoteDevices().size());

        // Try to find our target device
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
            // Try manual approach if discovery failed
            log.warn("Target device {} not found via discovery, trying manual approach", REMOTE_DEVICE_ID);
            remoteDevice = new RemoteDevice(localDevice, REMOTE_DEVICE_ID);
            // You might need to set the address manually here
            RemoteDeviceFinder.RemoteDeviceFuture remoteDeviceFuture = localDevice.getRemoteDevice(REMOTE_DEVICE_ID);
            remoteDevice = remoteDeviceFuture.get(5000); // Wait up to 5 seconds for the device to be found
            if (remoteDevice == null) {
                log.error("Failed to find remote device with ID {}", REMOTE_DEVICE_ID);
            } else {
                log.info("Remote device found: ID={}, Address={}", remoteDevice.getInstanceNumber(), remoteDevice.getAddress());
            }
        }

        log.info("Target remote device: {}", remoteDevice != null ? remoteDevice.getInstanceNumber() : "NOT FOUND");
    }

    private void setupCOVSubscription() throws Exception {
        if (remoteDevice == null) {
            log.error("Cannot setup COV subscription - remote device not available");
            return;
        }

        try {
            // Read some basic properties first to verify connectivity
            log.info("Testing connectivity to remote device...");
            Object modelName = remoteDevice.getDeviceProperty(PropertyIdentifier.modelName);
            log.info("Remote device model: {}", modelName);

            // Try to read the target object's present value
            Object presentValue = remoteDevice.getObjectProperty(TARGET_OBJECT, PropertyIdentifier.presentValue);
            log.info("Target object present value: {}", presentValue);

            // Subscribe to COV for the target object
            log.info("Setting up COV subscription for object: {}", TARGET_OBJECT);
            SubscribeCOVRequest covRequest = new SubscribeCOVRequest(
                    new UnsignedInteger(1), // subscriber process identifier
                    TARGET_OBJECT,
                    Boolean.TRUE, // confirmed notifications
                    new UnsignedInteger(COV_LIFETIME)
            );

            localDevice.send(remoteDevice, covRequest);
            log.info("COV subscription request sent successfully");

        } catch (Exception e) {
            log.error("Failed to setup COV subscription", e);
            throw e;
        }
    }

    private void shutdown() {
        try {
            if (remoteDevice != null && localDevice != null) {
                // Unsubscribe from COV
                log.info("Unsubscribing from COV...");
                SubscribeCOVRequest unsubscribeRequest = new SubscribeCOVRequest(
                        new UnsignedInteger(1),
                        TARGET_OBJECT,
                        Boolean.TRUE, // confirmed notifications
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