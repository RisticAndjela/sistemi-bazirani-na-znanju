package com.sbnz.frontend;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.sbnz.frontend.persistence.PostgresStorage;
import com.sbnz.frontend.web.ApiResponse;
import com.sbnz.frontend.web.RuleEngineService;
import com.sbnz.frontend.web.RuleRunResponse;
import com.sbnz.frontend.web.RunRequest;
import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.Executors;

public class WebApiApp {

    private final PostgresStorage storage = new PostgresStorage();
    private final RuleEngineService ruleEngineService = new RuleEngineService();
    private final ObjectMapper objectMapper = createObjectMapper();

    public static void main(String[] args) throws Exception {
        new WebApiApp().start();
    }

    private void start() throws Exception {
        storage.initialize();
        seedDemoCasesIfDatabaseEmpty();

        int port = Integer.parseInt(System.getProperty("sbnz.web.port", "8080"));
        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);
        server.createContext("/api/health", wrap(this::handleHealth));
        server.createContext("/api/patients", wrap(this::handlePatients));
        server.createContext("/api/rules/run", wrap(this::handleRuleRun));
        server.createContext("/api/rules/reset-learned-session", wrap(this::handleResetLearnedSession));
        server.setExecutor(Executors.newCachedThreadPool());
        server.start();

