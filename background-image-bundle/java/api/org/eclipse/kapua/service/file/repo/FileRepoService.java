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
package org.eclipse.kapua.service.file.repo;


import java.io.File;
import java.io.IOException;
import java.io.InputStream;

import org.eclipse.kapua.KapuaException;

import org.eclipse.kapua.model.id.KapuaId;
import org.eclipse.kapua.model.query.KapuaQuery;
import org.eclipse.kapua.service.KapuaEntityService;
import org.eclipse.kapua.service.KapuaNamedEntityService;
import org.eclipse.kapua.service.KapuaUpdatableEntityService;
import org.eclipse.kapua.service.config.KapuaConfigurableService;

/**
 * AccountService exposes APIs to manage Account objects.<br>
 * It includes APIs to create, update, find, list and delete Accounts.<br>
 * Instances of the AccountService can be acquired through the ServiceLocator object.
 *
 * @since 1.0
 */
public interface FileRepoService extends KapuaEntityService<FileRepo, FileRepoCreator>,
        KapuaUpdatableEntityService<FileRepo>,
        KapuaNamedEntityService<FileRepo>,
        KapuaConfigurableService {

    /**
     * Finds the fileRepo by fileRepo identifiers
     *
     * @param id
     * @return
     * @throws KapuaException
     */
    FileRepo find(KapuaId id) throws KapuaException;

    /**
     * Returns the {@link FileRepoListResult} with elements matching the provided query.
     *
     * @param query The {@link FileRepoQuery} used to filter results.
     * @return The {@link FileRepoListResult} with elements matching the query parameter.
     * @throws KapuaException
     * @since 1.0.0
     */
    @Override
    FileRepoListResult query(KapuaQuery<FileRepo> query) throws KapuaException;
    
    /**
     * Write to file with upload stream 
     *
     * @param uploadedInputStream The {@link InputStream} used to store file.
     * @param uploadedFileLocation The {@link String} used to store file location.
     * @return
     * @throws KapuaException
     * @since 1.0.0
     */
    void writeToFile(InputStream uploadedInputStream, String uploadedFileLocation) throws KapuaException;
    
    /**
     * Get file extension
     *
     * @param fileName The {@link String} used to get results.
     * @return {@link String}
     * @throws KapuaException
     * @since 1.0.0
     */
    String getFileExtension(String fileName) throws KapuaException;
    
    /**
     * Generate Random FileName
     *
     * @param extension The {@link String} used to get results.
     * @return {@link String}
     * @throws KapuaException
     * @since 1.0.0
     */
    String generateRandomFileName(String extension) throws KapuaException;
    
    /** 
     * 根據設置的寬高等比例壓縮圖片文件 先保存原文件，再壓縮、上傳 
     * @param oldFile  要進行壓縮的文件 
     * @param newFile  新文件 
     * @param width  寬度 //設置寬度時（高度傳入0，等比例縮放） 
     * @param height 高度 //設置高度時（寬度傳入0，等比例縮放） 
     * @param quality 品質
     * @return 返回壓縮後的文件的全路徑 
     */  
    String zipImageFile(File oldFile,File newFile, int width, int height,float quality) throws KapuaException;
    
    /** 
     * 按設置的寬度高度壓縮圖片文件 先保存原文件，再壓縮、上傳 
     * @param oldFile  要進行壓縮的文件全路徑 
     * @param newFile  新文件 
     * @param width  寬度 
     * @param height 高度 
     * @param quality 品質 
     * @return 返回壓縮後的文件的全路徑 
     */  
    String zipWidthHeightImageFile(File oldFile,File newFile, int width, int height,float quality) throws KapuaException;

    
    void cleanTtlFile();

	FileRepo create_account(FileRepoCreator fileRepoCreator) throws KapuaException;

	void delete_account(KapuaId scopeId, KapuaId fileRepoId) throws KapuaException;
	
	void saveChunk(InputStream inputStream, int chunkNumber, String uuid) throws IOException;
	
	boolean isUploadComplete(int totalChunks, String uuid);
	
	void mergeChunks(int totalChunks, String finalFileName, String uuid) throws IOException;

}
