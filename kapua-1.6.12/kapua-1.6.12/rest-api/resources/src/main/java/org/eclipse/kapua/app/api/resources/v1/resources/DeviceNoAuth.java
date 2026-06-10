/*******************************************************************************
 * Copyright (c) 2017, 2022 Eurotech and/or its affiliates and others.
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *     Eurotech - initial API and implementation
 */
package org.eclipse.kapua.app.api.resources.v1.resources;

import org.eclipse.kapua.KapuaException;
import org.eclipse.kapua.app.api.core.model.ScopeId;
import org.eclipse.kapua.app.api.core.resources.AbstractKapuaResource;
import org.eclipse.kapua.app.api.resources.v1.resources.model.DeviceRegistrationTokenStore;
import org.eclipse.kapua.app.api.resources.v1.resources.model.GatewayConfigXmlGen;
import org.eclipse.kapua.app.api.resources.v1.resources.model.Words;
import org.eclipse.kapua.broker.BrokerDomains;
import org.eclipse.kapua.common.util.GatewayConfig.GatewayConfigModel;
import org.eclipse.kapua.commons.responeCode.AccountResponseCode;
import org.eclipse.kapua.commons.responeCode.DeviceResponseCode;
import org.eclipse.kapua.commons.security.KapuaSecurityUtils;
import org.eclipse.kapua.commons.setting.system.SystemSetting;
import org.eclipse.kapua.commons.setting.system.SystemSettingKey;
import org.eclipse.kapua.locator.KapuaLocator;
import org.eclipse.kapua.model.domain.Actions;
import org.eclipse.kapua.service.account.Account;
import org.eclipse.kapua.service.account.AccountService;
import org.eclipse.kapua.service.authentication.credential.Credential;
import org.eclipse.kapua.service.authentication.credential.CredentialCreator;
import org.eclipse.kapua.service.authentication.credential.CredentialFactory;
import org.eclipse.kapua.service.authentication.credential.CredentialListResult;
import org.eclipse.kapua.service.authentication.credential.CredentialService;
import org.eclipse.kapua.service.authentication.credential.CredentialStatus;
import org.eclipse.kapua.service.authentication.credential.CredentialType;
import org.eclipse.kapua.service.authorization.access.AccessInfo;
import org.eclipse.kapua.service.authorization.access.AccessInfoCreator;
import org.eclipse.kapua.service.authorization.access.AccessInfoFactory;
import org.eclipse.kapua.service.authorization.access.AccessInfoService;
import org.eclipse.kapua.service.authorization.permission.Permission;
import org.eclipse.kapua.service.authorization.permission.PermissionFactory;
import org.eclipse.kapua.service.device.management.gatewayconfig.DeviceTokenGenGatewayconfig;
import org.eclipse.kapua.service.device.registry.Device;
import org.eclipse.kapua.service.device.registry.DeviceCreator;
import org.eclipse.kapua.service.device.registry.DeviceFactory;
import org.eclipse.kapua.service.device.registry.DeviceRegistryService;
import org.eclipse.kapua.service.user.User;
import org.eclipse.kapua.service.user.UserCreator;
import org.eclipse.kapua.service.user.UserFactory;
import org.eclipse.kapua.service.user.UserListResult;
import org.eclipse.kapua.service.user.UserQuery;
import org.eclipse.kapua.service.user.UserService;
import org.eclipse.kapua.service.user.UserType;

import javax.ws.rs.Consumes;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MediaType;
import java.util.HashSet;
import java.util.Set;

@Path("devicesNoAuth")
public class DeviceNoAuth extends AbstractKapuaResource {

    private final KapuaLocator locator = KapuaLocator.getInstance();

    private final DeviceRegistryService deviceService = locator.getService(DeviceRegistryService.class);
    private final DeviceFactory deviceFactory = locator.getFactory(DeviceFactory.class);

    private final UserService userService = locator.getService(UserService.class);
    private final UserFactory userFactory = locator.getFactory(UserFactory.class);

