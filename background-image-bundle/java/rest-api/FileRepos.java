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

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.GeneralSecurityException;
import java.util.UUID;

import javax.imageio.ImageIO;
import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.DefaultValue;
import javax.ws.rs.FormParam;
import javax.ws.rs.GET;
import javax.ws.rs.HttpMethod;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.StreamingOutput;

import org.eclipse.kapua.KapuaEntityNotFoundException;
import org.eclipse.kapua.KapuaException;
import org.eclipse.kapua.app.api.resources.v1.resources.model.CountResult;
import org.eclipse.kapua.app.api.resources.v1.resources.model.EntityId;
import org.eclipse.kapua.app.api.resources.v1.resources.model.ScopeId;
import org.eclipse.kapua.commons.model.id.KapuaEid;
import org.eclipse.kapua.commons.oauth.GoogleAuthManager;
import org.eclipse.kapua.commons.security.KapuaSecurityUtils;
import org.eclipse.kapua.commons.security.KapuaSession;
import org.eclipse.kapua.commons.setting.system.SystemSetting;
import org.eclipse.kapua.commons.setting.system.SystemSettingKey;
import org.eclipse.kapua.locator.KapuaLocator;
import org.eclipse.kapua.model.id.KapuaId;
import org.eclipse.kapua.service.KapuaService;
import org.eclipse.kapua.service.file.repo.FileRepo;
import org.eclipse.kapua.service.file.repo.FileRepoCreator;
import org.eclipse.kapua.service.file.repo.FileRepoFactory;
import org.eclipse.kapua.service.file.repo.FileRepoListResult;
import org.eclipse.kapua.service.file.repo.FileRepoQuery;
import org.eclipse.kapua.service.file.repo.FileRepoService;
import org.eclipse.kapua.service.file.repo.internal.FileRepoCreatorImpl;
import org.eclipse.kapua.service.file.repo.internal.FileRepoImpl;
import org.eclipse.kapua.service.account.Account;
import org.eclipse.kapua.service.account.AccountCreator;
import org.eclipse.kapua.service.account.AccountFactory;
import org.eclipse.kapua.service.account.AccountListResult;
import org.eclipse.kapua.service.account.AccountQuery;
import org.eclipse.kapua.service.account.AccountService;
import org.eclipse.kapua.service.account.internal.AccountResponseCode;
import org.eclipse.kapua.service.accountanduser.OauthAccountAndUser;
import org.eclipse.kapua.service.authentication.ApiKeyCredentials;
import org.eclipse.kapua.service.authentication.AuthenticationService;
import org.eclipse.kapua.service.authentication.EmailPasswordCredentials;
import org.eclipse.kapua.service.authentication.JwtCredentials;
import org.eclipse.kapua.service.authentication.RefreshTokenCredentials;
import org.eclipse.kapua.service.authentication.UsernamePasswordCredentials;
import org.eclipse.kapua.service.authentication.credential.Credential;
import org.eclipse.kapua.service.authentication.credential.CredentialFactory;
import org.eclipse.kapua.service.authentication.credential.CredentialListResult;
import org.eclipse.kapua.service.authentication.credential.CredentialService;
import org.eclipse.kapua.service.authentication.shiro.EmailPasswordCredentialsImpl;
import org.eclipse.kapua.service.authentication.token.AccessToken;
import org.eclipse.kapua.service.device.registry.Device;
import org.eclipse.kapua.service.device.registry.DeviceQuery;
import org.eclipse.kapua.service.user.User;
import org.eclipse.kapua.service.user.UserCreator;
import org.eclipse.kapua.service.user.UserFactory;
import org.eclipse.kapua.service.user.UserService;
import org.glassfish.jersey.media.multipart.FormDataContentDisposition;
import org.glassfish.jersey.media.multipart.FormDataParam;


import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken.Payload;
import com.google.api.client.json.Json;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiImplicitParam;
import io.swagger.annotations.ApiImplicitParams;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import io.swagger.annotations.Authorization;

