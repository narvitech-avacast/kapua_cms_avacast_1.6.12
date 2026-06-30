<%@ page language="java" contentType="application/json;charset=UTF-8" trimDirectiveWhitespaces="true" %>
<%@ page import="java.util.List" %>
<%@ page import="java.util.Map" %>
<%@ page import="org.eclipse.kapua.app.console.module.device.server.GwtDeviceServiceImpl" %>
<%@ page import="org.eclipse.kapua.app.console.module.device.server.GwtDeviceManagementServiceImpl" %>
<%@ page import="org.eclipse.kapua.app.console.module.api.shared.model.GwtConfigComponent" %>
<%@ page import="org.eclipse.kapua.app.console.module.api.shared.model.GwtConfigParameter" %>
<%
    response.setHeader("Cache-Control", "no-cache, no-store");
    response.setHeader("X-Content-Type-Options", "nosniff");

    // Require an active Kapua console session
    javax.servlet.http.HttpSession sess = request.getSession(false);
    if (sess == null || sess.getAttribute("console.current.session") == null) {
        response.sendError(401);
        return;
    }

    final String NET_COMP = "com.PeerInternet.Network.Config";
    final String P_SSID   = "WiFiConnectedSSID";
    final String P_SIG    = "WiFiSignal";
    final String P_AP     = "HotSpotStatus";

    Map<String, Map<String, String>> snapshot = GwtDeviceServiceImpl._deviceSnapshot;
    Map<String, String> ipMap = GwtDeviceServiceImpl.getRealIpSnapshot();

    StringBuilder sb = new StringBuilder("[");
    boolean first = true;
    for (Map.Entry<String, Map<String, String>> e : snapshot.entrySet()) {
        if (!first) sb.append(',');
        first = false;

        String clientId = e.getKey();
        Map<String, String> info = e.getValue();

        // Use real IP from debug-proxy if available, else fall back to snapshot IP
        String ip = ipMap.containsKey(clientId) ? ipMap.get(clientId) : info.get("ip");

        String ssid = "", signal = "", ap = "";
        List<GwtConfigComponent> cfg = GwtDeviceManagementServiceImpl.getCachedConfig(info.get("id"));
        if (cfg != null) {
            for (GwtConfigComponent comp : cfg) {
                if (NET_COMP.equals(comp.getComponentId())) {
                    ssid   = paramVal(comp, P_SSID);
                    signal = paramVal(comp, P_SIG);
                    ap     = paramVal(comp, P_AP);
                    break;
                }
            }
        }

        sb.append("{\"dn\":\"").append(esc(info.get("dn"))).append('"')
          .append(",\"st\":\"").append(esc(info.get("st"))).append('"')
          .append(",\"ip\":\"").append(esc(ip)).append('"')
          .append(",\"ssid\":\"").append(esc(ssid)).append('"')
          .append(",\"sig\":\"").append(esc(signal)).append('"')
          .append(",\"ap\":\"").append(esc(ap)).append('"')
          .append('}');
    }
    sb.append(']');
    out.write(sb.toString());
%>
<%!
private String paramVal(GwtConfigComponent comp, String id) {
    GwtConfigParameter p = comp.getParameter(id);
    if (p == null) return "";
    if (p.getValue() != null && !p.getValue().isEmpty()) return p.getValue();
    String[] vals = p.getValues();
    return (vals != null && vals.length > 0 && vals[0] != null) ? vals[0] : "";
}

private String esc(String s) {
    if (s == null) return "";
    return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\r", "").replace("\n", "\\n");
}
%>
