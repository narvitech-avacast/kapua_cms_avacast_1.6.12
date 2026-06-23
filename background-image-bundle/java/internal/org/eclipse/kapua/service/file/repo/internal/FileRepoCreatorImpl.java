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
import java.util.Properties;

import org.eclipse.kapua.KapuaException;
import org.eclipse.kapua.commons.model.AbstractKapuaNamedEntityCreator;
import org.eclipse.kapua.commons.model.AbstractKapuaUpdatableEntityCreator;
import org.eclipse.kapua.model.id.KapuaId;
import org.eclipse.kapua.service.file.repo.FileRepo;
import org.eclipse.kapua.service.file.repo.FileRepoCreator;

/**
 * {@link FileRepoCreator} implementation.
 *
 * @since 1.0.0
 */
public class FileRepoCreatorImpl extends AbstractKapuaNamedEntityCreator<FileRepo> implements FileRepoCreator {


    /**
	 * 
	 */
	private static final long serialVersionUID = -2710686032374802464L;
	private String image_path;
    private String dir;
    private String thumbnail;
    private String thumbnail_ratio;
    private Date ttl;
    

    /**
     * Constructor.
     *
     * @param scopeId
     * @since 1.0.0
     */
    public FileRepoCreatorImpl(KapuaId scopeId) {
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
		try {
			this.ttl = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").parse(ttl);
		} catch (ParseException e) {
		    e.printStackTrace(); 
		}
	}
	
	@Override
	public String getTtl() {
		if (this.ttl == null) {
			return "";
		}
		DateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
		return dateFormat.format(this.ttl);
	}
}
