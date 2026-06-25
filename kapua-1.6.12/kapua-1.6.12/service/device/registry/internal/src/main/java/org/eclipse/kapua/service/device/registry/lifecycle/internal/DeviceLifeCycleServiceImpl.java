/*******************************************************************************
 * Copyright (c) 2016, 2022 Eurotech and/or its affiliates and others
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *     Eurotech - initial API and implementation
 *******************************************************************************/
package org.eclipse.kapua.service.device.registry.lifecycle.internal;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Strings;
import org.eclipse.kapua.KapuaEntityNotFoundException;
import org.eclipse.kapua.KapuaException;
import org.eclipse.kapua.KapuaOptimisticLockingException;
import org.eclipse.kapua.commons.security.KapuaSecurityUtils;
import org.eclipse.kapua.locator.KapuaLocator;
import org.eclipse.kapua.locator.KapuaProvider;
import org.eclipse.kapua.message.KapuaPosition;
import org.eclipse.kapua.message.device.lifecycle.KapuaAppsMessage;
import org.eclipse.kapua.message.device.lifecycle.KapuaBirthChannel;
import org.eclipse.kapua.message.device.lifecycle.KapuaBirthMessage;
import org.eclipse.kapua.message.device.lifecycle.KapuaBirthPayload;
import org.eclipse.kapua.message.device.lifecycle.KapuaDisconnectMessage;
import org.eclipse.kapua.message.device.lifecycle.KapuaLifecycleMessage;
import org.eclipse.kapua.message.device.lifecycle.KapuaMissingMessage;
import org.eclipse.kapua.message.internal.device.lifecycle.model.BirthExtendedProperties;
import org.eclipse.kapua.message.internal.device.lifecycle.model.BirthExtendedProperty;
import org.eclipse.kapua.model.KapuaEntity;
import org.eclipse.kapua.model.id.KapuaId;
import org.eclipse.kapua.service.account.Account;
import org.eclipse.kapua.service.account.AccountService;
import org.eclipse.kapua.service.device.management.message.response.KapuaResponseCode;
import org.eclipse.kapua.service.device.registry.Device;
import org.eclipse.kapua.service.device.registry.DeviceExtendedProperty;
import org.eclipse.kapua.service.device.registry.DeviceRegistryService;
import org.eclipse.kapua.service.device.registry.connection.DeviceConnection;
import org.eclipse.kapua.service.device.registry.event.DeviceEvent;
import org.eclipse.kapua.service.device.registry.event.DeviceEventCreator;
import org.eclipse.kapua.service.device.registry.event.DeviceEventFactory;
import org.eclipse.kapua.service.device.registry.event.DeviceEventService;
import org.eclipse.kapua.service.device.registry.internal.DeviceExtendedPropertyImpl;
import org.eclipse.kapua.service.device.registry.lifecycle.DeviceLifeCycleService;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.checkerframework.checker.nullness.qual.Nullable;
import javax.validation.constraints.NotNull;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * {@link DeviceLifeCycleService} implementation.
 *
 * @since 1.0.0
 */
@KapuaProvider
public class DeviceLifeCycleServiceImpl implements DeviceLifeCycleService {

    private static final Logger LOG = LoggerFactory.getLogger(DeviceLifeCycleServiceImpl.class);

    private static final int MAX_RETRY = 3;
    private static final double MAX_WAIT = 500d;

