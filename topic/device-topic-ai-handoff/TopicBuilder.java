import java.util.Objects;

/**
 * Reference implementation for the new device project.
 *
 * Move this class into the project's own package before use.
 */
public final class TopicBuilder {

    private static final String CONTROL_PREFIX = "$EDC";

    private TopicBuilder() {
    }

    public static String deviceControlSubscription(String accountName, String clientId) {
        return join(CONTROL_PREFIX, required("accountName", accountName), required("clientId", clientId), "#");
    }

    public static String applicationSubscription(String accountName, String clientId, String applicationId) {
        return join(
                CONTROL_PREFIX,
                required("accountName", accountName),
                required("clientId", clientId),
                required("applicationId", applicationId),
                "#");
    }

    public static String birthTopic(String accountName, String clientId) {
        return join(CONTROL_PREFIX, required("accountName", accountName), required("clientId", clientId), "MQTT", "BIRTH");
    }

    public static String disconnectTopic(String accountName, String clientId) {
        return join(CONTROL_PREFIX, required("accountName", accountName), required("clientId", clientId), "MQTT", "DC");
    }

    public static String replyTopic(
            String accountName,
            String requesterClientId,
            String applicationId,
            String requestId) {
        return join(
                CONTROL_PREFIX,
                required("accountName", accountName),
                required("requesterClientId", requesterClientId),
                required("applicationId", applicationId),
                "REPLY",
                required("requestId", requestId));
    }

    public static String dataTopic(String accountName, String clientId, String localTopic) {
        String checkedLocalTopic = Objects.requireNonNull(localTopic, "localTopic").trim();
        if (checkedLocalTopic.isEmpty() || checkedLocalTopic.startsWith("/") || checkedLocalTopic.endsWith("/")) {
            throw new IllegalArgumentException("localTopic must be a non-empty relative MQTT topic");
        }
        if (checkedLocalTopic.contains("+") || checkedLocalTopic.contains("#")) {
            throw new IllegalArgumentException("Published data topics must not contain MQTT wildcards");
        }
        return join(required("accountName", accountName), required("clientId", clientId), checkedLocalTopic);
    }

    private static String required(String name, String value) {
        String checked = Objects.requireNonNull(value, name).trim();
        if (checked.isEmpty()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        if (checked.contains("/")) {
            throw new IllegalArgumentException(name + " must be one MQTT topic segment");
        }
        return checked;
    }

    private static String join(String... parts) {
        return String.join("/", parts);
    }
}
