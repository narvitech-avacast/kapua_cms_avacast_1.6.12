/*******************************************************************************
 * Copyright (c) 2017, 2022 Eurotech and/or its affiliates and others.
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *     Eurotech - initial API and implementation
 *******************************************************************************/
package org.eclipse.kapua.app.api.web.sso;

import org.eclipse.kapua.plugin.sso.openid.OpenIDLocator;
import org.eclipse.kapua.plugin.sso.openid.OpenIDService;
import org.eclipse.kapua.plugin.sso.openid.provider.ProviderOpenIDLocator;

import javax.json.JsonObject;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

public class RestApiSsoServlet extends HttpServlet {

    private static final long serialVersionUID = 7829455949133999476L;

    private static final String LOGIN_PATH = "/login";
    private static final String CALLBACK_PATH = "/callback";
    private static final String RETURN_TO_PARAM = "return_to";
    private static final String CODE_PARAM = "code";
    private static final String STATE_PARAM = "state";
    private static final String ERROR_PARAM = "error";
    private static final String ERROR_DESCRIPTION_PARAM = "error_description";
    private static final String ACCESS_TOKEN_PARAM = "access_token";
    private static final String ID_TOKEN_PARAM = "id_token";
    private static final String DEFAULT_PAGE = "/device-test.html";

    private OpenIDLocator locator;

    @Override
    public void init() throws ServletException {
        super.init();
        locator = new ProviderOpenIDLocator();
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        String pathInfo = req.getPathInfo();

        if (LOGIN_PATH.equals(pathInfo)) {
            doLogin(req, resp);
        } else if (CALLBACK_PATH.equals(pathInfo)) {
            doCallback(req, resp);
        } else {
            resp.sendError(HttpServletResponse.SC_NOT_FOUND);
        }
    }

    private void doLogin(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        try {
            OpenIDService openIdService = locator.getService();
            if (!openIdService.isEnabled()) {
                redirectWithError(req, resp, defaultReturnTo(req), "sso_disabled", "OpenID Connect SSO is not enabled.");
                return;
            }

            String returnTo = sanitizeReturnTo(req, req.getParameter(RETURN_TO_PARAM));
            String state = Base64.getUrlEncoder()
                    .withoutPadding()
                    .encodeToString(returnTo.getBytes(StandardCharsets.UTF_8));

            String loginUri = openIdService.getLoginUri(state, callbackUri(req));
            resp.sendRedirect(loginUri);
        } catch (Exception e) {
            redirectWithError(req, resp, defaultReturnTo(req), "sso_login_failed", e.getMessage());
        }
    }

    private void doCallback(HttpServletRequest req, HttpServletResponse resp) throws IOException, ServletException {
        String returnTo = returnToFromState(req);
        String error = req.getParameter(ERROR_PARAM);

        if (!isBlank(error)) {
            redirectWithError(req, resp, returnTo, error, req.getParameter(ERROR_DESCRIPTION_PARAM));
            return;
        }

        String code = req.getParameter(CODE_PARAM);
        if (isBlank(code)) {
            resp.sendError(HttpServletResponse.SC_BAD_REQUEST);
            return;
        }

        try {
            JsonObject tokens = locator.getService().getTokens(code, callbackUri(req));
            String redirect = appendQuery(
                    returnTo,
                    ACCESS_TOKEN_PARAM,
                    tokens.getString(ACCESS_TOKEN_PARAM),
                    ID_TOKEN_PARAM,
                    tokens.getString(ID_TOKEN_PARAM));
            resp.sendRedirect(resp.encodeRedirectURL(redirect));
        } catch (Exception e) {
            throw new ServletException("Failed to complete OpenID Connect login: " + e.getMessage(), e);
        }
    }

    private URI callbackUri(HttpServletRequest req) {
        StringBuilder uri = new StringBuilder();
        uri.append(req.getScheme()).append("://").append(req.getServerName());

        int port = req.getServerPort();
        if (port > 0 && !isDefaultPort(req.getScheme(), port)) {
            uri.append(':').append(port);
        }

        uri.append(req.getContextPath()).append(req.getServletPath()).append(CALLBACK_PATH);
        return URI.create(uri.toString());
    }

    private boolean isDefaultPort(String scheme, int port) {
        return ("http".equalsIgnoreCase(scheme) && port == 80) ||
                ("https".equalsIgnoreCase(scheme) && port == 443);
    }

    private String returnToFromState(HttpServletRequest req) {
        String state = req.getParameter(STATE_PARAM);
        if (isBlank(state)) {
            return defaultReturnTo(req);
        }

        try {
            String decoded = new String(Base64.getUrlDecoder().decode(state), StandardCharsets.UTF_8);
            return sanitizeReturnTo(req, decoded);
        } catch (IllegalArgumentException e) {
            return defaultReturnTo(req);
        }
    }

    private String sanitizeReturnTo(HttpServletRequest req, String returnTo) {
        if (isBlank(returnTo)) {
            return defaultReturnTo(req);
        }

        String contextPath = req.getContextPath();
        String normalized = returnTo.trim();

        if (normalized.startsWith("//") || normalized.contains("://")) {
            return defaultReturnTo(req);
        }

        if (!normalized.startsWith("/")) {
            normalized = contextPath + "/" + normalized;
        }

        if (!contextPath.isEmpty() && !normalized.startsWith(contextPath + "/")) {
            return defaultReturnTo(req);
        }

        return normalized;
    }

    private String defaultReturnTo(HttpServletRequest req) {
        return req.getContextPath() + DEFAULT_PAGE;
    }

    private void redirectWithError(
            HttpServletRequest req,
            HttpServletResponse resp,
            String returnTo,
            String error,
            String errorDescription) throws IOException {

        String redirect = appendQuery(
                sanitizeReturnTo(req, returnTo),
                ERROR_PARAM,
                error,
                ERROR_DESCRIPTION_PARAM,
                errorDescription);
        resp.sendRedirect(resp.encodeRedirectURL(redirect));
    }

    private String appendQuery(String url, String... nameValues) throws IOException {
        StringBuilder builder = new StringBuilder(url);
        builder.append(url.contains("?") ? '&' : '?');

        for (int i = 0; i < nameValues.length; i += 2) {
            if (i > 0) {
                builder.append('&');
            }

            builder
                    .append(urlEncode(nameValues[i]))
                    .append('=')
                    .append(urlEncode(nameValues[i + 1]));
        }

        return builder.toString();
    }

    private String urlEncode(String value) throws IOException {
        return URLEncoder.encode(value != null ? value : "", StandardCharsets.UTF_8.name());
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