    private static final ObjectMapper JSON_MAPPER = new ObjectMapper()
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);

    private static final KapuaLocator LOCATOR = KapuaLocator.getInstance();

    private static final DeviceEventService DEVICE_EVENT_SERVICE = LOCATOR.getService(DeviceEventService.class);
    private static final DeviceEventFactory DEVICE_EVENT_FACTORY = LOCATOR.getFactory(DeviceEventFactory.class);

    private static final DeviceRegistryService DEVICE_REGISTRY_SERVICE = LOCATOR.getService(DeviceRegistryService.class);

    private static final String KAPUA_API_URL =
            System.getProperty("kapua.api.internal.url", "http://kapua-api:8080");
    private static final String BIRTH_REPLY_BROKER_URL =
            System.getProperty("kapua.broker.internal.url", "tcp://localhost:1883");

    private static volatile String cachedJwt = null;
    private static volatile long   jwtExpiresAtMs = 0L;
    // Lazy to avoid ExceptionInInitializerError if AccountService isn't yet registered
    private static volatile AccountService accountServiceLazy = null;

    @Override
    public void birth(KapuaId connectionId, KapuaBirthMessage birthMessage) throws KapuaException {

        KapuaId scopeId = birthMessage.getScopeId();
        KapuaId deviceId = birthMessage.getDeviceId();

        KapuaBirthPayload birthPayload = birthMessage.getPayload();
        KapuaBirthChannel birthChannel = birthMessage.getChannel();

        //
        // Device update
        Device device;
        if (deviceId == null) {
            device = KapuaSecurityUtils.doPrivileged(() ->
                    DEVICE_REGISTRY_SERVICE.findByClientId(scopeId, birthChannel.getClientId()));
            if (device == null) {
                LOG.warn("Ignoring BIRTH for unregistered device scopeId={} clientId={}",
                        scopeId, birthChannel.getClientId());
                return;
            }
            deviceId = device.getId();
            birthMessage.setDeviceId(deviceId);
            LOG.info("Resolved registered device for BIRTH scopeId={} clientId={} deviceId={}",
                    scopeId, birthChannel.getClientId(), deviceId);
            device = updateDeviceInfoFromMessage(scopeId, deviceId, birthPayload, connectionId);
        } else {
            device = updateDeviceInfoFromMessage(scopeId, deviceId, birthPayload, connectionId);
        }

        //
        // Event create
        createLifecycleEvent(device, "BIRTH", birthMessage);

        // Publish BIRTH/Reply so device receives a Kapua JWT and becomes operational
        try {
            final KapuaId fScopeId = scopeId;
            if (accountServiceLazy == null) {
                accountServiceLazy = LOCATOR.getService(AccountService.class);
            }
            final AccountService svc = accountServiceLazy;
            Account account = KapuaSecurityUtils.doPrivileged(() -> svc.find(fScopeId));
            if (account != null) {
                String jwt = getOrRefreshJwt();
                if (jwt != null) {
                    String replyTopic = "$EDC/" + account.getName() + "/"
                            + birthChannel.getClientId() + "/MQTT/BIRTH/Reply";
                    String scopeIdCompact = scopeId.toCompactId();
                    String payload = "{\"accessToken\":\"" + jwt
                            + "\",\"scopeId\":\"" + scopeIdCompact + "\"}";
                    publishBirthReply(replyTopic, payload.getBytes(StandardCharsets.UTF_8));
                }
            }
        } catch (Throwable e) {
            LOG.warn("BIRTH/Reply error ({}) for clientId={}: {}", e.getClass().getName(), birthChannel.getClientId(), e.getMessage(), e);
        }
        triggerDeviceInitAsync(scopeId, deviceId);
    }

    private static synchronized String getOrRefreshJwt() {
        if (cachedJwt != null && System.currentTimeMillis() < jwtExpiresAtMs - 60_000L) {
            return cachedJwt;
        }
        try {
            URL url = new URL(KAPUA_API_URL + "/v1/authentication/user");
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setDoOutput(true);
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(5000);
            conn.getOutputStream().write(
                    "{\"username\":\"kapua-sys\",\"password\":\"kapua-password\"}".getBytes(StandardCharsets.UTF_8));
            if (conn.getResponseCode() == 200) {
                try (InputStream is = conn.getInputStream()) {
                    java.io.ByteArrayOutputStream buf = new java.io.ByteArrayOutputStream();
                    byte[] tmp = new byte[4096];
                    int n;
                    while ((n = is.read(tmp)) >= 0) { buf.write(tmp, 0, n); }
                    String resp = buf.toString("UTF-8");
                    int ti = resp.indexOf("\"tokenId\"");
                    if (ti >= 0) {
                        int s = resp.indexOf('"', ti + 10) + 1;
                        int e = resp.indexOf('"', s);
                        cachedJwt = resp.substring(s, e);
                        jwtExpiresAtMs = System.currentTimeMillis() + 25 * 60 * 1000L; // 25 min
                        LOG.info("Refreshed Kapua JWT for BIRTH/Reply (expires in 25 min)");
                        return cachedJwt;
                    }
                }
            }
            conn.disconnect();
        } catch (Exception e) {
            LOG.warn("Failed to fetch Kapua JWT from {}: {}", KAPUA_API_URL, e.getMessage());
        }
        return null;
    }

    private static void publishBirthReply(String topic, byte[] payload) {
        // Connect via the internalMqtt connector (port 1893) which bypasses the security filter:
        // isPassThroughConnection() and isInternalConnector() both return true for it.
        String internalBrokerUrl = "tcp://localhost:1893";
        String clientId = "birth-reply-" + Thread.currentThread().getId()
                + "-" + (System.currentTimeMillis() % 100000);
        try {
            MqttClient client = new MqttClient(internalBrokerUrl, clientId, new MemoryPersistence());
            MqttConnectOptions opts = new MqttConnectOptions();
            opts.setUserName("internalUsername");
            opts.setPassword("internalPassword".toCharArray());
            opts.setCleanSession(true);
            opts.setConnectionTimeout(5);
            client.connect(opts);
            MqttMessage msg = new MqttMessage(payload);
            msg.setQos(1);
            msg.setRetained(false);
            client.publish(topic, msg);
            client.disconnect();
            client.close();
            LOG.info("Published BIRTH/Reply ({} bytes) to topic={}", payload.length, topic);
        } catch (Throwable e) {
            LOG.warn("Failed to publish BIRTH/Reply ({}) to topic={}: {}", e.getClass().getName(), topic, e.getMessage(), e);
        }
    }

    private static void triggerDeviceInitAsync(KapuaId scopeId, KapuaId deviceId) {
        final String scopeCompact = scopeId.toCompactId();
        final String deviceCompact = deviceId.toCompactId();
        Thread t = new Thread(() -> {
            try { Thread.sleep(3000); } catch (InterruptedException e) { Thread.currentThread().interrupt(); return; }
            try {
                String jwt = getOrRefreshJwt();
                if (jwt == null) {
                    LOG.warn("Device init: no JWT available for scopeId={} deviceId={}", scopeCompact, deviceCompact);
                    return;
                }
                URL url = new URL(KAPUA_API_URL + "/v1/" + scopeCompact
                        + "/devices/" + deviceCompact + "/digitalsignage/_init_device");
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Authorization", "Bearer " + jwt);
                conn.setRequestProperty("Content-Type", "application/json");
                conn.setConnectTimeout(5000);
                conn.setReadTimeout(30000);
                int code = conn.getResponseCode();
                LOG.info("Device init on first birth scopeId={} deviceId={} HTTP={}",
                        scopeCompact, deviceCompact, code);
                conn.disconnect();
            } catch (Exception e) {
                LOG.warn("Failed to trigger device init for scopeId={} deviceId={}: {}",
                        scopeCompact, deviceCompact, e.getMessage());
            }
        }, "signage-init-" + deviceCompact);
        t.setDaemon(true);
        t.start();
    }

    @Override
    public void applications(KapuaId connectionId, KapuaAppsMessage message) throws KapuaException {

        Device device = updateDeviceInfoFromMessage(message.getScopeId(), message.getDeviceId(), message.getPayload(), connectionId);

        //
        // Event create
        createLifecycleEvent(device, "APPLICATION", message);
    }

    @Override
    public void missing(KapuaId connectionId, KapuaMissingMessage message) throws KapuaException {
        KapuaId scopeId = message.getScopeId();
        KapuaId deviceId = message.getDeviceId();

        //
        // Event create
        createLifecycleEvent(scopeId, deviceId, "MISSING", message);
    }

    @Override
    public void death(KapuaId connectionId, KapuaDisconnectMessage message) throws KapuaException {
        KapuaId scopeId = message.getScopeId();
        KapuaId deviceId = message.getDeviceId();

        //
        // Event create
        createLifecycleEvent(scopeId, deviceId, "DEATH", message);
    }

    /**
     * Updates the {@link Device} infos from the {@link KapuaBirthPayload} (or {@link org.eclipse.kapua.message.device.lifecycle.KapuaAppsPayload}.
     * <p>
     * Tries to update the {@link Device} a number of times to allow close device updates to be stored properly.
     *
     * @param scopeId      The {@link Device#getScopeId()} to update.
     * @param deviceId     The {@link Device#getId()} to update.
     * @param birthPayload The {@link KapuaBirthPayload} from which extract data.
     * @param connectionId The {@link DeviceConnection#getId()}
     * @return The updated {@link Device}.
     * @throws KapuaException If {@link Device} does not exists or {@link DeviceRegistryService#update(KapuaEntity)} causes an error.
     * @since 1.2.0
     */
    private Device updateDeviceInfoFromMessage(KapuaId scopeId, KapuaId deviceId, KapuaBirthPayload birthPayload, KapuaId connectionId) throws KapuaException {

        Device device = null;

        int retry = 0;
        do {
            retry++;

            try {
                device = DEVICE_REGISTRY_SERVICE.find(scopeId, deviceId);

                if (device == null) {
                    throw new KapuaEntityNotFoundException(Device.TYPE, deviceId);
                }

                // If the BirthMessage does not contain a 'Display Name' keep the one registered on the DeviceRegistryService.
                if (!Strings.isNullOrEmpty(birthPayload.getDisplayName())) {
                    device.setDisplayName(birthPayload.getDisplayName());
                }

                device.setSerialNumber(birthPayload.getSerialNumber());
                device.setModelId(birthPayload.getModelId());
                device.setModelName(birthPayload.getModelName());
                device.setImei(birthPayload.getModemImei());
                device.setImsi(birthPayload.getModemImsi());
                device.setIccid(birthPayload.getModemIccid());
                device.setBiosVersion(birthPayload.getBiosVersion());
                device.setFirmwareVersion(birthPayload.getFirmwareVersion());
                device.setOsVersion(birthPayload.getOsVersion());
                device.setJvmVersion(birthPayload.getJvmVersion());
                device.setOsgiFrameworkVersion(birthPayload.getContainerFrameworkVersion());
                device.setApplicationFrameworkVersion(birthPayload.getApplicationFrameworkVersion());
                device.setConnectionInterface(birthPayload.getConnectionInterface());
                device.setConnectionIp(birthPayload.getConnectionIp());
                device.setApplicationIdentifiers(birthPayload.getApplicationIdentifiers());
                device.setAcceptEncoding(birthPayload.getAcceptEncoding());

                device.setExtendedProperties(buildDeviceExtendedPropertyFromBirth(birthPayload.getExtendedProperties()));

                // issue #57
                device.setConnectionId(connectionId);

                device = DEVICE_REGISTRY_SERVICE.update(device);
                break;
            } catch (KapuaOptimisticLockingException e) {
                LOG.warn("Concurrent update for device: {}... Attempt: {}/{}. {}", device.getClientId(), retry, MAX_RETRY, retry < MAX_RETRY ? "Retrying..." : "Raising exception!");

                if (retry < MAX_RETRY) {
                    try {
                        Thread.sleep((long) (Math.random() * MAX_WAIT));
                    } catch (InterruptedException e1) {
                        LOG.warn("Error while waiting retry: {}", e.getMessage());
                    }
                } else {
                    throw e;
                }
            }
        }
        while (true);

        return device;
    }


    /**
     * Creates a {@link DeviceEvent} from the {@link KapuaLifecycleMessage}.
     * <p>
     * Internally invokes {@link DeviceRegistryService#find(KapuaId, KapuaId)} with the given parameters and
     * then uses {@link #createLifecycleEvent(Device, String, KapuaLifecycleMessage)}
     *
     * @param scopeId  The {@link Device#getScopeId()} of the {@link Device} that generated the {@link KapuaLifecycleMessage}.
     * @param deviceId The {@link Device#getId()} of the {@link Device} that generated the {@link KapuaLifecycleMessage}.
     * @param resource The resource used to publish the {@link KapuaLifecycleMessage}
     * @param message  The {@link KapuaLifecycleMessage} from which to extranct data.
     * @throws KapuaException if storing the {@link DeviceEvent} throws a {@link KapuaException}
     * @since 1.2.0
     */
    private DeviceEvent createLifecycleEvent(@NotNull KapuaId scopeId, KapuaId deviceId, @NotNull String resource, @NotNull KapuaLifecycleMessage<?, ?> message) throws KapuaException {

        Device device = DEVICE_REGISTRY_SERVICE.find(scopeId, deviceId);

        return createLifecycleEvent(device, resource, message);
    }

    /**
     * Creates a {@link DeviceEvent} from the {@link KapuaLifecycleMessage}.
     *
     * @param device   The {@link Device} that generated the {@link KapuaLifecycleMessage}.
     * @param resource The resource used to publish the {@link KapuaLifecycleMessage}
     * @param message  The {@link KapuaLifecycleMessage} from which to extranct data.
     * @throws KapuaException if storing the {@link DeviceEvent} throws a {@link KapuaException}
     * @since 1.2.0
     */
    private DeviceEvent createLifecycleEvent(@NotNull Device device, @NotNull String resource, @NotNull KapuaLifecycleMessage<?, ?> message) throws KapuaException {

        DeviceEventCreator deviceEventCreator = DEVICE_EVENT_FACTORY.newCreator(device.getScopeId(), device.getId(), message.getReceivedOn(), resource);
        deviceEventCreator.setResponseCode(KapuaResponseCode.ACCEPTED);
        deviceEventCreator.setSentOn(message.getSentOn());

        if (message.getPayload() != null) {
            deviceEventCreator.setEventMessage(message.getPayload().toDisplayString());
        }

        KapuaPosition position = message.getPosition();
        if (position != null) {
            deviceEventCreator.setPosition(position);
        }

        return KapuaSecurityUtils.doPrivileged(() -> DEVICE_EVENT_SERVICE.create(deviceEventCreator));
    }

    /**
     * Builds the {@link List} of {@link DeviceExtendedProperty}es from the {@link KapuaBirthPayload#getExtendedProperties()} property.
     *
     * @param extendedPropertiesString The {@link KapuaBirthPayload#getExtendedProperties()} to parse.
     * @return The {@link List} of {@link DeviceExtendedProperty}es parsed.
     * @throws KapuaException If there are exception while parsing the JSON-formatted metric.
     * @since 1.5.0
     */
    private List<DeviceExtendedProperty> buildDeviceExtendedPropertyFromBirth(@Nullable String extendedPropertiesString) throws KapuaException {
        if (Strings.isNullOrEmpty(extendedPropertiesString)) {
            return Collections.emptyList();
        }

        try {
            BirthExtendedProperties birthExtendedProperties = JSON_MAPPER.readValue(extendedPropertiesString, BirthExtendedProperties.class);

            List<DeviceExtendedProperty> deviceExtendedProperties = new ArrayList<>();
            for (Map.Entry<String, BirthExtendedProperty> eps : birthExtendedProperties.getExtendedProperties().entrySet()) {
                for (Map.Entry<String, String> ep : eps.getValue().entrySet()) {
                    deviceExtendedProperties.add(new DeviceExtendedPropertyImpl(eps.getKey(), ep.getKey(), ep.getValue()));
                }
            }

            return deviceExtendedProperties;
        } catch (Exception e) {
            throw KapuaException.internalError(e, "Error while parsing Device's extended properties!");
        }
    }
}