@Api(value = "File Repo", authorizations = { @Authorization(value = "kapuaAccessToken") })
@Path("{scopeId}/file-repo")
public class FileRepos extends AbstractKapuaResource {
	
	private final KapuaLocator locator = KapuaLocator.getInstance();
    private final FileRepoService fileRepoService = locator.getService(FileRepoService.class);
    private final FileRepoFactory fileRepoFactory = locator.getFactory(FileRepoFactory.class);
    private static final SystemSetting systemSetting = SystemSetting.getInstance();
 	/**
     * Creates a new FileRepo based on the information provided in FileRepoCreator
     * parameter.
     *
     * @param scopeId
     *            The {@link ScopeId} in which to create the {@link FileRepo}
     * @param FileRepoCreator
     *            Provides the information for the new {@link FileRepo} to be created.
     * @return The newly created {@link FileRepo} object.
     * @throws Exception
     *             Whenever something bad happens. See specific {@link KapuaService} exceptions.
     * @since 1.0.0
     */
    @ApiOperation(nickname = "fileRepoCreate",
            value = "Create an FileRepo",  //
            notes = "Creates a new FileRepo based on the information provided in FileRepoCreator parameter.",  //
            response = FileRepo.class)
    @POST
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    public FileRepo create(
    		@FormDataParam("file") InputStream fileInputStream,
            @FormDataParam("file") FormDataContentDisposition fileMetaData,
            @PathParam("scopeId") ScopeId scopeId,
            @FormDataParam("ttl") String ttl
             ) throws Exception 
    {
    	FileRepoCreator fileRepoCreator = new FileRepoCreatorImpl(scopeId);
    	String buildType = systemSetting.getString(SystemSettingKey.BUILD_TYPE);
    	// Generate a random filename using UUID
    	String userName = System.getProperty("user.name");
        String extension = fileRepoService.getFileExtension(fileMetaData.getFileName());
        String randomFileName = fileRepoService.generateRandomFileName(extension);
        String uploadedFileLocation = "";
        String thumbnailLocation = "";
        String uploadDir="";
        String uploadThumbnailDir="";

        
        switch(buildType) {
	case "vm":
	        uploadedFileLocation = "/home/"+userName+"/upload/" + randomFileName;
	        thumbnailLocation = "/home/"+userName+"/upload/thumbnail/" + randomFileName;
	        uploadDir="/home/"+userName+"/upload/";
	        uploadThumbnailDir="/home/"+userName+"/upload/thumbnail";

	    break;
	case "docker":
	    String dockerExtensionPath = systemSetting.getString(SystemSettingKey.DOCKER_FILEREPO_STORAGE);
	        uploadedFileLocation = dockerExtensionPath+"/upload/" + randomFileName;
	        thumbnailLocation = dockerExtensionPath+"/upload/thumbnail/" + randomFileName;
	        uploadDir=dockerExtensionPath+"/upload/";
	        uploadThumbnailDir=dockerExtensionPath+"/upload/thumbnail";
	    break;
        }
        
        File up_dir = new File(uploadDir);
        File th_dir = new File(uploadThumbnailDir);
        
        
        if(!up_dir.exists())
        {
          up_dir.mkdirs();
        }
        if(!th_dir.exists())
        {
          th_dir.mkdirs();
        }
        
       

        // Save the file
        fileRepoService.writeToFile(fileInputStream, uploadedFileLocation);
    	
        fileRepoCreator.setScopeId(scopeId);
        fileRepoCreator.setDir(uploadDir);
        fileRepoCreator.setImagePath(uploadedFileLocation);
        

        
        String rawFileName = fileMetaData.getFileName();
        byte[] fileNameBytes = rawFileName.getBytes(StandardCharsets.ISO_8859_1);
        String decodedFileName = new String(fileNameBytes, StandardCharsets.UTF_8);
        
        fileRepoCreator.setName(decodedFileName);
       
        if ( extension.equals(".jpg") || extension.equals(".jpeg") || extension.equals(".png")) {
        	fileRepoCreator.setThumbnail(thumbnailLocation);
            String path = fileRepoService.zipImageFile(new File(uploadedFileLocation),new File(thumbnailLocation),300,0,0.6f);
            BufferedImage bimg = ImageIO.read(new File(path));
            int width = bimg.getWidth();
            int height = bimg.getHeight();
            fileRepoCreator.setThumbnailRatio(String.valueOf(width)+"*"+String.valueOf(height));
        }
        
        if ( ttl != null  ) {
        	fileRepoCreator.setTtl(ttl);
        }
        return  KapuaSecurityUtils.doPrivileged(()->fileRepoService.create(fileRepoCreator));
    }
    