    private final AccountService accountService = locator.getService(AccountService.class);

    private final CredentialService credentialService = locator.getService(CredentialService.class);
    private final CredentialFactory credentialFactory = locator.getFactory(CredentialFactory.class);

    private final AccessInfoService accessInfoService = locator.getService(AccessInfoService.class);
    private final AccessInfoFactory accessInfoFactory = locator.getFactory(AccessInfoFactory.class);
    private final PermissionFactory permissionFactory = locator.getFactory(PermissionFactory.class);

    @Context
    private HttpHeaders headers;

    @POST
    @Path("getAndroidGatewayConfigByAccessToken")
    @Consumes({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML})
    @Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML})
    public Words getAndroidGatewayConfigByAccessToken(
            DeviceTokenGenGatewayconfig tokenWithPlatform) {

        SystemSetting systemSetting = SystemSetting.getInstance();

        try {
            if (tokenWithPlatform.getScopeId() == null || tokenWithPlatform.getScopeId().isEmpty()) {
                Words err = new Words();
                err.setValue("400:MISSING_SCOPE_ID");
                return err;
            }
            final ScopeId scopeId = new ScopeId(tokenWithPlatform.getScopeId());

            Device device;
            if (!isBlank(tokenWithPlatform.getClientId())) {
                boolean tokenValid = DeviceRegistrationTokenStore.getInstance().consume(
                        scopeId.toCompactId(), tokenWithPlatform.getAccessToken());
                if (!tokenValid) {
                    Words err = new Words();
                    err.setValue("401:INVALID_OR_EXPIRED_DEVICE_TOKEN");
                    return err;
                }
                device = createOrFindDevice(scopeId, tokenWithPlatform);
            } else {
                device = KapuaSecurityUtils.doPrivileged(
                        () -> deviceService.findByClientId(scopeId, tokenWithPlatform.getAccessToken()));
            }

            if (device == null) {
                DeviceResponseCode code = DeviceResponseCode.NOT_FIND_DEVICE;
                Words err = new Words();
                err.setValue(code.fullDescription(code));
                return err;
            }

            GatewayConfigModel gcm = buildGatewayConfigModel(device, systemSetting);
            if (gcm == null) {
                Words err = new Words();
                err.setValue("5200:USER_NOT_FIND");
                return err;
            }

            if ("Android".equals(tokenWithPlatform.getPlatform())) {
                GatewayConfigXmlGen gcxg = new GatewayConfigXmlGen();
                gcxg.setGatewayConfig(gcm);
                return gcxg.build();
            }

            return new Words("");

        } catch (Exception e) {
            e.printStackTrace();
            Words err = new Words();
            err.setValue("500:" + e.getMessage());
            return err;
        }
    }

    private Device createOrFindDevice(
            ScopeId scopeId,
            DeviceTokenGenGatewayconfig tokenWithPlatform) throws KapuaException {

        Device existing = KapuaSecurityUtils.doPrivileged(
                () -> deviceService.findByClientId(scopeId, tokenWithPlatform.getClientId()));
        if (existing != null) {
            return existing;
        }

        DeviceCreator deviceCreator = KapuaSecurityUtils.doPrivileged(
                () -> deviceFactory.newCreator(scopeId));
        deviceCreator.setClientId(tokenWithPlatform.getClientId());
        deviceCreator.setDisplayName(!isBlank(tokenWithPlatform.getDisplayName()) ?
                tokenWithPlatform.getDisplayName() :
                tokenWithPlatform.getClientId());

        return KapuaSecurityUtils.doPrivileged(
                () -> deviceService.create(deviceCreator));
    }

    @POST
    @Path("{scopeId}/GatewayConfigByAccountID")
    @Consumes({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML})
    @Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML})
    public Words gatewayConfigByAccountID(
            @PathParam("scopeId") ScopeId scopeId) {

        SystemSetting systemSetting = SystemSetting.getInstance();

        try {
            Account account = KapuaSecurityUtils.doPrivileged(
                    () -> accountService.find(scopeId));
            if (account == null) {
                AccountResponseCode code = AccountResponseCode.NOT_FIND_ACCOUNT;
                Words err = new Words();
                err.setValue(code.fullDescription(code));
                return err;
            }

            DeviceCreator deviceCreator = KapuaSecurityUtils.doPrivileged(
                    () -> deviceFactory.newCreator(account.getId()));
            deviceCreator.setClientId("device-" + System.currentTimeMillis());
            deviceCreator.setDisplayName("");

            Device device = KapuaSecurityUtils.doPrivileged(
                    () -> deviceService.create(deviceCreator));
            if (device == null) {
                DeviceResponseCode code = DeviceResponseCode.NOT_FIND_DEVICE;
                Words err = new Words();
                err.setValue(code.fullDescription(code));
                return err;
            }

            GatewayConfigModel gcm = buildGatewayConfigModel(device, systemSetting);
            if (gcm == null) {
                Words err = new Words();
                err.setValue("5200:USER_NOT_FIND");
                return err;
            }

            GatewayConfigXmlGen gcxg = new GatewayConfigXmlGen();
            gcxg.setGatewayConfig(gcm);
            return gcxg.build();

        } catch (Exception e) {
            e.printStackTrace();
            Words err = new Words();
            err.setValue("500:" + e.getMessage());
            return err;
        }
    }

    private User findBrokerUser(org.eclipse.kapua.model.id.KapuaId scopeId, String brokerUserName)
            throws KapuaException {
        UserQuery query = KapuaSecurityUtils.doPrivileged(
                () -> userFactory.newQuery(scopeId));
        UserListResult userList = KapuaSecurityUtils.doPrivileged(
                () -> userService.query(query));

        if (userList == null || userList.getSize() == 0) {
            return null;
        }
        for (int i = 0; i < userList.getSize(); i++) {
            User user = userList.getItem(i);
            if (brokerUserName.equals(user.getName())) {
                return user;
            }
        }
        return null;
    }

    private synchronized User ensureBrokerUser(Account account) throws KapuaException {
        String accountName = account.getName();
        String brokerUserName = accountName + "-broker";
        User brokerUser = findBrokerUser(account.getId(), brokerUserName);

        if (brokerUser == null) {
            UserCreator userCreator = KapuaSecurityUtils.doPrivileged(
                    () -> userFactory.newCreator(account.getId(), brokerUserName));
            userCreator.setUserType(UserType.INTERNAL);
            userCreator.setDisplayName("Gateway User");
            brokerUser = KapuaSecurityUtils.doPrivileged(
                    () -> userService.create(userCreator));
        }

        ensureBrokerAccess(brokerUser);
        ensureBrokerCredential(account, brokerUser);
        return brokerUser;
    }

    private void ensureBrokerAccess(User brokerUser) throws KapuaException {
        AccessInfo accessInfo = KapuaSecurityUtils.doPrivileged(
                () -> accessInfoService.findByUserId(brokerUser.getScopeId(), brokerUser.getId()));
        if (accessInfo != null) {
            return;
        }

        AccessInfoCreator accessInfoCreator = KapuaSecurityUtils.doPrivileged(
                () -> accessInfoFactory.newCreator(brokerUser.getScopeId()));
        accessInfoCreator.setUserId(brokerUser.getId());

        Set<Permission> permissions = new HashSet<>();
        permissions.add(KapuaSecurityUtils.doPrivileged(
                () -> permissionFactory.newPermission(
                        BrokerDomains.BROKER_DOMAIN,
                        Actions.connect,
                        brokerUser.getScopeId())));
        accessInfoCreator.setPermissions(permissions);

        KapuaSecurityUtils.doPrivileged(
                () -> accessInfoService.create(accessInfoCreator));
    }

    private void ensureBrokerCredential(Account account, User brokerUser) throws KapuaException {
        CredentialListResult credentials = KapuaSecurityUtils.doPrivileged(
                () -> credentialService.findByUserId(brokerUser.getScopeId(), brokerUser.getId()));
        if (credentials != null) {
            for (int i = 0; i < credentials.getSize(); i++) {
                Credential credential = credentials.getItem(i);
                if (CredentialType.PASSWORD.equals(credential.getCredentialType())) {
                    return;
                }
            }
        }

        CredentialCreator credentialCreator = KapuaSecurityUtils.doPrivileged(
                () -> credentialFactory.newCreator(
                        account.getId(),
                        brokerUser.getId(),
                        CredentialType.PASSWORD,
                        defaultBrokerPassword(account.getName()),
                        CredentialStatus.ENABLED,
                        null));
        KapuaSecurityUtils.doPrivileged(
                () -> credentialService.create(credentialCreator));
    }

    private GatewayConfigModel buildGatewayConfigModel(Device device, SystemSetting systemSetting)
            throws Exception {

        GatewayConfigModel gcm = new GatewayConfigModel();
        gcm.setDeviceName(device.getClientId());

        Account account = KapuaSecurityUtils.doPrivileged(
                () -> accountService.find(device.getScopeId()));
        if (account == null) {
            return null;
        }

        User brokerUser = ensureBrokerUser(account);
        gcm.setBrokerUser(brokerUser.getName());
        gcm.setBrokerHost(resolveBrokerHost(systemSetting));
        gcm.setBrokerPort(systemSetting.getString(SystemSettingKey.BROKER_PORT, "1883"));
        gcm.setBrokerProtocol(systemSetting.getString(SystemSettingKey.BROKER_SCHEME));
        gcm.setAccountName(account.getName());
        gcm.setBrokerPassword(resolveBrokerPassword(systemSetting, account, brokerUser));

        return gcm;
    }

    private String resolveBrokerHost(SystemSetting systemSetting) {
        String configuredHost = systemSetting.getString(SystemSettingKey.BROKER_HOST);
        String requestHost = firstForwardedValue(header("X-Forwarded-Host"));
        if (isBlank(requestHost)) {
            requestHost = header("Host");
        }

        String host = stripPort(requestHost);
        if (!isBlank(host)) {
            if (isLocalHost(host) && !isBlank(configuredHost)) {
                return configuredHost;
            }
            return host;
        }

        return configuredHost;
    }

    private String header(String name) {
        return headers != null ? headers.getHeaderString(name) : null;
    }

    private String firstForwardedValue(String value) {
        if (isBlank(value)) {
            return value;
        }

        int commaIndex = value.indexOf(',');
        return commaIndex >= 0 ? value.substring(0, commaIndex).trim() : value.trim();
    }

    private String stripPort(String host) {
        if (isBlank(host)) {
            return host;
        }

        String trimmedHost = host.trim();
        if (trimmedHost.startsWith("[")) {
            int endBracket = trimmedHost.indexOf(']');
            return endBracket > 0 ? trimmedHost.substring(1, endBracket) : trimmedHost;
        }

        int colonIndex = trimmedHost.indexOf(':');
        return colonIndex >= 0 ? trimmedHost.substring(0, colonIndex) : trimmedHost;
    }

    private boolean isLocalHost(String host) {
        return "localhost".equalsIgnoreCase(host) || "127.0.0.1".equals(host) || "::1".equals(host);
    }

    private String resolveBrokerPassword(SystemSetting systemSetting, Account account, User brokerUser) {
        if (!"kapua-broker".equals(brokerUser.getName())) {
            return defaultBrokerPassword(account.getName());
        }

        String configuredPassword = systemSetting.getString(SystemSettingKey.BROKER_PASSWORD, "");
        if (!isBlank(configuredPassword)) {
            return configuredPassword;
        }

        return defaultBrokerPassword(account.getName());
    }

    private String defaultBrokerPassword(String accountName) {
        return accountName + "-Password1!";
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
