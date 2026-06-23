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

import org.eclipse.kapua.model.KapuaNamedEntityCreator;
import org.eclipse.kapua.model.KapuaUpdatableEntityCreator;
import org.eclipse.kapua.model.id.KapuaId;
import org.eclipse.kapua.model.id.KapuaIdAdapter;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlType;
import javax.xml.bind.annotation.adapters.XmlJavaTypeAdapter;

/**
 * {@link FileRepoCreator} encapsulates all the information needed to create a new {@link Device} in the system.<br>
 * The data provided will be used to seed the new {@link Device} and its related information.<br>
 * The fields of the {@link FileRepoCreator} presents the attributes that are searchable for a given device.<br>
 * The DeviceCreator Properties field can be used to provide additional properties associated to the Device;
 * those properties will not be searchable through Device queries.<br>
 * The clientId field of the Device is used to store the MAC address of the primary network interface of the device.
 *
 * @since 1.0.0
 */
@XmlRootElement(name = "fileRepo")
@XmlAccessorType(XmlAccessType.PROPERTY)
@XmlType(propOrder = {
        "imagePath",
        "dir",
        "thumbnail",
        "thumbnailRatio",
        "ttl"
        
}, factoryClass = FileRepoXmlRegistry.class, factoryMethod = "newFileRepoCreator")
public interface FileRepoCreator extends KapuaNamedEntityCreator<FileRepo> {

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