    @GET
    @Path("/chunk-start")
    @Produces(MediaType.APPLICATION_JSON)
    public Response startUpload(@PathParam("scopeId") ScopeId scopeId) {
        // 生成一個唯一的 uploadId 作為上傳工作的標識符
    	UUID uuid = UUID.randomUUID();
        String uid = uuid.toString();
  
        return Response.status(Response.Status.OK).entity("{\"uuid\":\"" + uid + "\"}").build();
    }
    
    @POST
    @Path("/chunk")
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    public FileRepo uploadChunk(@FormDataParam("file") InputStream fileInputStream,
    							@FormDataParam("file") FormDataContentDisposition fileMetaData,
                                @FormDataParam("chunkNumber") int chunkNumber,
                                @FormDataParam("extension") String extension,
                                @FormDataParam("uuid") String uuid,
                                @PathParam("scopeId") ScopeId scopeId,
                                @FormDataParam("ttl") String ttl,
                                @FormDataParam("name") String name,
                                @FormDataParam("totalChunks") int totalChunks) {
    	String userName = System.getProperty("user.name");
    	String buildType = systemSetting.getString(SystemSettingKey.BUILD_TYPE);
    	FileRepo repo = null;
        try {
        	fileRepoService.saveChunk(fileInputStream, chunkNumber, uuid);
            if (fileRepoService.isUploadComplete(totalChunks, uuid)) {
                // 如果所有分段都上傳完成，合併分段文件;
            	try {
                    
                    String randomFileName = fileRepoService.generateRandomFileName("."+extension);
                    String finalFileName = "/home/"+userName+"/upload/" + randomFileName; // 最終文件的名稱和路徑
                    String uploadPath="/home/"+userName+"/upload/";
                    String thumbnailLocation = "/home/"+userName+"/upload/thumbnail/" + randomFileName;
                    String uploadThumbnailPath="/home/"+userName+"/upload/thumbnail";
                    
            	switch(buildType) {
        	case "vm":
                    finalFileName = "/home/"+userName+"/upload/" + randomFileName; // 最終文件的名稱和路徑
                    uploadPath="/home/"+userName+"/upload/";
                    thumbnailLocation = "/home/"+userName+"/upload/thumbnail/" + randomFileName;
                    uploadThumbnailPath="/home/"+userName+"/upload/thumbnail";
        	    break;
        	case "docker":
        	    String dockerExtensionPath = systemSetting.getString(SystemSettingKey.DOCKER_FILEREPO_STORAGE);
                    finalFileName = dockerExtensionPath+"/upload/" + randomFileName; // 最終文件的名稱和路徑
                    uploadPath = dockerExtensionPath+"/upload/";
                    thumbnailLocation = dockerExtensionPath+"/upload/thumbnail/" + randomFileName;
                    uploadThumbnailPath = dockerExtensionPath+"/upload/thumbnail";
        	    break;
        	}
                    
                    fileRepoService.mergeChunks(totalChunks, finalFileName, uuid);
                    FileRepoCreator fileRepoCreator = new FileRepoCreatorImpl(scopeId);
                    File up_dir = new File(uploadPath);
                    File th_dir = new File(uploadThumbnailPath);
                    if(!up_dir.exists())
                    {
                      up_dir.mkdirs();
                    }
                    if(!th_dir.exists())
                    {
                      th_dir.mkdirs();
                    }

                    fileRepoCreator.setScopeId(scopeId);
                    fileRepoCreator.setDir(uploadPath);
                    fileRepoCreator.setImagePath(finalFileName);
                    
//                    String rawFileName = fileMetaData.getFileName();
//                    byte[] fileNameBytes = rawFileName.getBytes(StandardCharsets.ISO_8859_1);
//                    String decodedFileName = new String(fileNameBytes, StandardCharsets.UTF_8);
                    System.out.println("name="+name);
                    fileRepoCreator.setName(name);
                   
                    if ( extension.equals("jpg") || extension.equals("jpeg") || extension.equals("png")) {
                    	fileRepoCreator.setThumbnail(thumbnailLocation);
                        String path = fileRepoService.zipImageFile(new File(finalFileName),new File(thumbnailLocation),300,0,0.6f);
                        BufferedImage bimg = ImageIO.read(new File(path));
                        int width = bimg.getWidth();
                        int height = bimg.getHeight();
                        fileRepoCreator.setThumbnailRatio(String.valueOf(width)+"*"+String.valueOf(height));
                    }
                    
                    if ( ttl != null  ) {
                    	fileRepoCreator.setTtl(ttl);
                    }
                    repo = KapuaSecurityUtils.doPrivileged(()->fileRepoService.create(fileRepoCreator));
            	} catch (KapuaException e) {
            		
            	}
            	//return Response.status(Response.Status.OK).entity("{\"fileRepo\":\"" +  + "\"}").build();
            } else {
            	//return Response.status(Response.Status.OK).entity("{\"message\":\"" + "chunk successful" + "\"}").build();
            }
        } catch (IOException e) {
        	String errorMessage = "Error uploading chunk: " + e.getMessage();
        	System.out.println(errorMessage);
            //return Response.status(Response.Status.INTERNAL_SERVER_ERROR).entity("{\"error\":\"" + errorMessage + "\"}").build();
        }
        return repo;
    }
    
    
    /**
     * Creates a new FileRepo based on the information provided in FileRepoCreator
     * parameter.
     *
     * @param scopeId
     *            The {@link ScopeId} in which to create the {@link FileRepo}
     * @param FileRepoCreator
     *            Provides the information for the new {@link FileRepo} to be created.
     * @return The newly created {@link FileRepo} object.
     * @throws Exception
     *             Whenever something bad happens. See specific {@link KapuaService} exceptions.
     * @since 1.0.0
     */
    @ApiOperation(nickname = "fileRepoCreate by account",
            value = "Create an FileRepo by account",  //
            notes = "Creates a new FileRepo based on the information provided in FileRepoCreator parameter.",  //
            response = FileRepo.class)
    @POST
    @Path("/account")
    @ApiImplicitParams({
        @ApiImplicitParam(name = "Authorization", value = "Access Token", required = true, allowEmptyValue = false, paramType = "header", dataType = "string", format = "string", example = "Bearer access_token")
    })
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    public FileRepo create_account(
    		@FormDataParam("file") InputStream fileInputStream,
            @FormDataParam("file") FormDataContentDisposition fileMetaData,
            @PathParam("scopeId") ScopeId scopeId,
            @FormDataParam("ttl") String ttl
             ) throws Exception 
    {
    	FileRepoCreator fileRepoCreator = new FileRepoCreatorImpl(scopeId);
    	// Generate a random filename using UUID
    	String userName = System.getProperty("user.name");
    	String buildType = systemSetting.getString(SystemSettingKey.BUILD_TYPE);
        String extension = fileRepoService.getFileExtension(fileMetaData.getFileName());
        String randomFileName = fileRepoService.generateRandomFileName(extension);
        String uploadedFileLocation = "/home/"+userName+"/upload/" + randomFileName;
        String thumbnailLocation = "/home/"+userName+"/upload/thumbnail/" + randomFileName;
        String uploadDir="/home/"+userName+"/upload/";
        String uploadThumbnailPath="/home/"+userName+"/upload/thumbnail";    
	switch(buildType) {
	case "vm":
	        uploadedFileLocation = "/home/"+userName+"/upload/" + randomFileName;
	        thumbnailLocation = "/home/"+userName+"/upload/thumbnail/" + randomFileName;
	        uploadDir="/home/"+userName+"/upload/";
	        uploadThumbnailPath="/home/"+userName+"/upload/thumbnail";

	    break;
	case "docker":
	    String dockerExtensionPath = systemSetting.getString(SystemSettingKey.DOCKER_FILEREPO_STORAGE);
	        uploadedFileLocation = dockerExtensionPath+"/upload/" + randomFileName;
	        thumbnailLocation = dockerExtensionPath+"/upload/thumbnail/" + randomFileName;
	        uploadDir=dockerExtensionPath+"/upload/";
	        uploadThumbnailPath=dockerExtensionPath+"/upload/thumbnail";
	    break;
	}
        File up_dir = new File(uploadDir);
        File th_dir = new File(uploadThumbnailPath);
        if(!up_dir.exists())
        {
          up_dir.mkdirs();
        }
        if(!th_dir.exists())
        {
          th_dir.mkdirs();
        }
        
        // Save the file
        fileRepoService.writeToFile(fileInputStream, uploadedFileLocation);
    	
        fileRepoCreator.setScopeId(scopeId);
        fileRepoCreator.setDir(uploadDir);
        fileRepoCreator.setImagePath(uploadedFileLocation);
        
        String rawFileName = fileMetaData.getFileName();
        byte[] fileNameBytes = rawFileName.getBytes(StandardCharsets.ISO_8859_1);
        String decodedFileName = new String(fileNameBytes, StandardCharsets.UTF_8);
        
        fileRepoCreator.setName(decodedFileName);
        
        if ( extension.equals(".jpg") || extension.equals(".jpeg") || extension.equals(".png") ) {
        	fileRepoCreator.setThumbnail(thumbnailLocation);
            
            String path = fileRepoService.zipImageFile(new File(uploadedFileLocation),new File(thumbnailLocation),300,0,0.6f);
            BufferedImage bimg = ImageIO.read(new File(path));
            int width = bimg.getWidth();
            int height = bimg.getHeight();
            fileRepoCreator.setThumbnailRatio(String.valueOf(width)+"*"+String.valueOf(height));
        }
        
        if ( ttl != null  ) {
        	fileRepoCreator.setTtl(ttl);
        }
        return  fileRepoService.create_account(fileRepoCreator);
    }
    
    
    /**
     * Counts the results with the given {@link FileRepoQuery} parameter.
     *
     * @param scopeId
     *            The {@link ScopeId} in which to search results.
     * @param query
     *            The {@link FileRepoQuery} to use to filter results.
     * @return The count of all the result matching the given {@link FileRepoQuery} parameter.
     * @throws Exception
     *             Whenever something bad happens. See specific {@link KapuaService} exceptions.
     * @since 1.0.0
     */
    @ApiOperation(nickname = "fileRepoCount", value = "Counts the FileRepos", notes = "Counts the FileRepos with the given FileRepoQuery parameter returning the number of matching FileRepos", response = CountResult.class)
    @POST
    @Path("/count")
    @Consumes({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    public CountResult count(
            @ApiParam(value = "The ScopeId in which to count results", required = true, defaultValue = DEFAULT_SCOPE_ID) @PathParam("scopeId") ScopeId scopeId,
            @ApiParam(value = "The FileRepoQuery to use to filter count results", required = true) FileRepoQuery query) throws Exception {
        query.setScopeId(scopeId);

        return new CountResult(fileRepoService.count(query));
    }
 	
 	
    /**
     * Returns the FileRepo specified by the "fileRepoId" path parameter.
     *
     * @param scopeId
     *            The {@link ScopeId} of the requested {@link FileRepo}.
     * @param fileRepoId
     *            The id of the requested Device.
     * @return The requested FileRepo object.
     * @throws Exception
     *             Whenever something bad happens. See specific {@link KapuaService} exceptions.
     * @since 1.0.0
     */
    @ApiOperation(nickname = "fileRepoFind", value = "Get a FileRepo", notes = "Returns the FileRepo specified by the \"fileRepoId\" path parameter.", response = FileRepo.class)
    @GET
    @Path("/{fileRepoId}")
    @Produces({ MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML })
    public FileRepo find(
            @ApiParam(value = "The ScopeId of the requested FileRepo", required = true, defaultValue = DEFAULT_SCOPE_ID) 
            @PathParam("scopeId") ScopeId scopeId,
            @ApiParam(value = "The id of the requested FileRepo", required = true)
            @PathParam("fileRepoId") EntityId fileRepoId) throws Exception {
    		
    		FileRepo filerepo = fileRepoService.find(scopeId, fileRepoId);
	        if (filerepo != null) {
	            return filerepo;
	        } else {
	            throw new KapuaEntityNotFoundException(FileRepo.TYPE, fileRepoId);
	        }
    }
    
    /**
     * Returns the FileRepo specified by the "fileRepoId" path parameter.
     *
     * @param scopeId
     *            The {@link ScopeId} of the requested {@link FileRepo}.
     * @param fileRepoId
     *            The id of the requested Device.
     * @return The requested FileRepo object.
     * @throws Exception
     *             Whenever something bad happens. See specific {@link KapuaService} exceptions.
     * @since 1.0.0
     */
    @ApiOperation(nickname = "fileRepoGetImage", value = "Get a FileRepo Image", notes = "Returns the FileRepo Image by the \"fileRepoId\" path parameter.")
    @GET
    @Path("/{fileRepoId}/get-image")
    public Response getImage(
            @ApiParam(value = "The ScopeId of the requested FileRepo", required = true, defaultValue = DEFAULT_SCOPE_ID) 
            @PathParam("scopeId") ScopeId scopeId,
            @ApiParam(value = "The id of the requested FileRepo", required = true)
            @PathParam("fileRepoId") EntityId fileRepoId) throws Exception {
          
            FileRepo filerepo = fileRepoService.find(scopeId, fileRepoId);
            File imageFile = new File(filerepo.getImagePath());
            
            if (!imageFile.exists()) {
                return Response.status(404).entity("File not found").build();
            }

            BufferedImage image = ImageIO.read(imageFile);
            String fileExtension = fileRepoService.getFileExtension(filerepo.getImagePath());

            if (".jpg".equalsIgnoreCase(fileExtension)) {
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                ImageIO.write(image, "jpg", baos);
                byte[] imageData = baos.toByteArray();
                return Response.ok(imageData, "image/jpeg").build();
            } else if (".png".equalsIgnoreCase(fileExtension)) {
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                ImageIO.write(image, "png", baos);
                byte[] imageData = baos.toByteArray();
                return Response.ok(imageData, "image/png").build();
            } else {
            	StreamingOutput fileStream = new StreamingOutput() {
                    @Override
                    public void write(java.io.OutputStream output) throws IOException, WebApplicationException {
                        try (InputStream inputStream = Files.newInputStream(imageFile.toPath())) {
                            byte[] buffer = new byte[4096];
                            int bytesRead;
                            while ((bytesRead = inputStream.read(buffer)) != -1) {
                                output.write(buffer, 0, bytesRead);
                            }
                        } catch (Exception e) {
                            throw new WebApplicationException("File Not Found !!");
                        }
                    }
                };
                return Response.ok(fileStream, MediaType.APPLICATION_OCTET_STREAM)
                        .header("Content-Disposition", "attachment; filename="+filerepo.getName()+"")
                        .build();
            }
    }
    
    /**
     * Returns the FileRepo specified by the "fileRepoId" path parameter.
     *
     * @param scopeId
     *            The {@link ScopeId} of the requested {@link FileRepo}.
     * @param fileRepoId
     *            The id of the requested Device.
     * @return The requested FileRepo object.
     * @throws Exception
     *             Whenever something bad happens. See specific {@link KapuaService} exceptions.
     * @since 1.0.0
     */
    @ApiOperation(nickname = "fileRepoActivate", value = "Activate FileRepo", notes = "Activate FileRepo by the \"fileRepoId\" path parameter.")
    @PUT
    @Path("/{fileRepoId}/activate")
    @Produces({ MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML })
    @ApiImplicitParams({
        @ApiImplicitParam(name = "Authorization", value = "Access Token", required = true, allowEmptyValue = false, paramType = "header", dataType = "string", format = "string", example = "Bearer access_token")
    })
    public FileRepo activate(
            @ApiParam(value = "The ScopeId of the requested FileRepo", required = true, defaultValue = DEFAULT_SCOPE_ID) 
            @PathParam("scopeId") ScopeId scopeId,
            @ApiParam(value = "The id of the requested FileRepo", required = true)
            @PathParam("fileRepoId") EntityId fileRepoId) throws Exception {
    	  	
    		FileRepo filerepo = fileRepoService.find(scopeId, fileRepoId);
    		if(filerepo==null) {
    		    throw new KapuaEntityNotFoundException("File repo","not find image file"); 
    		}
    		
    		filerepo.setTtl("");
    		
    		return KapuaSecurityUtils.doPrivileged(()->fileRepoService.update(filerepo));
    }
    
    /**
     * Returns the FileRepo specified by the "fileRepoId" path parameter.
     *
     * @param scopeId
     *            The {@link ScopeId} of the requested {@link FileRepo}.
     * @param fileRepoId
     *            The id of the requested Device.
     * @return The requested FileRepo object.
     * @throws Exception
     *             Whenever something bad happens. See specific {@link KapuaService} exceptions.
     * @since 1.0.0
     */
    @ApiOperation(nickname = "fileRepoGetThumbnail", value = "Get a FileRepo Thumbnail", notes = "Returns the FileRepo Thumbnail by the \"fileRepoId\" path parameter.")
    @GET
    @Path("/{fileRepoId}/get-thumbnail")
    public Response getThumbnail(
            @ApiParam(value = "The ScopeId of the requested FileRepo", required = true, defaultValue = DEFAULT_SCOPE_ID) 
            @PathParam("scopeId") ScopeId scopeId,
            @ApiParam(value = "The id of the requested FileRepo", required = true)
            @PathParam("fileRepoId") EntityId fileRepoId) throws Exception {
      
            FileRepo filerepo = fileRepoService.find(scopeId, fileRepoId);
            File imageFile = new File(filerepo.getThumbnail());
            
            if (!imageFile.exists()) {
                return Response.status(404).entity("Image not found").build();
            }

            BufferedImage image = ImageIO.read(imageFile);
            String fileExtension = fileRepoService.getFileExtension(filerepo.getThumbnail());

            if (".jpg".equalsIgnoreCase(fileExtension)) {
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                ImageIO.write(image, "jpg", baos);
                byte[] imageData = baos.toByteArray();
                return Response.ok(imageData, "image/jpeg").build();
            } else if (".png".equalsIgnoreCase(fileExtension)) {
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                ImageIO.write(image, "png", baos);
                byte[] imageData = baos.toByteArray();
                return Response.ok(imageData, "image/png").build();
            } else {
                return Response.status(415).entity("Unsupported file type").build();
            }
    }
    
    /**
     * Queries the results with the given {@link FileRepoQuery} parameter.
     *
     * @param scopeId
     *            The {@link ScopeId} in which to search results.
     * @param query
     *            The {@link FileRepoQuery} to use to filter results.
     * @return The {@link FileRepoListResult} of all the result matching the given {@link FileRepoQuery} parameter.
     * @throws Exception
     *             Whenever something bad happens. See specific {@link KapuaService} exceptions.
     * @since 1.0.0
     */
    @ApiOperation(nickname = "fileRepoQuery",
            value = "Queries the FileRepos", //
            notes = "Queries the FileRepos with the given FileRepoQuery parameter returning all matching FileRepos", //
            response = FileRepoListResult.class) //
    @POST
    @Path("query")
    @Consumes({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    public FileRepoListResult query(
            @ApiParam(value = "The ScopeId in which to search results.", required = true, defaultValue = DEFAULT_SCOPE_ID) @PathParam("scopeId") ScopeId scopeId, //
            @ApiParam(value = "The FileRepoQuery to use to filter results.", required = true) FileRepoQuery query) throws Exception {
        query.setScopeId(scopeId);

        return fileRepoService.query(query);
    }
    
    /**
     * Deletes the FileRepo specified by the "fileRepoId" path parameter.
     *
     * @param scopeId
     *            The ScopeId of the requested {@link FileRepo}.
     * @param fileRepoId
     *            The id of the FileRepo to be deleted.
     * @return HTTP 200 if operation has completed successfully.
     * @throws Exception
     *             Whenever something bad happens. See specific {@link KapuaService} exceptions.
     * @since 1.0.0
     */
    @ApiOperation(nickname = "fileRepoDelete",
            value = "Delete an FileRepo",  //
            notes = "Deletes the FileRepo specified by the \"fileRepoId\" path parameter.")
    @DELETE
    @Path("{fileRepoId}")
    public Response deleteFileRepo(
            @ApiParam(value = "The ScopeId of the FileRepo to delete.", required = true, defaultValue = DEFAULT_SCOPE_ID) @PathParam("scopeId") ScopeId scopeId, //
            @ApiParam(value = "The id of the FileRepo to be deleted", required = true) @PathParam("fileRepoId") EntityId fileRepoId) throws Exception {
        fileRepoService.delete(scopeId, fileRepoId);

        return returnOk();
    }
    
    /**
     * Deletes the FileRepo specified by the "fileRepoId" path parameter.
     *
     * @param scopeId
     *            The ScopeId of the requested {@link FileRepo}.
     * @param fileRepoId
     *            The id of the FileRepo to be deleted.
     * @return HTTP 200 if operation has completed successfully.
     * @throws Exceptionhttp://localhost:8081/v1/
     *             Whenever something bad happens. See specific {@link KapuaService} exceptions.
     * @since 1.0.0
     */
    @ApiOperation(nickname = "fileRepoDelete by account",
            value = "Delete an FileRepo by account",  //
            notes = "Deletes the FileRepo specified by the \"fileRepoId\" path parameter.")
    @DELETE
    @ApiImplicitParams({
        @ApiImplicitParam(name = "Authorization", value = "Access Token", required = true, allowEmptyValue = false, paramType = "header", dataType = "string", format = "string", example = "Bearer access_token")
    })
    @Path("{fileRepoId}/account")
    public Response deleteFileRepo_account(
            @ApiParam(value = "The     @GET\n" + 
            		"    @Path(\"/pdf\")\n" + 
            		"    public Response downloadPdfFile() {\n" + 
            		"        \n" + 
            		"    }ScopeId of the FileRepo to delete.", required = true, defaultValue = DEFAULT_SCOPE_ID) @PathParam("scopeId") ScopeId scopeId, //
            @ApiParam(value = "The id of the FileRepo to be deleted", required = true) @PathParam("fileRepoId") EntityId fileRepoId) throws Exception {
        fileRepoService.delete_account(scopeId, fileRepoId);

        return returnOk();
    }


    
    @GET
    @ApiOperation(nickname = "CleanfileRepo",
    value = "Clean an FileRepo",  //
    notes = "Clean the FileRepo.")
    @Path("/clean-timeout")
    public Response cleanFileRepo(
            @ApiParam(value = "The ScopeId of the FileRepo to delete.", required = true, defaultValue = DEFAULT_SCOPE_ID) @PathParam("scopeId") ScopeId scopeId) throws Exception {
        
    	fileRepoService.cleanTtlFile();
        return returnOk();
    }
}