        System.out.println("SBNZ Web API started on http://localhost:" + port);
        System.out.println("Connected to PostgreSQL: " + storage.getConnectionSummary());
    }

    private void handleHealth(HttpExchange exchange) throws IOException {
        if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
            sendMethodNotAllowed(exchange);
            return;
        }
        sendJson(exchange, 200, new ApiResponse(true, storage.getConnectionSummary(), "API is running."));
    }

    private void handlePatients(HttpExchange exchange) throws IOException {
        String path = exchange.getRequestURI().getPath();
        String method = exchange.getRequestMethod().toUpperCase();

        if ("/api/patients".equals(path)) {
            if ("GET".equals(method)) {
                sendJson(exchange, 200, new ApiResponse(true, storage.findAllPatients(), "Patients loaded."));
                return;
            }
            if ("POST".equals(method)) {
                PatientCase patientCase = readBody(exchange, PatientCase.class);
                validatePatientCase(patientCase);
                storage.savePatient(patientCase);
                sendJson(exchange, 200, new ApiResponse(true, patientCase, "Patient saved."));
                return;
            }
            sendMethodNotAllowed(exchange);
            return;
        }

        if (path.startsWith("/api/patients/") && path.endsWith("/history") && "GET".equals(method)) {
            Long childId = parseChildId(path, "/api/patients/", "/history");
            sendJson(exchange, 200, new ApiResponse(true, storage.findHistoryForChild(childId), "History loaded."));
            return;
        }

        sendNotFound(exchange);
    }

    private void handleRuleRun(HttpExchange exchange) throws IOException {
        if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
            sendMethodNotAllowed(exchange);
            return;
        }

        RunRequest request = readBody(exchange, RunRequest.class);
        if (request == null || request.patientCase == null) {
            throw new IllegalArgumentException("Request body must include patientCase.");
        }

        validatePatientCase(request.patientCase);
        String sessionMode = normalizeSessionMode(request.sessionMode);
        if (request.persistPatient) {
            storage.savePatient(request.patientCase);
        }

        String rawReport = ruleEngineService.runRulesForMode(request.patientCase, sessionMode);
        storage.saveRunHistory(request.patientCase.childId, rawReport);
        RuleRunResponse response = new RuleRunResponse(
                request.patientCase,
                sessionMode,
                rawReport,
                ruleEngineService.buildStyledHtmlReport(rawReport)
        );
        sendJson(exchange, 200, new ApiResponse(true, response, "Rules executed."));
    }

    private void handleResetLearnedSession(HttpExchange exchange) throws IOException {
        if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
            sendMethodNotAllowed(exchange);
            return;
        }
        ruleEngineService.resetLearnedSession();
        sendJson(exchange, 200, new ApiResponse(true, null, "Learned session reset."));
    }

    private HttpHandler wrap(RouteHandler handler) {
        return exchange -> {
            addCorsHeaders(exchange.getResponseHeaders());
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(204, -1);
                exchange.close();
                return;
            }
            try {
                handler.handle(exchange);
            } catch (IllegalArgumentException ex) {
                sendJson(exchange, 400, new ApiResponse(false, null, ex.getMessage()));
            } catch (Exception ex) {
                sendJson(exchange, 500, new ApiResponse(false, null, ex.getMessage()));
            } finally {
                exchange.close();
            }
        };
    }

    private void validatePatientCase(PatientCase patientCase) {
        if (patientCase.childId == null) {
            throw new IllegalArgumentException("Child ID is required.");
        }
        if (patientCase.ageInMonths < 0) {
            throw new IllegalArgumentException("Age in months must be zero or greater.");
        }
    }

    private Long parseChildId(String path, String prefix, String suffix) {
        String value = path.substring(prefix.length(), path.length() - suffix.length());
        return Long.parseLong(value);
    }

    private String normalizeSessionMode(String sessionMode) {
        if ("FRESH_ONLY".equals(sessionMode) || "LEARNED_ONLY".equals(sessionMode)) {
            return sessionMode;
        }
        return "COMPARE_BOTH";
    }

    private <T> T readBody(HttpExchange exchange, Class<T> clazz) throws IOException {
        try (InputStream body = exchange.getRequestBody()) {
            return objectMapper.readValue(body, clazz);
        }
    }

    private void sendJson(HttpExchange exchange, int statusCode, Object body) throws IOException {
        byte[] bytes = objectMapper.writeValueAsBytes(body);
        Headers headers = exchange.getResponseHeaders();
        headers.set("Content-Type", "application/json; charset=utf-8");
        exchange.sendResponseHeaders(statusCode, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    private void sendMethodNotAllowed(HttpExchange exchange) throws IOException {
        sendJson(exchange, 405, new ApiResponse(false, null, "Method not allowed."));
    }

    private void sendNotFound(HttpExchange exchange) throws IOException {
        sendJson(exchange, 404, new ApiResponse(false, null, "Endpoint not found."));
    }

    private void addCorsHeaders(Headers headers) {
        headers.set("Access-Control-Allow-Origin", "*");
        headers.set("Access-Control-Allow-Methods", "GET,POST,OPTIONS");
        headers.set("Access-Control-Allow-Headers", "Content-Type");
    }

    private void seedDemoCasesIfDatabaseEmpty() {
        if (storage.hasPatients()) {
            return;
        }
        Path path = Path.of("data", "demo-children.csv");
        if (!Files.exists(path)) {
            return;
        }
        try {
            List<String> lines = Files.readAllLines(path, StandardCharsets.UTF_8);
            for (int i = 1; i < lines.size(); i++) {
                String line = lines.get(i).trim();
                if (line.isEmpty()) {
                    continue;
                }
                String[] p = line.split(",");
                if (p.length < 16) {
                    continue;
                }
                PatientCase patientCase = new PatientCase();
                patientCase.childId = Long.parseLong(p[0].trim());
                patientCase.ageInMonths = Integer.parseInt(p[1].trim());
                patientCase.rr1 = Integer.parseInt(p[2].trim());
                patientCase.spo21 = Integer.parseInt(p[3].trim());
                patientCase.chest1 = Boolean.parseBoolean(p[4].trim());
                patientCase.grunting1 = Boolean.parseBoolean(p[5].trim());
                patientCase.apnea1 = Boolean.parseBoolean(p[6].trim());
                patientCase.cyanosis1 = Boolean.parseBoolean(p[7].trim());
                patientCase.rr2 = Integer.parseInt(p[8].trim());
                patientCase.spo22 = Integer.parseInt(p[9].trim());
                patientCase.chest2 = Boolean.parseBoolean(p[10].trim());
                patientCase.grunting2 = Boolean.parseBoolean(p[11].trim());
                patientCase.apnea2 = Boolean.parseBoolean(p[12].trim());
                patientCase.cyanosis2 = Boolean.parseBoolean(p[13].trim());
                patientCase.intakePercent = Integer.parseInt(p[14].trim());
                patientCase.poorFeeding = Boolean.parseBoolean(p[15].trim());
                storage.savePatient(patientCase);
            }
        } catch (Exception ignored) {
            // Manual input still works if demo data is unavailable.
        }
    }

    private ObjectMapper createObjectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        return mapper;
    }

    @FunctionalInterface
    private interface RouteHandler {
        void handle(HttpExchange exchange) throws Exception;
    }
}
