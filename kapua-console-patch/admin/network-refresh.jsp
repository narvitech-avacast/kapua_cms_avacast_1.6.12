<%@ page language="java" contentType="application/json;charset=UTF-8" trimDirectiveWhitespaces="true" %>
<%@ page import="org.eclipse.kapua.commons.security.KapuaSecurityUtils" %>
<%@ page import="org.eclipse.kapua.app.console.module.device.server.GwtDeviceManagementServiceImpl" %>
<%
    response.setHeader("Cache-Control", "no-cache, no-store");
    response.setHeader("X-Content-Type-Options", "nosniff");

    if (KapuaSecurityUtils.getSession() == null) {
        response.sendError(401);
        return;
    }

    GwtDeviceManagementServiceImpl.evictAllNetworkConfigCache();
    out.write("{\"cleared\":true}");
%>
