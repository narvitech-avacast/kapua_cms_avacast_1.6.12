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

import org.eclipse.kapua.commons.model.query.FieldSortCriteria;
import org.eclipse.kapua.commons.model.query.FieldSortCriteria.SortOrder;
import org.eclipse.kapua.commons.model.query.predicate.AbstractKapuaQuery;
import org.eclipse.kapua.model.id.KapuaId;
import org.eclipse.kapua.service.file.repo.FileRepo;
import org.eclipse.kapua.service.file.repo.FileRepoQuery;

/**
 * {@link DeviceQuery} implementation.
 *
 * @since 1.0.0
 */
public class FileRepoQueryImpl extends AbstractKapuaQuery<FileRepo> implements FileRepoQuery {

    /**
     * Constructor
     */
    private FileRepoQueryImpl() {
        super();
    }

    /**
     * Constructor
     *
     * @param scopeId
     */
    public FileRepoQueryImpl(KapuaId scopeId) {
        this();
        setScopeId(scopeId);
    }
}
