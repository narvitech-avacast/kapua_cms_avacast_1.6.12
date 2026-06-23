/*******************************************************************************
 * Copyright (c) 2011, 2018 Eurotech and/or its affiliates and others
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Eurotech - initial API and implementation
 *******************************************************************************/
package org.eclipse.kapua.service.file.repo.internal;

import com.google.common.collect.Lists;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.UUID;
import java.util.List;
import javax.imageio.ImageIO;

import org.apache.commons.lang3.StringUtils;
import org.eclipse.kapua.KapuaDuplicateNameException;
import org.eclipse.kapua.KapuaDuplicateNameInAnotherAccountError;
import org.eclipse.kapua.KapuaEntityNotFoundException;
import org.eclipse.kapua.KapuaException;
import org.eclipse.kapua.KapuaIllegalArgumentException;
import org.eclipse.kapua.KapuaMaxNumberOfItemsReachedException;
import org.eclipse.kapua.commons.configuration.AbstractKapuaConfigurableResourceLimitedService;
import org.eclipse.kapua.commons.jpa.EntityManagerFactory;
import org.eclipse.kapua.commons.model.query.predicate.AttributePredicateImpl;
import org.eclipse.kapua.commons.security.KapuaSecurityUtils;
import org.eclipse.kapua.commons.setting.system.SystemSetting;
import org.eclipse.kapua.commons.setting.system.SystemSettingKey;
import org.eclipse.kapua.commons.util.AccessTokenGenerator;
import org.eclipse.kapua.commons.util.ArgumentValidator;
import org.eclipse.kapua.commons.util.CommonsValidationRegex;
import org.eclipse.kapua.event.ServiceEvent;
import org.eclipse.kapua.locator.KapuaLocator;
import org.eclipse.kapua.locator.KapuaProvider;
import org.eclipse.kapua.model.domain.Actions;
import org.eclipse.kapua.model.domain.Domain;
import org.eclipse.kapua.model.id.KapuaId;
import org.eclipse.kapua.model.query.KapuaQuery;
import org.eclipse.kapua.model.query.predicate.QueryPredicate;
import org.eclipse.kapua.service.account.AccountDomains;
import org.eclipse.kapua.service.authorization.AuthorizationService;
import org.eclipse.kapua.service.authorization.permission.PermissionFactory;
import org.eclipse.kapua.service.file.repo.FileRepo;
import org.eclipse.kapua.service.file.repo.FileRepoCreator;
import org.eclipse.kapua.service.file.repo.FileRepoDomains;
import org.eclipse.kapua.service.file.repo.FileRepoFactory;
import org.eclipse.kapua.service.file.repo.FileRepoListResult;
import org.eclipse.kapua.service.file.repo.FileRepoQuery;
import org.eclipse.kapua.service.file.repo.FileRepoService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * {@link DeviceRegistryService} implementation.
 *
 * @since 1.0.0
 */
