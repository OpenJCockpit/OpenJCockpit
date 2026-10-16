package nl.metafactory.agents.a2a.services;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/** A2A services added in the dashboard (table {@code a2a_services}, V6); secrets arrive here encrypted. */
@Component
public class A2AServiceStore {

    /** One stored service; {@code toString} never renders the encrypted secrets. */
    public record StoredService(String id, String type, String name, String baseUrl, String publicUrl,
                                String apiKeyEncrypted, String callerTokenUrl, String callerClientId,
                                String callerClientSecretEncrypted, String createdBy, Instant createdAt,
                                String updatedBy, Instant updatedAt) {

        @Override
        public String toString() {
            return "StoredService{id=" + id + ", name=" + name + ", baseUrl=" + baseUrl + "}";
        }
    }

    private static final String COLUMNS = """
            id, type, name, base_url, public_url, api_key_encrypted, caller_token_url, caller_client_id,
            caller_client_secret_encrypted, created_by, created_at, updated_by, updated_at""";

    private final JdbcClient jdbc;

    public A2AServiceStore(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public List<StoredService> list() {
        return jdbc.sql("SELECT " + COLUMNS + " FROM a2a_services ORDER BY lower(name), id").query(A2AServiceStore::row).list();
    }

    public Optional<StoredService> find(String id) {
        return jdbc.sql("SELECT " + COLUMNS + " FROM a2a_services WHERE id = :id").param("id", id)
                .query(A2AServiceStore::row).optional();
    }

    public void insert(StoredService s) {
        jdbc.sql("INSERT INTO a2a_services (" + COLUMNS + """
                        ) VALUES (:id, :type, :name, :baseUrl, :publicUrl, :apiKey, :tokenUrl, :clientId, :clientSecret,
                                  :createdBy, :createdAt, :updatedBy, :updatedAt)""")
                .params(params(s)).update();
    }

    public void update(StoredService s) {
        jdbc.sql("""
                        UPDATE a2a_services SET name = :name, base_url = :baseUrl, public_url = :publicUrl,
                               api_key_encrypted = :apiKey, caller_token_url = :tokenUrl, caller_client_id = :clientId,
                               caller_client_secret_encrypted = :clientSecret, updated_by = :updatedBy, updated_at = :updatedAt
                        WHERE id = :id""")
                .params(params(s)).update();
    }

    public boolean delete(String id) {
        return jdbc.sql("DELETE FROM a2a_services WHERE id = :id").param("id", id).update() > 0;
    }

    /** Workflows that link at least one agent of the service ({@code workflow_agent_refs}). */
    public List<String> workflowsUsing(String id) {
        return jdbc.sql("""
                        SELECT DISTINCT workflow_id FROM workflow_agent_refs
                        WHERE source = 'A2A' AND provider = :id ORDER BY workflow_id""")
                .param("id", id).query(String.class).list();
    }

    private static java.util.Map<String, Object> params(StoredService s) {
        var p = new java.util.HashMap<String, Object>();
        p.put("id", s.id());
        p.put("type", s.type());
        p.put("name", s.name());
        p.put("baseUrl", s.baseUrl());
        p.put("publicUrl", s.publicUrl());
        p.put("apiKey", s.apiKeyEncrypted());
        p.put("tokenUrl", s.callerTokenUrl());
        p.put("clientId", s.callerClientId());
        p.put("clientSecret", s.callerClientSecretEncrypted());
        p.put("createdBy", s.createdBy());
        p.put("createdAt", s.createdAt() == null ? null : Timestamp.from(s.createdAt()));
        p.put("updatedBy", s.updatedBy());
        p.put("updatedAt", Timestamp.from(s.updatedAt()));
        return p;
    }

    private static StoredService row(ResultSet rs, int n) throws SQLException {
        return new StoredService(rs.getString(1), rs.getString(2), rs.getString(3), rs.getString(4), rs.getString(5),
                rs.getString(6), rs.getString(7), rs.getString(8), rs.getString(9), rs.getString(10),
                rs.getTimestamp(11).toInstant(), rs.getString(12), rs.getTimestamp(13).toInstant());
    }
}
