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

import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashSet;
import java.util.Set;

import javax.persistence.AttributeOverride;
import javax.persistence.AttributeOverrides;
import javax.persistence.Basic;
import javax.persistence.CollectionTable;
import javax.persistence.Column;
import javax.persistence.ElementCollection;
import javax.persistence.Embedded;
import javax.persistence.Entity;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;
import javax.persistence.FetchType;
import javax.persistence.JoinColumn;
import javax.persistence.OneToOne;
import javax.persistence.Table;

import org.eclipse.kapua.KapuaException;
import org.eclipse.kapua.commons.model.AbstractKapuaNamedEntity;
import org.eclipse.kapua.commons.model.id.KapuaEid;
import org.eclipse.kapua.model.id.KapuaId;
import org.eclipse.kapua.service.file.repo.*;

import org.apache.commons.lang3.builder.ToStringBuilder;

/**
 * {@link FileRepo} implementation.
 *
 * @since 1.0.0
 */
@Entity(name = "FileRepo")
@Table(name = "file_repo")
public class FileRepoImpl extends AbstractKapuaNamedEntity implements FileRepo {
	
    /**
	 * 
	 */
	private static final long serialVersionUID = -1846604112214010382L;

	@Basic
    @Column(name = "image_path")
    private String image_path;
    
    @Basic
    @Column(name = "dir")
    private String dir;
    
    @Basic
    @Column(name = "thumbnail")
    private String thumbnail;
    
    @Basic
    @Column(name = "thumbnail_ratio")
    private String thumbnail_ratio;
    
    @Basic
    @Column(name = "ttl",nullable = true, updatable = true)
    private Date ttl;
    

	public String getType() {
        return "fileRepo"; 
    }
	
	/**
     * Constructor
     */
    protected FileRepoImpl() {
        super();
    }
    
    /**
     * Constructor
     *
     * @param scopeId
     */
    public FileRepoImpl(KapuaId scopeId) {
        super(scopeId);
    }

	@Override
	public String getImagePath() {
		return image_path;
	}

	@Override
	public void setImagePath(String image_path) {
		this.image_path = image_path;
	}

	@Override
	public String getDir() {
		return dir;
	}

	@Override
	public void setDir(String dir) {
		this.dir = dir;
	}

	@Override
	public String getThumbnail() {
		return thumbnail;
	}

	@Override
	public void setThumbnail(String thumbnail) {
		this.thumbnail = thumbnail;
	}

	@Override
	public String getThumbnailRatio() {
		return thumbnail_ratio;
	}

	@Override
	public void setThumbnailRatio(String thumbnail_ratio) {
		this.thumbnail_ratio = thumbnail_ratio;
	}
	
	@Override
	public void setTtl(String ttl) {
		if ( ttl.isEmpty() ) {
			this.ttl = null;
		} else {
			try {
	            this.ttl = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").parse(ttl);
	        } catch (ParseException e) {
	            System.err.println("日期解析错误: " + e.getMessage());
	            this.ttl = null;
	        }
		}
	}
	
	@Override
	public String getTtl() {
		if ( this.ttl == null ) {
			return "";
		}
		DateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
		return dateFormat.format(this.ttl);
	}
}
