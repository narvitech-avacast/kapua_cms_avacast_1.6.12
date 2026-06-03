package org.eclipse.kapua.app.api.core.exception.mapper;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Response;
import javax.ws.rs.ext.ExceptionMapper;
import javax.ws.rs.ext.Provider;

/**
 * @since 2.0.0
 */
@Provider
public class WebApplicationExceptionMapper implements ExceptionMapper<WebApplicationException> {

    private static final Logger LOG = LoggerFactory.getLogger(WebApplicationExceptionMapper.class);

    @Inject
    public WebApplicationExceptionMapper() {
    }

    @Override
    public Response toResponse(WebApplicationException webApplicationException) {
        LOG.error(webApplicationException.getMessage(), webApplicationException);
        return Response
                .fromResponse(webApplicationException.getResponse())
                .build();
    }
}
