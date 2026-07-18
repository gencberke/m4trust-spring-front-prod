package com.m4trust.coreapi.document;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
class DocumentRepository {

    private final JdbcTemplate jdbcTemplate;

    DocumentRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    void insert(DocumentRecord document) {
        jdbcTemplate.update("""
                INSERT INTO document (
                    id, deal_id, file_name, media_type, document_status,
                    object_key, declared_size_bytes, declared_sha256,
                    upload_expires_at, verified_size_bytes, verified_sha256,
                    object_version, created_at, available_at, superseded_at,
                    updated_at, version
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, document.id(), document.dealId(), document.fileName(),
                document.mediaType(), document.status().name(),
                document.objectKey(), document.declaredSizeBytes(),
                document.declaredSha256(), Timestamp.from(document.uploadExpiresAt()),
                document.verifiedSizeBytes(), document.verifiedSha256(),
                document.objectVersion(), Timestamp.from(document.createdAt()),
                timestamp(document.availableAt()), timestamp(document.supersededAt()),
                Timestamp.from(document.updatedAt()), document.version());
    }

    Optional<DocumentRecord> findById(UUID documentId) {
        return jdbcTemplate.query("SELECT * FROM document WHERE id = ?",
                this::mapDocument, documentId).stream().findFirst();
    }

    Optional<DocumentRecord> findByIdForUpdate(UUID documentId) {
        return jdbcTemplate.query("SELECT * FROM document WHERE id = ? FOR UPDATE",
                this::mapDocument, documentId).stream().findFirst();
    }

    boolean update(DocumentRecord document, long previousVersion) {
        return jdbcTemplate.update("""
                UPDATE document
                SET document_status = ?, verified_size_bytes = ?,
                    verified_sha256 = ?, object_version = ?, available_at = ?,
                    superseded_at = ?, updated_at = ?, version = ?
                WHERE id = ? AND version = ?
                """, document.status().name(), document.verifiedSizeBytes(),
                document.verifiedSha256(), document.objectVersion(),
                timestamp(document.availableAt()), timestamp(document.supersededAt()),
                Timestamp.from(document.updatedAt()), document.version(),
                document.id(), previousVersion) == 1;
    }

    private DocumentRecord mapDocument(ResultSet resultSet, int rowNumber)
            throws SQLException {
        return new DocumentRecord(resultSet.getObject("id", UUID.class),
                resultSet.getObject("deal_id", UUID.class),
                resultSet.getString("file_name"), resultSet.getString("media_type"),
                DocumentStatus.valueOf(resultSet.getString("document_status")),
                resultSet.getString("object_key"), resultSet.getLong("declared_size_bytes"),
                resultSet.getString("declared_sha256"),
                resultSet.getTimestamp("upload_expires_at").toInstant(),
                resultSet.getObject("verified_size_bytes", Long.class),
                resultSet.getString("verified_sha256"), resultSet.getString("object_version"),
                resultSet.getTimestamp("created_at").toInstant(), instant(resultSet, "available_at"),
                instant(resultSet, "superseded_at"), resultSet.getTimestamp("updated_at").toInstant(),
                resultSet.getLong("version"));
    }

    private static Timestamp timestamp(Instant value) {
        return value == null ? null : Timestamp.from(value);
    }

    private static Instant instant(ResultSet resultSet, String column)
            throws SQLException {
        Timestamp timestamp = resultSet.getTimestamp(column);
        return timestamp == null ? null : timestamp.toInstant();
    }

    record DocumentRecord(UUID id, UUID dealId, String fileName, String mediaType,
            DocumentStatus status, String objectKey, long declaredSizeBytes,
            String declaredSha256, Instant uploadExpiresAt, Long verifiedSizeBytes,
            String verifiedSha256, String objectVersion, Instant createdAt,
            Instant availableAt, Instant supersededAt, Instant updatedAt, long version) {
    }
}
