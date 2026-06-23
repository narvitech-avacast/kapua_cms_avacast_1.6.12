/*******************************************************************************
 * Copyright (c) 2011, 2016 Eurotech and/or its affiliates and others
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

import org.eclipse.kapua.model.KapuaEntityFactory;
import org.eclipse.kapua.model.id.KapuaId;
import org.eclipse.kapua.service.file.repo.FileRepoCreator;

/**
 * Device service factory definition.
 *
 * @since 1.0
 */
public interface FileRepoFactory extends KapuaEntityFactory<FileRepo, FileRepoCreator, FileRepoQuery, FileRepoListResult> {

	 /**
     * Creates a new device creator
     *
     * @param scopeId
     * @return
     */
    FileRepoCreator newCreator(KapuaId scopeId);

}
