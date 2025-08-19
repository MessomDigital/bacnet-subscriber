package no.messom.realestate.bacnet;

import com.serotonin.bacnet4j.LocalDevice;
import com.serotonin.bacnet4j.RemoteDevice;
import com.serotonin.bacnet4j.npdu.ip.IpNetwork;
import com.serotonin.bacnet4j.npdu.ip.IpNetworkBuilder;
import com.serotonin.bacnet4j.service.unconfirmed.WhoIsRequest;
import com.serotonin.bacnet4j.transport.DefaultTransport;
import com.serotonin.bacnet4j.type.enumerated.ObjectType;
import com.serotonin.bacnet4j.type.enumerated.PropertyIdentifier;
import com.serotonin.bacnet4j.type.primitive.ObjectIdentifier;
import com.serotonin.bacnet4j.util.RemoteDeviceDiscoverer;
import com.serotonin.bacnet4j.util.RemoteDeviceFinder;
import org.slf4j.Logger;

import java.util.concurrent.ScheduledExecutorService;

import static org.slf4j.LoggerFactory.getLogger;

public class BacnetCOVSubscriber {
    private static final Logger log = getLogger(BacnetCOVSubscriber.class);

    private static final int LOCAL_DEVICE_ID = 1235;
    private static final int REMOTE_DEVICE_ID = 2640;
    private static final String REMOTE_IP = "192.168.2.233";
    public static final String LOCAL_IP = "192.168.2.28";
    private static final int BACNET_PORT = 47808;
    private static final int COV_LIFETIME = 60; // seconds
    private static final ObjectIdentifier TARGET_OBJECT =
            new ObjectIdentifier(ObjectType.analogValue, 1);

    public static void main(String[] args) throws Exception {
        BacnetCOVSubscriber app = new BacnetCOVSubscriber();
        app.run();
    }

    public void run() throws Exception {
        LocalDevice localDevice = null;
        ScheduledExecutorService scheduler = null;

        try {
            IpNetwork ipNetwork = new IpNetworkBuilder()
                    .withLocalBindAddress(LOCAL_IP)
                    .withBroadcast("192.168.2.255", 32)
                    .build();
            localDevice = new LocalDevice(LOCAL_DEVICE_ID, new DefaultTransport(ipNetwork));
            localDevice.initialize();
            /*
            RemoteDeviceFinder.RemoteDeviceFuture remoteDeviceFuture = localDevice.getRemoteDevice(REMOTE_DEVICE_ID);
            RemoteDevice remoteDevice = remoteDeviceFuture.get();
            if (remoteDevice == null) {
                log.warn("Remote device with ID {} not found", REMOTE_DEVICE_ID);
                return;
            }

             */
            RemoteDeviceDiscoverer discoverer = new RemoteDeviceDiscoverer(localDevice);
            discoverer.start();
//            localDevice.sendGlobalBroadcast(new WhoIsRequest());

            Thread.sleep(5000); // Wait for discovery to complete
            log.info("Fund {} devices on the network", discoverer.getRemoteDevices().size());
            discoverer.stop();

            localDevice.sendLocalBroadcast(new WhoIsRequest());
            //Bacnet Objects discovery

            final RemoteDevice rd = new RemoteDevice(localDevice, REMOTE_DEVICE_ID);
            final ObjectIdentifier oid = new ObjectIdentifier(ObjectType.analogValue, 0);
            log.info("ModelName {} ",rd.getDeviceProperty(PropertyIdentifier.modelName));
            log.info("Description {} ",rd.getObjectProperty(oid, PropertyIdentifier.activeText));
            log.info("PresentValue {} ",rd.getObjectProperty(oid, PropertyIdentifier.presentValue));
//            localDevice.whoIs(REMOTE_DEVICE_ID, REMOTE_IP, BACNET_PORT);
            log.info("Local BACnet device initialized with ID: {}", LOCAL_DEVICE_ID);
        } catch (Exception e) {
            log.error("Failed to initialize local device", e);
            if (localDevice != null) {
                localDevice.terminate();
            }
            return;
        }
        log.info("Local BACnet device initialized with ID: {}", LOCAL_DEVICE_ID);
    }
}