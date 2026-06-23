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
package org.eclipse.kapua.service.file.repo.internal;

import org.eclipse.kapua.locator.KapuaProvider;
import org.eclipse.kapua.model.id.KapuaId;
import org.eclipse.kapua.service.file.repo.FileRepo;
import org.eclipse.kapua.service.file.repo.FileRepoCreator;
import org.eclipse.kapua.service.file.repo.FileRepoFactory;
import org.eclipse.kapua.service.file.repo.FileRepoListResult;
import org.eclipse.kapua.service.file.repo.FileRepoQuery;

/**
 * Device service factory implementation.
 *
 * @since 1.0
 */
@KapuaProvider
public class FileRepoFactoryImpl implements FileRepoFactory {

    @Override
    public FileRepoCreator newCreator(KapuaId scopeId) {
        FileRepoCreator file_repo_creator = newCreator(scopeId);
        return file_repo_creator;
    }

    @Override
    public FileRepoQuery newQuery(KapuaId scopeId) {
        return new FileRepoQueryImpl(scopeId);
    }

    @Override
    public FileRepoListResult newListResult() {
        return new FileRepoListResultImpl();
    }

    @Override
    public FileRepo newEntity(KapuaId scopeId) {
        return new FileRepoImpl(scopeId);
    }


}
