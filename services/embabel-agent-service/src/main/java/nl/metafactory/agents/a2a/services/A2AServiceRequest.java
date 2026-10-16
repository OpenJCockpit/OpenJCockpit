package nl.metafactory.agents.a2a.services;

/**
 * Create or update an A2A service. A blank {@code apiKey} or {@code callerClientSecret} on update keeps the
 * stored value; blank {@code callerTokenUrl} and {@code callerClientId} remove the caller identity.
 * {@code toString} never renders the secrets.
 */
public record A2AServiceRequest(String id, String name, String baseUrl, String publicUrl, String apiKey,
                                String callerTokenUrl, String callerClientId, String callerClientSecret) {

    @Override
    public String toString() {
        return "A2AServiceRequest{id=" + id + ", name=" + name + ", baseUrl=" + baseUrl + ", apiKey="
                + (apiKey == null ? "null" : "***") + ", callerClientSecret=" + (callerClientSecret == null ? "null" : "***") + "}";
    }
}