@KapuaProvider
public class FileRepoServiceImpl extends AbstractKapuaConfigurableResourceLimitedService<FileRepo, FileRepoCreator, FileRepoService, FileRepoListResult, FileRepoQuery, FileRepoFactory>
        implements FileRepoService {

    private static final Logger LOGGER = LoggerFactory.getLogger(FileRepoServiceImpl.class);
    private static final KapuaLocator LOCATOR = KapuaLocator.getInstance();
    private static final KapuaLocator locator = KapuaLocator.getInstance();
    private static final AuthorizationService AUTHORIZATION_SERVICE = LOCATOR.getService(AuthorizationService.class);
    private static final PermissionFactory PERMISSION_FACTORY = LOCATOR.getFactory(PermissionFactory.class);
    private static final SystemSetting systemSetting = SystemSetting.getInstance();
    /**
     * Constructor
     */
    public FileRepoServiceImpl() {
    	super(FileRepoService.class.getName(), FileRepoDomains.FILEREPO_DOMAIN, FileRepoEntityManagerFactory.getInstance(), FileRepoService.class, FileRepoFactory.class);
    } 

    // Operations implementation
    public FileRepo create(FileRepoCreator fileRepoCreator) throws KapuaException {
        //
        // Argument validation
        ArgumentValidator.notNull(fileRepoCreator, "fileRepoCreator");
        ArgumentValidator.notNull(fileRepoCreator.getScopeId(), "fileRepoCreator.scopeId");
        ArgumentValidator.notEmptyOrNull(fileRepoCreator.getName(), "fileRepoCreator.name");
      
        //
        // Check Access
        //        authorizationService.checkPermission(permissionFactory.newPermission(
        //                AccountDomains.ACCOUNT_DOMAIN, Actions.write, accountCreator.getScopeId()));

        return entityManagerSession.onTransactedInsert(entityManager -> FileRepoDAO.create(entityManager, fileRepoCreator));
    }
    
 // Operations implementation
    @Override
    public FileRepo create_account(FileRepoCreator fileRepoCreator) throws KapuaException {
        //
        // Argument validation
        ArgumentValidator.notNull(fileRepoCreator, "fileRepoCreator");
        ArgumentValidator.notNull(fileRepoCreator.getScopeId(), "fileRepoCreator.scopeId");
        ArgumentValidator.notEmptyOrNull(fileRepoCreator.getName(), "fileRepoCreator.name");
      
        //
        // Check Access
        AUTHORIZATION_SERVICE.checkPermission(PERMISSION_FACTORY.newPermission(
                        FileRepoDomains.FILEREPO_DOMAIN, Actions.write, fileRepoCreator.getScopeId()));

        return entityManagerSession.onTransactedInsert(entityManager -> FileRepoDAO.create(entityManager, fileRepoCreator));
    }


    @Override
    public FileRepo update(FileRepo fileRepo) throws KapuaException {

    	// Check Access
        AUTHORIZATION_SERVICE.checkPermission(PERMISSION_FACTORY.newPermission(FileRepoDomains.FILEREPO_DOMAIN, Actions.write, fileRepo.getScopeId()));
    	return entityManagerSession.onTransactedResult(entityManager -> {
            FileRepo currentFileRepo = FileRepoDAO.find(entityManager, fileRepo.getScopeId(), fileRepo.getId());
            if (currentFileRepo == null) {
                throw new KapuaEntityNotFoundException(FileRepo.TYPE, fileRepo.getId());
            }
            // Update
            return FileRepoDAO.update(entityManager, fileRepo);
        });
    }

    @Override
    public FileRepo find(KapuaId scopeId, KapuaId entityId) throws KapuaException {
        return entityManagerSession.onResult(entityManager -> FileRepoDAO.find(entityManager, scopeId, entityId));
    }

    @Override
    public FileRepoListResult query(KapuaQuery<FileRepo> query) throws KapuaException {
        return entityManagerSession.onResult(entityManager -> FileRepoDAO.query(entityManager, query));
    }

    @Override
    public long count(KapuaQuery<FileRepo> query) throws KapuaException {
        return entityManagerSession.onResult(entityManager -> FileRepoDAO.count(entityManager, query));
    }

    @Override
    public void delete(KapuaId scopeId, KapuaId fileRepoId) throws KapuaException {
        
        FileRepo file_repo = this.find(scopeId, fileRepoId);
        String buildType = systemSetting.getString(SystemSettingKey.BUILD_TYPE);
        String uploadedFileLocation = "";
        String thumbnailLocation = "";
        String uploadDir="";
        String uploadThumbnailDir="";
        switch(buildType) {
        
        case "vm":
            break;
        case "docker":
	    String dockerExtensionPath = systemSetting.getString(SystemSettingKey.DOCKER_FILEREPO_STORAGE);
	    uploadedFileLocation = file_repo.getImagePath();
	    thumbnailLocation = file_repo.getThumbnail();
	    File upFile = new File(uploadedFileLocation);
	    File thFile = new File(thumbnailLocation);
	        
	        
	    if(upFile.exists()){
		upFile.delete();
	    }
	    if(thFile.exists()){
		   if (thFile.delete()) {
		        System.out.println("remove thumbnail succ!:"+thumbnailLocation);
		    } else {
		        System.out.println("remove thumbnail failed!:"+thumbnailLocation);
		    }
	    }
            break;
        }
        //Stage2: remove device in database
        entityManagerSession.onTransactedAction(entityManager -> FileRepoDAO.delete(entityManager, scopeId, fileRepoId));
    }
    
    @Override
    public void delete_account(KapuaId scopeId, KapuaId fileRepoId) throws KapuaException {
        
    	Actions action = Actions.delete;
    	AUTHORIZATION_SERVICE.checkPermission(
    			PERMISSION_FACTORY.newPermission(FileRepoDomains.FILEREPO_DOMAIN, action, scopeId));
        FileRepo file_repo = this.find(scopeId, fileRepoId);

        entityManagerSession.onTransactedAction(entityManager -> FileRepoDAO.delete(entityManager, scopeId, fileRepoId));
    }

    @Override
    public FileRepo findByName(String name) throws KapuaException {
        //
        // Argument validation
        ArgumentValidator.notEmptyOrNull(name, "name");

        //
        // Do find
        return entityManagerSession.onResult(em -> {
            FileRepo fileRepo = FileRepoDAO.findByName(em, name);

            //
            // Check Access
//            if (account != null) {
//                authorizationService.checkPermission(permissionFactory.newPermission(
//                        AccountDomains.ACCOUNT_DOMAIN, Actions.read, account.getId()));
//            }

            return fileRepo;
        });
    }

	@Override
    public FileRepo find(KapuaId fileRepoId) throws KapuaException {
        //
        // Argument validation
        ArgumentValidator.notNull(fileRepoId, "fileRepoId");

        //
        // Check Access
        //        authorizationService.checkPermission(permissionFactory
        //                .newPermission(AccountDomains.ACCOUNT_DOMAIN, Actions.read, accountId));

        //
        // Make sure account exists
        return findById(fileRepoId);
    }
	
	/**
     * Find an {@link FileRepo} without authorization checks.
     *
     * @param fileRepoId
     * @return
     * @throws KapuaException
     * @since 1.0.0
     */
    private FileRepo findById(KapuaId fileRepoId) throws KapuaException {
        //
        // Argument Validation
        ArgumentValidator.notNull(fileRepoId, "fileRepoId");

        //
        // Do find
        return entityManagerSession.onResult(em -> FileRepoDAO.find(em, null, fileRepoId));
    }
    
    @Override
    public void saveChunk(InputStream inputStream, int chunkNumber, String uuid) throws IOException {
        // 在指定目錄下創建分段文件
	String buildType = systemSetting.getString(SystemSettingKey.BUILD_TYPE);
    	String userName = System.getProperty("user.name");
    	System.out.println("get host name:"+userName);
    	File chunk_dir = null;
        String chunkFileName = "";
	switch(buildType) {
	case "vm": 
	       chunk_dir = new File("/home/"+userName+"/upload/chunk");
	       chunkFileName = "/home/"+userName+"/upload/chunk/" + uuid + "_" + chunkNumber + ".tmp";
	    break;
	case "docker":
	      String dockerExtensionPath = systemSetting.getString(SystemSettingKey.DOCKER_FILEREPO_STORAGE);
	      chunk_dir = new File(dockerExtensionPath+"/upload/chunk");
	      chunkFileName = dockerExtensionPath+"/upload/chunk/" + uuid + "_" + chunkNumber + ".tmp";
	    break;
	}
	
        if(!chunk_dir.exists())
        {
            boolean created = chunk_dir.mkdirs();
            if(!created) {
        	System.out.print("Fail to create dir");
            }
        }

        try (FileOutputStream fos = new FileOutputStream(chunkFileName)) {
            byte[] buffer = new byte[8192]; // 8KB的緩衝區
            int bytesRead; 
            while ((bytesRead = inputStream.read(buffer)) != -1) {
                fos.write(buffer, 0, bytesRead);
            }
        }

    }

    @Override
    public boolean isUploadComplete(int totalChunks, String uuid) {
        // 檢查是否已經收到了所有的分段
	String buildType = systemSetting.getString(SystemSettingKey.BUILD_TYPE);
    	String userName = System.getProperty("user.name");
        int count = 0;
        String chunkFileName="";
	switch(buildType) {
	case "vm":
	        for (int i = 1; i <= totalChunks; i++) {
		    chunkFileName = "/home/"+userName+"/upload/chunk/" + uuid + "_" + i + ".tmp";
	            if (new File(chunkFileName).exists()) {
	                count++;
	            }
	        }
	        return count == totalChunks;

	case "docker":
	      String dockerExtensionPath = systemSetting.getString(SystemSettingKey.DOCKER_FILEREPO_STORAGE);
	        for (int i = 1; i <= totalChunks; i++) {
		      chunkFileName = dockerExtensionPath+"/upload/chunk/" + uuid + "_" + i + ".tmp";
	            if (new File(chunkFileName).exists()) {
	                count++;
	            }
	        }
	        return count == totalChunks;

	}
	
	return false;
	



    }

    @Override
    public void mergeChunks(int totalChunks, String finalFileName, String uuid) throws IOException {
    	String userName = System.getProperty("user.name");
    	String buildType = systemSetting.getString(SystemSettingKey.BUILD_TYPE);

	switch(buildType) {
	case "vm":
	        // 合併所有分段文件為最終文件
	        try (FileOutputStream fos = new FileOutputStream(finalFileName)) {
	            for (int i = 1; i <= totalChunks; i++) {
	                String chunkFileName = "/home/"+userName+"/upload/chunk/" + uuid + "_" + i + ".tmp";
	                try (FileInputStream fis = new FileInputStream(chunkFileName)) {
	                    byte[] buffer = new byte[8192]; // 8KB的緩衝區
	                    int bytesRead;
	                    while ((bytesRead = fis.read(buffer)) != -1) {
	                        fos.write(buffer, 0, bytesRead);
	                    }
	                }
	            }
	        }
	        // 合併完成後，刪除分段文件
	        for (int i = 1; i <= totalChunks; i++) {
	            String chunkFileName = "/home/"+userName+"/upload/chunk/" + uuid + "_" + i + ".tmp";
	            new File(chunkFileName).delete();
	        }
            break;
	case "docker":
	    String dockerExtensionPath = systemSetting.getString(SystemSettingKey.DOCKER_FILEREPO_STORAGE);
	        // 合併所有分段文件為最終文件
	        try (FileOutputStream fos = new FileOutputStream(finalFileName)) {
	            for (int i = 1; i <= totalChunks; i++) {
	                String chunkFileName = dockerExtensionPath+"/upload/chunk/" + uuid + "_" + i + ".tmp";
	                try (FileInputStream fis = new FileInputStream(chunkFileName)) {
	                    byte[] buffer = new byte[8192]; // 8KB的緩衝區
	                    int bytesRead;
	                    while ((bytesRead = fis.read(buffer)) != -1) {
	                        fos.write(buffer, 0, bytesRead);
	                    }
	                }
	            }
	        }
	        // 合併完成後，刪除分段文件
	        for (int i = 1; i <= totalChunks; i++) {
	            String chunkFileName = dockerExtensionPath+"/upload/chunk/" + uuid + "_" + i + ".tmp";
	            new File(chunkFileName).delete();
	        }
	    break;
	    }

    }
    
    
    @Override
	public String zipWidthHeightImageFile(File oldFile,File newFile, int width, int height,float quality) {  
        if (oldFile == null) {  
            return null;  
        }  
        String newImage = null;  
        try {  
            /** 對服務器上的臨時文件進行處理 */  
            Image srcFile = ImageIO.read(oldFile);  
            
            String srcImgPath = newFile.getAbsoluteFile().toString();
            System.out.println(srcImgPath);
            String subfix = "jpg";
    		subfix = srcImgPath.substring(srcImgPath.lastIndexOf(".")+1,srcImgPath.length());

    		BufferedImage buffImg = null; 
    		if(subfix.equals("png")){
    			buffImg = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
    		}else{
    			buffImg = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
    		}

    		Graphics2D graphics = buffImg.createGraphics();
    		graphics.setBackground(new Color(255,255,255));
    		graphics.setColor(new Color(255,255,255));
    		graphics.fillRect(0, 0, width, height);
    		graphics.drawImage(srcFile.getScaledInstance(width, height, Image.SCALE_SMOOTH), 0, 0, null);  

    		ImageIO.write(buffImg, subfix, new File(srcImgPath));  
        } catch (FileNotFoundException e) {  
            e.printStackTrace();  
        } catch (IOException e) {  
            e.printStackTrace();  
        }  
        return newImage;  
    }  

    public String zipImageFile(File oldFile,File newFile, int width, int height,float quality) {  
        if (oldFile == null) {  
            return null;  
        }  
        try {  
            /** 對服務器上的臨時文件進行處理 */  
            Image srcFile = ImageIO.read(oldFile);  
            int w = srcFile.getWidth(null);  
            int h = srcFile.getHeight(null);  
            double bili;  
            if(width>0){  
                bili=width/(double)w;  
                height = (int) (h*bili);  
            }else{  
                if(height>0){  
                    bili=height/(double)h;  
                    width = (int) (w*bili);  
                }  
            }  
            
            String srcImgPath = newFile.getAbsoluteFile().toString();
            System.out.println(srcImgPath);
            String subfix = "jpg";
    		subfix = srcImgPath.substring(srcImgPath.lastIndexOf(".")+1,srcImgPath.length());

    		BufferedImage buffImg = null; 
    		if(subfix.equals("png")){
    			buffImg = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
    			System.out.println("subfix=" + subfix);
    		}else{
    			buffImg = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
    		}

    		Graphics2D graphics = buffImg.createGraphics();
    		graphics.drawImage(srcFile.getScaledInstance(width, height, Image.SCALE_SMOOTH), 0, 0, null);  

    		ImageIO.write(buffImg, subfix, new File(srcImgPath));  
  
        } catch (FileNotFoundException e) {  
            e.printStackTrace();  
        } catch (IOException e) {  
            e.printStackTrace();  
        }  
        return newFile.getAbsolutePath();  
    }  
  
    @Override
    public void writeToFile(InputStream uploadedInputStream, String uploadedFileLocation) {
        try {
            OutputStream out = new FileOutputStream(new File(uploadedFileLocation));
            int read;
            byte[] bytes = new byte[1024];

            while ((read = uploadedInputStream.read(bytes)) != -1) {
                out.write(bytes, 0, read);
            }
            
            out.flush();
            out.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public String getFileExtension(String fileName) {
        int dotIndex = fileName.lastIndexOf(".");
        if (dotIndex > 0) {
            return fileName.substring(dotIndex);
        }
        return "";
    }

    @Override
    public String generateRandomFileName(String extension) {
        UUID uuid = UUID.randomUUID();
        return uuid.toString() + extension;
    }

    @Override
    public void cleanTtlFile() {
    	Date currentTime = new Date();
    	
        try {
            // 查询所有FileRepo记录
            List<FileRepoImpl> fileRepos =  entityManagerSession.onResult(entityManager -> FileRepoDAO.getAllFileRepos(entityManager));
            for (FileRepo fileRepo : fileRepos) {
            	String s_ttl = fileRepo.getTtl();
            	if (s_ttl != "" && !s_ttl.isEmpty()) {
            	    try {
            	        Date ttl = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").parse(s_ttl);
            	        if (ttl != null && ttl.before(currentTime)) {
            	        	
            	             // 创建一个 File 对象
            	             File fileToDelete = new File(fileRepo.getImagePath());

            	             // 检查文件是否存在
            	             if (fileToDelete.exists()) {
            	                 // 尝试删除文件
            	                 if (fileToDelete.delete()) {
            	                	 LOGGER.info("FileRepo with ID {} has expired and has been deleted Image.", fileRepo.getId());
            	                 }
            	             }
            	             
            	             if ( fileRepo.getThumbnail() != null ) {
            	            	 // 创建一个 File 对象
                	             File fileToDelete_thum = new File(fileRepo.getThumbnail());
                	             // 检查文件是否存在
                	             if (fileToDelete_thum.exists()) {
                	                 // 尝试删除文件
                	                 if (fileToDelete_thum.delete()) {
                	                	 LOGGER.info("FileRepo with ID {} has expired and has been deleted Thumbnail.", fileRepo.getId());
                	                	
                	                 }
                	             }
            	             }
            	            

            	            entityManagerSession.onTransactedAction(entityManager -> FileRepoDAO.delete(entityManager, fileRepo.getScopeId(), fileRepo.getId()));
            	            LOGGER.info("FileRepo with ID {} has expired and has been deleted.", fileRepo.getId());
            	        }
            	    } catch (ParseException e) {
            	        LOGGER.error("Error parsing TTL date: {}", e.getMessage());
            	    }
            	} else {
            	    LOGGER.warn("TTL date is null or empty for FileRepo with ID {}", fileRepo.getId());
            	}
            }
        } catch (KapuaException e) {
            LOGGER.error("Error while cleaning up expired FileRepo records: {}", e.getMessage());
        }
    }
}
