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

import io.swagger.annotations.ApiModelProperty;

import org.eclipse.kapua.model.KapuaNamedEntity;
import org.eclipse.kapua.model.id.KapuaId;
import org.eclipse.kapua.model.id.KapuaIdAdapter;


import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlElementWrapper;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlType;
import javax.xml.bind.annotation.adapters.XmlJavaTypeAdapter;
import java.util.Set;


@XmlRootElement(name = "fileRepo")
@XmlAccessorType(XmlAccessType.PROPERTY)
@XmlType(propOrder = {
        "imagePath",
        "dir",
        "thumbnail",
        "thumbnailRatio",
        "ttl"
        
}, factoryClass = FileRepoXmlRegistry.class, factoryMethod = "newFileRepo")
public interface FileRepo extends KapuaNamedEntity {

    String TYPE = "fileRepo";

    @Override
    default String getType() {
        return TYPE;
    }

    /**
     * Get the image path
     *
     * @return
     */
    @XmlElement(name = "imagePath")
    String getImagePath();

    /**
     * Set the image path
     *
     * @param image path
     */
    void setImagePath(String image_path);
    
    /**
     * Get the dir
     *
     * @return
     */
    @XmlElement(name = "dir")
    String getDir();

    /**
     * Set the dir
     *
     * @param dir
     */
    void setDir(String dir);
    
    /**
     * Get the thumbnail
     *
     * @return
     */
    @XmlElement(name = "thumbnail")
    String getThumbnail ();

    /**
     * Set the thumbnail
     *
     * @param thumbnail
     */
    void setThumbnail(String thumbnail);
    
    /**
     * Get the thumbnail ratio
     *
     * @return
     */
    @XmlElement(name = "thumbnailRatio")
    String getThumbnailRatio();

    /**
     * Set the thumbnail ratio
     *
     * @param thumbnail ratio
     */
    void setThumbnailRatio(String thumbnail_ratio);
    
    /**
     * Get the ttl
     *
     * @return
     */
    @XmlElement(name = "ttl")
    String getTtl();

    /**
     * Set the ttl
     *
     * @param ttl
     */
    void setTtl(String ttl);

}
