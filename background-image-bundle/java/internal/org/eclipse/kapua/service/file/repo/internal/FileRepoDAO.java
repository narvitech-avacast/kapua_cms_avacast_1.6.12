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
package org.eclipse.kapua.service.file.repo.internal;

import org.eclipse.kapua.KapuaEntityNotFoundException;
import org.eclipse.kapua.KapuaException;
import org.eclipse.kapua.commons.jpa.EntityManager;
import org.eclipse.kapua.commons.jpa.EntityManagerFactory;
import org.eclipse.kapua.commons.service.internal.ServiceDAO;
import org.eclipse.kapua.model.id.KapuaId;
import org.eclipse.kapua.model.query.KapuaQuery;
import org.eclipse.kapua.service.file.repo.FileRepo;
import org.eclipse.kapua.service.file.repo.FileRepoCreator;
import org.eclipse.kapua.service.file.repo.FileRepoListResult;

import java.util.Collections;
import java.util.List;

import javax.persistence.TypedQuery;
import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.CriteriaQuery;
import javax.persistence.criteria.Root;

/**
 * {@link FileRepo} DAO
 *
 * @since 1.0.0
 */
public class FileRepoDAO extends ServiceDAO {

    /**
     * Creates a new FileRepo
     *
     * @param em
     * @param fileRepoCreator
     * @return
     */
    public static FileRepo create(EntityManager em, FileRepoCreator file_repo_creator) {
        FileRepo file_repo = new FileRepoImpl(file_repo_creator.getScopeId());
        file_repo.setImagePath(file_repo_creator.getImagePath());
        file_repo.setDir(file_repo_creator.getDir());
        file_repo.setThumbnail(file_repo_creator.getThumbnail());
        file_repo.setThumbnailRatio(file_repo_creator.getThumbnailRatio());
        file_repo.setName(file_repo_creator.getName());
        if (file_repo_creator.getTtl() != "") {
        	file_repo.setTtl(file_repo_creator.getTtl());
        }
        
      
        return ServiceDAO.create(em, file_repo);
    }

    /**
     * Updates the provided fileRepo
     *
     * @param em
     * @param fileRepo
     * @return
     * @throws KapuaEntityNotFoundException If {@link FileRepo} is not found.
     */
    public static FileRepo update(EntityManager em, FileRepo file_repo) throws KapuaEntityNotFoundException {
        FileRepoImpl file_repo_impl = (FileRepoImpl) file_repo;
        return ServiceDAO.update(em, FileRepoImpl.class, file_repo_impl);
    }

    /**
     * Finds the fileRepo by device identifier
     *
     * @param em
     * @param scopeId
     * @param fileRepoId
     * @return
     */
    public static FileRepo find(EntityManager em, KapuaId scopeId, KapuaId fileRepoId) {
        return ServiceDAO.find(em, FileRepoImpl.class, scopeId, fileRepoId);
    }

    /**
     * Returns the fileRepo list matching the provided query
     *
     * @param em
     * @param query
     * @return
     * @throws KapuaException
     */
    public static FileRepoListResult query(EntityManager em, KapuaQuery<FileRepo> query)
            throws KapuaException {

        FileRepoListResult results = ServiceDAO.query(em, FileRepo.class, FileRepoImpl.class, new FileRepoListResultImpl(), query);

        return results;
    }

    /**
     * Returns the fileRepo count matching the provided query
     *
     * @param em
     * @param query
     * @return
     * @throws KapuaException
     */
    public static long count(EntityManager em, KapuaQuery<FileRepo> query)
            throws KapuaException {
        return ServiceDAO.count(em, FileRepo.class, FileRepoImpl.class, query);
    }

    /**
     * Deletes the file_repo by fileRepo identifier
     *
     * @param em
     * @param scopeId
     * @param fileRepoId
     * @throws KapuaEntityNotFoundException If {@link FileRepo} is not found.
     */
    public static void delete(EntityManager em, KapuaId scopeId, KapuaId fileRepoId) throws KapuaEntityNotFoundException {
        ServiceDAO.delete(em, FileRepoImpl.class, scopeId, fileRepoId);
    }

    /**
     * Finds the fileRepo by name
     *
     * @param em
     * @param name
     * @return
     */
    public static FileRepo findByName(EntityManager em, String name) {
        return ServiceDAO.findByField(em, FileRepoImpl.class, "name", name);
    }
    
    public static List<FileRepoImpl> getAllFileRepos(EntityManager em) {
    		
    	try {
    		
    		 CriteriaBuilder cb = em.getCriteriaBuilder();
    	     CriteriaQuery<FileRepoImpl> criteriaSelectQuery = cb.createQuery(FileRepoImpl.class);
    	     
	        //
	        // FROM
	        Root<FileRepoImpl> entityRoot = criteriaSelectQuery.from(FileRepoImpl.class);
	       
    	        //
    	        // SELECT
    	        criteriaSelectQuery.select(entityRoot);
    	        
    	        
            // 创建 TypedQuery
    	        TypedQuery<FileRepoImpl> query = em.createQuery(criteriaSelectQuery);

            // 执行查询并返回结果
            return query.getResultList();
        } catch (Exception e) {
            // 捕获并记录异常
            e.printStackTrace(); // 在控制台输出异常信息
            // 或者使用日志记录器记录异常信息
            // LOGGER.error("Error in getAllFileRepos: {}", e.getMessage());
            return Collections.emptyList(); // 返回一个空列表或其他默认值，具体取决于你的需求
        }
        
    }
}
