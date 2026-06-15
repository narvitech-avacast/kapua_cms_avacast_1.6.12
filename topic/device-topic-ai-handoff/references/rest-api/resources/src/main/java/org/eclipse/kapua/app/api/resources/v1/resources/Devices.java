/*******************************************************************************
 * Copyright (c) 2011, 2017 Eurotech and/or its affiliates and others
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Eurotech - initial API and implementation
 *******************************************************************************/
package org.eclipse.kapua.app.api.resources.v1.resources;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.DefaultValue;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import org.eclipse.kapua.KapuaEntityNotFoundException;
import org.eclipse.kapua.KapuaException;
import org.eclipse.kapua.app.api.resources.v1.resources.model.Capacity;
import org.eclipse.kapua.app.api.resources.v1.resources.model.CountResult;
import org.eclipse.kapua.app.api.resources.v1.resources.model.DeviceType;
import org.eclipse.kapua.app.api.resources.v1.resources.model.EntityId;
import org.eclipse.kapua.app.api.resources.v1.resources.model.GatewayConfigXmlGen;
import org.eclipse.kapua.app.api.resources.v1.resources.model.ScopeId;
import org.eclipse.kapua.app.api.resources.v1.resources.model.Str;
import org.eclipse.kapua.app.api.resources.v1.resources.model.Words;
import org.eclipse.kapua.common.util.GatewayConfig.GatewayConfigGenerator;
import org.eclipse.kapua.common.util.GatewayConfig.GatewayConfigModel;
import org.eclipse.kapua.commons.model.query.predicate.AndPredicateImpl;
import org.eclipse.kapua.commons.model.query.predicate.AttributePredicateImpl;
import org.eclipse.kapua.commons.responeCode.DeviceResponseCode;
import org.eclipse.kapua.commons.setting.system.SystemSetting;
import org.eclipse.kapua.commons.setting.system.SystemSettingKey;
import org.eclipse.kapua.locator.KapuaLocator;
import org.eclipse.kapua.model.id.KapuaId;
import org.eclipse.kapua.service.KapuaService;
import org.eclipse.kapua.service.account.Account;
import org.eclipse.kapua.service.account.AccountFactory;
import org.eclipse.kapua.service.account.AccountService;
import org.eclipse.kapua.service.account.internal.AccountResponseCode;
import org.eclipse.kapua.service.authentication.credential.Credential;
import org.eclipse.kapua.service.authentication.credential.CredentialAttributes;
import org.eclipse.kapua.service.authentication.credential.CredentialFactory;
import org.eclipse.kapua.service.authentication.credential.CredentialListResult;
import org.eclipse.kapua.service.authentication.credential.CredentialQuery;
import org.eclipse.kapua.service.authentication.credential.CredentialService;
import org.eclipse.kapua.service.authentication.shiro.utils.AuthenticationUtils;
import org.eclipse.kapua.service.authentication.shiro.utils.CryptAlgorithm;
import org.eclipse.kapua.service.device.management.gatewayconfig.DeviceTokenGenGatewayconfig;
import org.eclipse.kapua.service.device.registry.Device;
import org.eclipse.kapua.service.device.registry.DeviceAttributes;
import org.eclipse.kapua.service.device.registry.DeviceCreator;
import org.eclipse.kapua.service.device.registry.DeviceFactory;
import org.eclipse.kapua.service.device.registry.DeviceListResult;
import org.eclipse.kapua.service.device.registry.DeviceQuery;
import org.eclipse.kapua.service.device.registry.DeviceRegistryService;
import org.eclipse.kapua.service.device.registry.DeviceStatus;
import org.eclipse.kapua.service.device.registry.connection.DeviceConnectionStatus;
import org.eclipse.kapua.service.device.registry.event.DeviceEvent;
import org.eclipse.kapua.service.device.registry.event.DeviceEventFactory;
import org.eclipse.kapua.service.device.registry.event.DeviceEventListResult;
import org.eclipse.kapua.service.device.registry.event.DeviceEventQuery;
import org.eclipse.kapua.service.device.registry.event.DeviceEventService;
import org.eclipse.kapua.service.device.registry.internal.DeviceDAO;
import org.eclipse.kapua.service.user.User;
import org.eclipse.kapua.service.user.UserAttributes;
import org.eclipse.kapua.service.user.UserFactory;
import org.eclipse.kapua.service.user.UserListResult;
import org.eclipse.kapua.service.user.UserQuery;
import org.eclipse.kapua.service.user.UserService;

