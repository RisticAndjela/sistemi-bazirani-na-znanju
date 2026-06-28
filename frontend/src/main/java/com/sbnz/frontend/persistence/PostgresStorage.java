package com.sbnz.frontend.persistence;

import com.sbnz.frontend.PatientCase;
import com.sbnz.frontend.RunHistoryEntry;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class PostgresStorage {

    private static final String DEFAULT_URL = "jdbc:postgresql://localhost:5432/sbnz_respiratory";
    private static final String DEFAULT_USER = "sbnz_user";
    private static final String DEFAULT_PASSWORD = "sbnz_pass";

    private final String jdbcUrl;
    private final String username;
    private final String password;

    public PostgresStorage() {
        this.jdbcUrl = readSetting("SBNZ_DB_URL", "sbnz.db.url", DEFAULT_URL);
        this.username = readSetting("SBNZ_DB_USER", "sbnz.db.user", DEFAULT_USER);
        this.password = readSetting("SBNZ_DB_PASSWORD", "sbnz.db.password", DEFAULT_PASSWORD);
    }

    public void initialize() {
        try (Connection connection = openConnection();
             Statement statement = connection.createStatement()) {
            statement.execute(
                    "CREATE TABLE IF NOT EXISTS patient_cases (" +
                            "child_id BIGINT PRIMARY KEY," +
                            "age_in_months INTEGER NOT NULL," +
                            "rr1 INTEGER NOT NULL," +
                            "spo21 INTEGER NOT NULL," +
                            "chest1 BOOLEAN NOT NULL," +
                            "grunting1 BOOLEAN NOT NULL," +
                            "apnea1 BOOLEAN NOT NULL," +
                            "cyanosis1 BOOLEAN NOT NULL," +
                            "rr2 INTEGER NOT NULL," +
                            "spo22 INTEGER NOT NULL," +
                            "chest2 BOOLEAN NOT NULL," +
                            "grunting2 BOOLEAN NOT NULL," +
                            "apnea2 BOOLEAN NOT NULL," +
                            "cyanosis2 BOOLEAN NOT NULL," +
                            "intake_percent INTEGER NOT NULL," +
                            "poor_feeding BOOLEAN NOT NULL," +
                            "updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP" +
                            ")"
            );
            statement.execute(
                    "CREATE TABLE IF NOT EXISTS rule_run_history (" +
                            "id BIGSERIAL PRIMARY KEY," +
                            "child_id BIGINT NOT NULL REFERENCES patient_cases(child_id) ON DELETE CASCADE," +
                            "created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP," +
                            "report TEXT NOT NULL" +
                            ")"
            );
        } catch (SQLException ex) {
            throw new IllegalStateException(buildConnectionError(ex), ex);
        }
    }

    public List<PatientCase> findAllPatients() {
        String sql = "SELECT child_id, age_in_months, rr1, spo21, chest1, grunting1, apnea1, cyanosis1, " +
                "rr2, spo22, chest2, grunting2, apnea2, cyanosis2, intake_percent, poor_feeding " +
                "FROM patient_cases ORDER BY child_id";
        try (Connection connection = openConnection();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet rs = statement.executeQuery()) {
            List<PatientCase> patients = new ArrayList<>();
            while (rs.next()) {
                patients.add(mapPatient(rs));
            }
            return patients;
        } catch (SQLException ex) {
            throw new IllegalStateException("Failed to load patients from PostgreSQL.", ex);
        }
    }

    public boolean hasPatients() {
        try (Connection connection = openConnection();
             PreparedStatement statement = connection.prepareStatement("SELECT EXISTS (SELECT 1 FROM patient_cases)");
             ResultSet rs = statement.executeQuery()) {
            return rs.next() && rs.getBoolean(1);
        } catch (SQLException ex) {
            throw new IllegalStateException("Failed to check patient data in PostgreSQL.", ex);
        }
    }

    public void savePatient(PatientCase patientCase) {
        String sql = "INSERT INTO patient_cases (" +
                "child_id, age_in_months, rr1, spo21, chest1, grunting1, apnea1, cyanosis1, " +
                "rr2, spo22, chest2, grunting2, apnea2, cyanosis2, intake_percent, poor_feeding, updated_at" +
                ") VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP) " +
                "ON CONFLICT (child_id) DO UPDATE SET " +
                "age_in_months = EXCLUDED.age_in_months, " +
                "rr1 = EXCLUDED.rr1, spo21 = EXCLUDED.spo21, chest1 = EXCLUDED.chest1, " +
                "grunting1 = EXCLUDED.grunting1, apnea1 = EXCLUDED.apnea1, cyanosis1 = EXCLUDED.cyanosis1, " +
                "rr2 = EXCLUDED.rr2, spo22 = EXCLUDED.spo22, chest2 = EXCLUDED.chest2, " +
                "grunting2 = EXCLUDED.grunting2, apnea2 = EXCLUDED.apnea2, cyanosis2 = EXCLUDED.cyanosis2, " +
                "intake_percent = EXCLUDED.intake_percent, poor_feeding = EXCLUDED.poor_feeding, " +
                "updated_at = CURRENT_TIMESTAMP";
        try (Connection connection = openConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            fillPatientStatement(statement, patientCase);
            statement.executeUpdate();
        } catch (SQLException ex) {
            throw new IllegalStateException("Failed to save patient " + patientCase.childId + " to PostgreSQL.", ex);
        }
    }

    public void saveRunHistory(Long childId, String report) {
        String sql = "INSERT INTO rule_run_history (child_id, report) VALUES (?, ?)";
        try (Connection connection = openConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, childId);
            statement.setString(2, report);
            statement.executeUpdate();
        } catch (SQLException ex) {
            throw new IllegalStateException("Failed to save run history for patient " + childId + ".", ex);
        }
    }

    public List<RunHistoryEntry> findHistoryForChild(Long childId) {
        String sql = "SELECT created_at, report FROM rule_run_history WHERE child_id = ? ORDER BY created_at DESC, id DESC";
        try (Connection connection = openConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, childId);
            try (ResultSet rs = statement.executeQuery()) {
                List<RunHistoryEntry> history = new ArrayList<>();
                while (rs.next()) {
                    Timestamp createdAt = rs.getTimestamp("created_at");
                    history.add(new RunHistoryEntry(createdAt.toLocalDateTime(), rs.getString("report")));
                }
                return history;
            }
        } catch (SQLException ex) {
            throw new IllegalStateException("Failed to load run history for patient " + childId + ".", ex);
        }
    }

    public String getConnectionSummary() {
        return jdbcUrl + " | user=" + username;
    }

    private Connection openConnection() throws SQLException {
        return DriverManager.getConnection(jdbcUrl, username, password);
    }

    private String readSetting(String envKey, String propertyKey, String defaultValue) {
        String envValue = System.getenv(envKey);
        if (envValue != null && !envValue.isBlank()) {
            return envValue;
        }
        String propertyValue = System.getProperty(propertyKey);
        if (propertyValue != null && !propertyValue.isBlank()) {
            return propertyValue;
        }
        return defaultValue;
    }

    private PatientCase mapPatient(ResultSet rs) throws SQLException {
        PatientCase patientCase = new PatientCase();
        patientCase.childId = rs.getLong("child_id");
        patientCase.ageInMonths = rs.getInt("age_in_months");
        patientCase.rr1 = rs.getInt("rr1");
        patientCase.spo21 = rs.getInt("spo21");
        patientCase.chest1 = rs.getBoolean("chest1");
        patientCase.grunting1 = rs.getBoolean("grunting1");
        patientCase.apnea1 = rs.getBoolean("apnea1");
        patientCase.cyanosis1 = rs.getBoolean("cyanosis1");
        patientCase.rr2 = rs.getInt("rr2");
        patientCase.spo22 = rs.getInt("spo22");
        patientCase.chest2 = rs.getBoolean("chest2");
        patientCase.grunting2 = rs.getBoolean("grunting2");
        patientCase.apnea2 = rs.getBoolean("apnea2");
        patientCase.cyanosis2 = rs.getBoolean("cyanosis2");
        patientCase.intakePercent = rs.getInt("intake_percent");
        patientCase.poorFeeding = rs.getBoolean("poor_feeding");
        return patientCase;
    }

    private void fillPatientStatement(PreparedStatement statement, PatientCase patientCase) throws SQLException {
        statement.setLong(1, patientCase.childId);
        statement.setInt(2, patientCase.ageInMonths);
        statement.setInt(3, patientCase.rr1);
        statement.setInt(4, patientCase.spo21);
        statement.setBoolean(5, patientCase.chest1);
        statement.setBoolean(6, patientCase.grunting1);
        statement.setBoolean(7, patientCase.apnea1);
        statement.setBoolean(8, patientCase.cyanosis1);
        statement.setInt(9, patientCase.rr2);
        statement.setInt(10, patientCase.spo22);
        statement.setBoolean(11, patientCase.chest2);
        statement.setBoolean(12, patientCase.grunting2);
        statement.setBoolean(13, patientCase.apnea2);
        statement.setBoolean(14, patientCase.cyanosis2);
        statement.setInt(15, patientCase.intakePercent);
        statement.setBoolean(16, patientCase.poorFeeding);
    }

    private String buildConnectionError(SQLException ex) {
        return "PostgreSQL connection failed for " + getConnectionSummary() +
                ". Start the local database with run-postgres.bat or provide SBNZ_DB_URL/SBNZ_DB_USER/SBNZ_DB_PASSWORD. " +
                "Cause: " + ex.getMessage();
    }
}
