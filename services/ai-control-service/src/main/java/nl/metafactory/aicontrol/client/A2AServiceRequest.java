package nl.metafactory.aicontrol.client;

/** Create or change an A2A service; {@code toString} never renders the secrets. */
public record A2AServiceRequest(
        String id,
        String name,
        String baseUrl,
        String publicUrl,
        String apiKey,
        String callerTokenUrl,
        String callerClientId,
        String callerClientSecret
) {

    @Override
    public String toString() {
        return "A2AServiceRequest{id=" + id + ", name=" + name + ", baseUrl=" + baseUrl + ", apiKey="
                + (apiKey == null ? "null" : "***") + ", callerClientSecret="
                + (callerClientSecret == null ? "null" : "***") + "}";
    }
}