import com.google.common.base.Strings;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiImplicitParam;
import io.swagger.annotations.ApiImplicitParams;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import io.swagger.annotations.Authorization;

@Api(value = "Devices", authorizations = { @Authorization(value = "kapuaAccessToken") })
@Path("{scopeId}/devices")
public class Devices extends AbstractKapuaResource {

    private final KapuaLocator locator = KapuaLocator.getInstance();
    private final DeviceRegistryService deviceService = locator.getService(DeviceRegistryService.class);
    private final DeviceFactory deviceFactory = locator.getFactory(DeviceFactory.class);

    private final DeviceEventService deviceEventService = locator.getService(DeviceEventService.class);
    private final DeviceEventFactory deviceEventFactory = locator.getFactory(DeviceEventFactory.class);
    
    private final UserService userService = locator.getService(UserService.class);
    private final UserFactory userFactory = locator.getFactory(UserFactory.class);
    
    private final AccountService accountService = locator.getService(AccountService.class);
    private final AccountFactory accountFactory = locator.getFactory(AccountFactory.class);
    
    private final CredentialService credentialService = locator.getService(CredentialService.class);
    private final CredentialFactory credentialFactory = locator.getFactory(CredentialFactory.class);
    
    
    /**
     * Gets the {@link Device} list in the scope.
     *
     * @param scopeId
     *            The {@link ScopeId} in which to search results.
     * @param clientId
     *            The id of the {@link Device} in which to search results
     * @param connectionStatus
     *            The {@link DeviceConnectionStatus} in which to search results
     * @param fetchAttributes
     *            Additional attributes to be returned. Allowed values: connection, lastEvent
     * @param offset
     *            The result set offset.
     * @param limit
     *            The result set limit.
     * @return The {@link DeviceListResult} of all the devices associated to the current selected scope.
     * @throws Exception
     *             Whenever something bad happens. See specific {@link KapuaService} exceptions.
     * @since 1.0.0
     * 
     */
    @ApiOperation(nickname = "deviceSimpleQuery", value = "Gets the Device list in the scope", notes = "Returns the list of all the devices associated to the current selected scope.", response = DeviceListResult.class)
    @GET
    @Produces({ MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML })
    @ApiImplicitParams({
    @ApiImplicitParam(name = "Authorization", value = "Access Token", required = true, allowEmptyValue = false, paramType = "header", dataType = "string", format = "string", example = "Bearer access_token")
    })
    public DeviceListResult simpleQuery(
            @ApiParam(value = "The ScopeId in which to search results.", required = true, defaultValue = DEFAULT_SCOPE_ID) @PathParam("scopeId") ScopeId scopeId,
            @ApiParam(value = "The tag id to filter results.") @QueryParam("tagId") EntityId tagId,
            @ApiParam(value = "The client id to filter results.") @QueryParam("clientId") String clientId,
            @ApiParam(value = "The connection status to filter results.") @QueryParam("status") DeviceConnectionStatus connectionStatus,
            @ApiParam(value = "Additional attributes to be returned. Allowed values: connection, lastEvent", allowableValues = "connection, lastEvent", allowMultiple = true) @QueryParam("fetchAttributes") List<String> fetchAttributes,
            @ApiParam(value = "The result set offset.", defaultValue = "0") @QueryParam("offset") @DefaultValue("0") int offset,
            @ApiParam(value = "The result set limit.", defaultValue = "50") @QueryParam("limit") @DefaultValue("50") int limit) throws Exception {
        DeviceQuery query = deviceFactory.newQuery(scopeId);

        AndPredicateImpl andPredicate = new AndPredicateImpl();
        if (tagId != null) {
            andPredicate.and(new AttributePredicateImpl<KapuaId>(DeviceAttributes.TAG_IDS, tagId));
        }
        if (!Strings.isNullOrEmpty(clientId)) {
            andPredicate.and(new AttributePredicateImpl<>(DeviceAttributes.CLIENT_ID, clientId));
        }
        if (connectionStatus != null) {
            andPredicate.and(new AttributePredicateImpl<>(DeviceAttributes.CONNECTION_STATUS, connectionStatus));
        }
        query.setPredicate(andPredicate);
        query.setFetchAttributes(fetchAttributes);
        query.setOffset(offset);
        query.setLimit(limit);

        return query(scopeId, query);
    }

