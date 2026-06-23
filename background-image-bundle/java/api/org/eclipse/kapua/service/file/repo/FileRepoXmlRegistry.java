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

import org.eclipse.kapua.locator.KapuaLocator;

import javax.xml.bind.annotation.XmlRegistry;

/**
 * {@link Device} xml factory class
 *
 * @since 1.0
 */
@XmlRegistry
public class FileRepoXmlRegistry {

    private static final KapuaLocator LOCATOR = KapuaLocator.getInstance();
    private static final FileRepoFactory FILEREPO_FACTORY = LOCATOR.getFactory(FileRepoFactory.class);

    /**
     * Creates a new {@link Device}
     *
     * @return
     */
    public FileRepo newFileRepo() {
        return FILEREPO_FACTORY.newEntity(null);
    }

    /**
     * Creates a new file repo creator
     *
     * @return
     */
    public FileRepoCreator newFileRepoCreator() {
        return FILEREPO_FACTORY.newCreator(null);
        
    }

    /**
     * Creates a new file repo list result
     *
     * @return
     */
    public FileRepoListResult newFileRepoListResult() {
        return FILEREPO_FACTORY.newListResult();
    }

    public FileRepoQuery newQuery() {
        return FILEREPO_FACTORY.newQuery(null);
    }
}