    /**
     * Queries the results with the given {@link DeviceQuery} parameter.
     *
     * @param scopeId
     *            The {@link ScopeId} in which to search results.
     * @param query
     *            The {@link DeviceQuery} to use to filter results.
     * @return The {@link DeviceListResult} of all the result matching the given {@link DeviceQuery} parameter.
     * @throws Exception
     *             Whenever something bad happens. See specific {@link KapuaService} exceptions.
     * @since 1.0.0
     */
    @ApiOperation(nickname = "deviceQuery", value = "Queries the Devices", notes = "Queries the Devices with the given Devices parameter returning all matching Devices", response = DeviceListResult.class)
    @POST
    @Path("_query")
    @Consumes({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @ApiImplicitParams({
    @ApiImplicitParam(name = "Authorization", value = "Access Token", required = true, allowEmptyValue = false, paramType = "header", dataType = "string", format = "string", example = "Bearer access_token")
    })
    public DeviceListResult query(
            @ApiParam(value = "The ScopeId in which to search results.", required = true, defaultValue = DEFAULT_SCOPE_ID) @PathParam("scopeId") ScopeId scopeId,
            @ApiParam(value = "The DeviceQuery to use to filter results.", required = true) DeviceQuery query) throws Exception {
        query.setScopeId(scopeId);

        return deviceService.query(query);
    }

    /**
     * Counts the results with the given {@link DeviceQuery} parameter.
     *
     * @param scopeId
     *            The {@link ScopeId} in which to search results.
     * @param query
     *            The {@link DeviceQuery} to use to filter results.
     * @return The count of all the result matching the given {@link DeviceQuery} parameter.
     * @throws Exception
     *             Whenever something bad happens. See specific {@link KapuaService} exceptions.
     * @since 1.0.0
     */
    @ApiOperation(nickname = "deviceCount", value = "Counts the Devices", notes = "Counts the Devices with the given DeviceQuery parameter returning the number of matching Devices", response = CountResult.class)
    @POST
    @Path("_count")
    @Consumes({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @ApiImplicitParams({
    @ApiImplicitParam(name = "Authorization", value = "Access Token", required = true, allowEmptyValue = false, paramType = "header", dataType = "string", format = "string", example = "Bearer access_token")
    })
    public CountResult count(
            @ApiParam(value = "The ScopeId in which to count results", required = true, defaultValue = DEFAULT_SCOPE_ID) @PathParam("scopeId") ScopeId scopeId,
            @ApiParam(value = "The DeviceQuery to use to filter count results", required = true) DeviceQuery query) throws Exception {
        query.setScopeId(scopeId);

        return new CountResult(deviceService.count(query));
    }

    /**
     * Creates a new Device based on the information provided in DeviceCreator
     * parameter.
     *
     * @param scopeId
     *            The {@link ScopeId} in which to create the {@link Device}
     * @param deviceCreator
     *            Provides the information for the new Device to be created.
     * @return The newly created Device object.
     * @throws Exception
     *             Whenever something bad happens. See specific {@link KapuaService} exceptions.
     * @since 1.0.0
     */
    @ApiOperation(nickname = "deviceCreate", value = "Create an Device", notes = "Creates a new Device based on the information provided in DeviceCreator parameter.", response = Device.class)
    @POST
    @Consumes({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @ApiImplicitParams({
    @ApiImplicitParam(name = "Authorization", value = "Access Token", required = true, allowEmptyValue = false, paramType = "header", dataType = "string", format = "string", example = "Bearer access_token")
    })
    public Device create(
            @ApiParam(value = "The ScopeId in which to create the Device.", required = true, defaultValue = DEFAULT_SCOPE_ID) @PathParam("scopeId") ScopeId scopeId,
            @ApiParam(value = "Provides the information for the new Device to be created", required = true) DeviceCreator deviceCreator) throws Exception {
        deviceCreator.setScopeId(scopeId);

        return deviceService.create(deviceCreator);
    }

    /**
     * Returns the Device specified by the "deviceId" path parameter.
     *
     * @param scopeId
     *            The {@link ScopeId} of the requested {@link Device}.
     * @param deviceId
     *            The id of the requested Device.
     * @return The requested Device object.
     * @throws Exception
     *             Whenever something bad happens. See specific {@link KapuaService} exceptions.
     * @since 1.0.0
     */
    @ApiOperation(nickname = "deviceFind", value = "Get a Device", notes = "Returns the Device specified by the \"deviceId\" path parameter.", response = Device.class)
    @GET
    @Path("{deviceId}")
    @Produces({ MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML })
    @ApiImplicitParams({
    @ApiImplicitParam(name = "Authorization", value = "Access Token", required = true, allowEmptyValue = false, paramType = "header", dataType = "string", format = "string", example = "Bearer access_token")
    })
    public Device find(
            @ApiParam(value = "The ScopeId of the requested Device", required = true, defaultValue = DEFAULT_SCOPE_ID) @PathParam("scopeId") ScopeId scopeId,
            @ApiParam(value = "The id of the requested Device", required = true) @PathParam("deviceId") EntityId deviceId) throws Exception {
        Device device = deviceService.find(scopeId, deviceId);

        if (device != null) {
            return device;
        } else {
            throw new KapuaEntityNotFoundException(Device.TYPE, deviceId);
        }
    }

    /**
     * Updates the Device based on the information provided in the Device parameter.
     *
     * @param scopeId
     *            The ScopeId of the requested Device.
     * @param deviceId
     *            The id of the requested {@link Device}
     * @param device
     *            The modified Device whose attributed need to be updated.
     * @return The updated device.
     * @throws Exception
     *             Whenever something bad happens. See specific {@link KapuaService} exceptions.
     * @since 1.0.0
     */
    @ApiOperation(nickname = "deviceUpdate", value = "Update a Device", notes = "Updates a new Device based on the information provided in the Device parameter.", response = Device.class)
    @PUT
    @Path("{deviceId}")
    @Consumes({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @ApiImplicitParams({
    @ApiImplicitParam(name = "Authorization", value = "Access Token", required = true, allowEmptyValue = false, paramType = "header", dataType = "string", format = "string", example = "Bearer access_token")
    })
    public Device update(
            @ApiParam(value = "The ScopeId of the requested Device.", required = true, defaultValue = DEFAULT_SCOPE_ID) @PathParam("scopeId") ScopeId scopeId,
            @ApiParam(value = "The id of the requested Device", required = true) @PathParam("deviceId") EntityId deviceId,
            @ApiParam(value = "The modified Device whose attributed need to be updated", required = true) Device device) throws Exception {
        device.setScopeId(scopeId);
        device.setId(deviceId);

        return deviceService.update(device);
    }

    /**
     * Deletes the Device specified by the "deviceId" path parameter.
     *
     * @param scopeId
     *            The ScopeId of the requested {@link Device}.
     * @param deviceId
     *            The id of the Device to be deleted.
     * @return HTTP 200 if operation has completed successfully.
     * @throws Exception
     *             Whenever something bad happens. See specific {@link KapuaService} exceptions.
     * @since 1.0.0
     */
    @ApiOperation(nickname = "deviceDelete", value = "Delete a Device", notes = "Deletes the Device specified by the \"deviceId\" path parameter.")
    @DELETE
    @Path("{deviceId}")
    @ApiImplicitParams({
    @ApiImplicitParam(name = "Authorization", value = "Access Token", required = true, allowEmptyValue = false, paramType = "header", dataType = "string", format = "string", example = "Bearer access_token")
    })
    public Response deleteDevice(
            @ApiParam(value = "The ScopeId of the Device to delete.", required = true, defaultValue = DEFAULT_SCOPE_ID) @PathParam("scopeId") ScopeId scopeId,
            @ApiParam(value = "The id of the Device to be deleted", required = true) @PathParam("deviceId") EntityId deviceId) throws Exception {
        deviceService.delete(scopeId, deviceId);

        return returnOk();
    }

    @ApiOperation(nickname = "deviceTypeNumber", value = "Count the number of devices' type",notes = "Count how many types appear in the scope.")
    @GET
    @Path("deviceTypeNumber")
    @Consumes({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @ApiImplicitParams({
    @ApiImplicitParam(name = "Authorization", value = "Access Token", required = true, allowEmptyValue = false, paramType = "header", dataType = "string", format = "string", example = "Bearer access_token")
    })
    public CountResult deviceTypeNumber(
        @ApiParam(value = "The ScopeId to count the number of type.", required = true, defaultValue = DEFAULT_SCOPE_ID) @PathParam("scopeId") ScopeId scopeId) throws Exception {
        return new CountResult(deviceTypeList(scopeId).size());
    }

    @ApiOperation(nickname = "deviceTypeList", value = "List device type names and their number.", notes = "List device type names and their number.")
    @GET
    @Path("deviceTypeList")
    @Consumes({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @ApiImplicitParams({
    @ApiImplicitParam(name = "Authorization", value = "Access Token", required = true, allowEmptyValue = false, paramType = "header", dataType = "string", format = "string", example = "Bearer access_token")
    })
    public List<DeviceType> deviceTypeList(
        @ApiParam(value = "The ScopeId to search.", required = true, defaultValue = DEFAULT_SCOPE_ID) @PathParam("scopeId") ScopeId scopeId) throws Exception {
        DeviceQuery query = deviceFactory.newQuery(scopeId);
        DeviceListResult deviceList = query(scopeId, query);

        List<DeviceType> deviceTypeList = new ArrayList<>();

        int i; 
        for(i=0;i<deviceList.getSize();i++)
        {
            String modelId = deviceList.getItem(i).getModelId();

            if(modelId == null) {
                modelId = "";
            }

            boolean exist = false;
            int j;
            for(j=0;j<deviceTypeList.size();j++)
            {
                if(modelId.equals(deviceTypeList.get(j).getModelId())){
                    exist = true;
                    break;
                }
            }

            if(exist == true) {
                deviceTypeList.get(j).setCount(deviceTypeList.get(j).getCount()+1);
            } 
            else {
                DeviceType d = new DeviceType(modelId, 1);
                deviceTypeList.add(d);
            }
        }

        return deviceTypeList;
    }

    @ApiOperation(nickname = "sortedEvents", value = "Get the DeviceEvent list and sort them", notes = "Get the DeviceEvent list and sort them from lastest to oldest.")
    @GET
    @Path("sortedEvents")
    @Consumes({ MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML })
    @Produces({ MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML })
    @ApiImplicitParams({
    @ApiImplicitParam(name = "Authorization", value = "Access Token", required = true, allowEmptyValue = false, paramType = "header", dataType = "string", format = "string", example = "Bearer access_token")
    })
    public DeviceEventListResult sortedEvents(
        @ApiParam(value = "The ScopeId to get events and sort.", required = true, defaultValue = DEFAULT_SCOPE_ID) @PathParam("scopeId") ScopeId scopeId) throws Exception {
        DeviceEventQuery query = deviceEventFactory.newQuery(scopeId);
        query.setScopeId(scopeId);

        DeviceEventListResult deviceEventList = deviceEventService.query(query);
        deviceEventList.sort(receivedOnComparator);
        return deviceEventList;
    }

    Comparator<DeviceEvent> receivedOnComparator = new Comparator<DeviceEvent>() 
    {
        @Override
        public int compare(DeviceEvent a, DeviceEvent b) 
        {
            if(a.getReceivedOn().after(b.getReceivedOn())) {
                return -1;
            }
            else if(a.getReceivedOn().before(b.getReceivedOn())) {
                return 1;
            }
            else {
                return 0;
            }
        }
    };

    @ApiOperation(nickname = "deviceCapacity", value = "Test the device capacity.", notes = "test.")
    @POST
    @Path("capacity")
    @Consumes({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @ApiImplicitParams({
    @ApiImplicitParam(name = "Authorization", value = "Access Token", required = true, allowEmptyValue = false, paramType = "header", dataType = "string", format = "string", example = "Bearer access_token")
    })
    public Capacity deviceCapacity(
        @ApiParam(value = "The ScopeId.", required = true, defaultValue = DEFAULT_SCOPE_ID) @PathParam("scopeId") ScopeId scopeId,
        @ApiParam(value = "The client id to filter results.") @PathParam("deviceId") EntityId deviceId) throws Exception {
        return new Capacity(128, 64);
    }

    @ApiOperation(nickname = "deviceTest", value = "###########################################", notes = "test.")
    @POST
    @Path("deviceTest")
    @Consumes({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    public Str deviceTest(
        @ApiParam(value = "The ScopeId.", required = true, defaultValue = DEFAULT_SCOPE_ID) @PathParam("scopeId") ScopeId scopeId) throws Exception {
        return new Str("128");
    }
    
    @ApiOperation(nickname="createDeviceAndGiveGatewayConfig", value = "create a new Device And return GatewayConfig", notes ="It will create a new device and replay a new GatewayConfig")
    @POST
    @Consumes({ MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML })
    @Produces({ MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML })
    @Path("deviceAndGatewayConfig")
    public Words createDeviceAndGiveGatewayConfig(@ApiParam(value = "The ScopeId.", required = true, defaultValue = DEFAULT_SCOPE_ID) @PathParam("scopeId") ScopeId scopeId,
	    @ApiParam(value = "Provides the information for the new Device to be created", required = true) DeviceCreator deviceCreator) {
	deviceCreator.setScopeId(scopeId);
	
	GatewayConfigGenerator gcg = new GatewayConfigGenerator();
	GatewayConfigModel gcm = new GatewayConfigModel();
	SystemSetting systemSetting = SystemSetting.getInstance();
	try {
	   
	    Device device = deviceService.create(deviceCreator);
//===== Step1 : Set device name	    
	    gcm.setDeviceName(device.getClientId());
//===== Step2 : Broker user's user name
	    UserQuery query = userFactory.newQuery(device.getScopeId());

	        AndPredicateImpl andPredicate = new AndPredicateImpl();
	        query.setPredicate(andPredicate);

	        query.setOffset(0);
	        query.setLimit(null);
	    UserListResult userList = userService.query(query);
	    if(userList==null ||userList.getSize()==0) {
		Words err = new Words();
		err.setValue("5200:USER_NOT_FIND");
		return err;
	    }
	    User brokerUser=null;
	    for(int i=0;i<userList.getSize();i++) {
		User user = userList.getItem(i);
		if(user.getName().contains("Broker")) {
		    brokerUser=user;
		    break;
		}
	    }
	    if(brokerUser==null) {
		Words err = new Words();
		err.setValue("5200:USER_NOT_FIND");
		return err;
	    }
	    gcm.setBrokerUser(brokerUser.getName());
	    gcm.setBrokerHost(systemSetting.getString(SystemSettingKey.BROKER_HOST));
	    gcm.setBrokerPort(systemSetting.getString(SystemSettingKey.BROKER_PORT));
	    gcm.setBrokerProtocol(systemSetting.getString(SystemSettingKey.BROKER_SCHEME));
//===== Step3 : Set Account Name
	    
	    Account account = accountService.find(brokerUser.getScopeId());
	    if(account ==null) {
		AccountResponseCode code = AccountResponseCode.NOT_FIND_ACCOUNT;
		Words err = new Words();
		err.setValue(code.fullDescription(code));
		return err;
	    }
	    gcm.setAccountName(account.getName());	    
//===== Step4 : Broker user's password by credential
	    
	    CredentialListResult credList = credentialService.findByUserId(brokerUser.getScopeId(), brokerUser.getId());
	    
	    if(credList==null || credList.getSize()==0) {
		Words err = new Words();
		err.setValue("5104:NOT_FIND_CREDENTIAL");
		return err;
	    }
	    Credential cred = credList.getItem(0);
	    gcm.setBrokerPassword(systemSetting.getString(SystemSettingKey.BROKER_PASSWORD));
	    
//===== Step5 : Generate Gateway config
	    GatewayConfigXmlGen gcxg = new GatewayConfigXmlGen();
	    gcxg.setGatewayConfig(gcm);
		 
//===== Step5-1: Clear device access token data
		if(device!=null) {
		Device copyDevice = device;
		copyDevice.setDeviceAccessToken(null);
		  deviceService.update(copyDevice);
		}
		 
		 return gcxg.build();
	} catch (KapuaException e) {
	    // TODO Auto-generated catch block
	    e.printStackTrace();
	}
	return null;
    }
    
    @Deprecated
    @ApiOperation(nickname = "getGatewayConfigByAccessToken", value = "Get Gateway Config By AccessToken", notes = "The tx will use this api to get gatewayconfig"
    	+ " by access token")
    @POST
    @Consumes({ MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML })
    @Produces({ MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML })
    @Path("getAndroidGatewayConfigByAccessToken")
    public Words getAndroidGatewayConfigByAccessToken(
	    @ApiParam(value = "The ScopeId.", required = true, defaultValue = DEFAULT_SCOPE_ID) @PathParam("scopeId") ScopeId scopeId,
	    @ApiParam(value = "the platform is Android or Windows which will fit data for  gateway config ,") DeviceTokenGenGatewayconfig tokenWithPlatform) {
	
	
	GatewayConfigGenerator gcg = new GatewayConfigGenerator();
	GatewayConfigModel gcm = new GatewayConfigModel();
	SystemSetting systemSetting = SystemSetting.getInstance();
	try {
	    
//===== Step1 : Set device name
	    Device device = deviceService.findByAccessToken(tokenWithPlatform.getAccessToken());
	    
	    if(device==null) {
		DeviceResponseCode code = DeviceResponseCode.NOT_FIND_DEVICE;
		Words err = new Words();
		err.setValue(code.fullDescription(code));
		return err;
	    }
	    gcm.setDeviceName(device.getClientId());     
//===== Step2 : Broker user's user name
	    UserQuery query = userFactory.newQuery(device.getScopeId());

	        AndPredicateImpl andPredicate = new AndPredicateImpl();
	        query.setPredicate(andPredicate);

	        query.setOffset(0);
	        query.setLimit(null);
	    UserListResult userList = userService.query(query);
	    if(userList==null ||userList.getSize()==0) {
		Words err = new Words();
		err.setValue("5200:USER_NOT_FIND");
		return err;
	    }
	    User brokerUser=null;
	    for(int i=0;i<userList.getSize();i++) {
		User user = userList.getItem(i);
		if(user.getName().contains("Broker")) {
		    brokerUser=user;
		    break;
		}
	    }
	    if(brokerUser==null) {
		Words err = new Words();
		err.setValue("5200:USER_NOT_FIND");
		return err;
	    }
	    
	    gcm.setBrokerUser(brokerUser.getName());
	    gcm.setBrokerHost(systemSetting.getString(SystemSettingKey.BROKER_HOST));
	    gcm.setBrokerPort(systemSetting.getString(SystemSettingKey.BROKER_PORT));
	    gcm.setBrokerProtocol(systemSetting.getString(SystemSettingKey.BROKER_SCHEME));
//===== Step3 : Set Account Name
	    
	    Account account = accountService.find(brokerUser.getScopeId());
	    if(account ==null) {
		AccountResponseCode code = AccountResponseCode.NOT_FIND_ACCOUNT;
		Words err = new Words();
		err.setValue(code.fullDescription(code));
		return err;
	    }
	    gcm.setAccountName(account.getName());	    
//===== Step4 : Broker user's password by credential
	    
	    CredentialListResult credList = credentialService.findByUserId(brokerUser.getScopeId(), brokerUser.getId());
	    
	    if(credList==null || credList.getSize()==0) {
		Words err = new Words();
		err.setValue("5104:NOT_FIND_CREDENTIAL");
		return err;
	    }
	    Credential cred = credList.getItem(0);
	    gcm.setBrokerPassword(systemSetting.getString(SystemSettingKey.BROKER_PASSWORD));
	    
//===== Step5 : Generate Gateway config
	    GatewayConfigXmlGen gcxg = new GatewayConfigXmlGen();
	    if(tokenWithPlatform.getPlatform().equals("Android")) {
		 gcxg.setGatewayConfig(gcm);
		 
//===== Step5-1: Clear device access token data
		if(device!=null) {
		Device copyDevice = device;
		copyDevice.setDeviceAccessToken(null);
		  deviceService.update(copyDevice);
		}
		 
		 return gcxg.build();
	    }else {
		 gcg.setGatewayConfig(gcm);
	    }
	    return null;
	} catch (KapuaException e) {
	    // TODO Auto-generated catch block
	    e.printStackTrace();
	    Words err = new Words();
	    err.setValue("500:"+e.getMessage());
	    return err;
	}
    }
}