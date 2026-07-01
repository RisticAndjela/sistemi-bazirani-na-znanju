package com.sbnz.frontend.web;

import com.sbnz.frontend.PatientCase;
import com.sbnz.frontend.drools.RespiratoryKieSessionFactory;
import com.sbnz.model.ChildProfile;
import com.sbnz.model.ClinicalSignal;
import com.sbnz.model.HydrationIntakeEvent;
import com.sbnz.model.Recommendation;
import com.sbnz.model.RespiratoryAssessmentEvent;
import org.kie.api.event.rule.AfterMatchFiredEvent;
import org.kie.api.event.rule.DefaultAgendaEventListener;
import org.kie.api.runtime.KieSession;
import org.kie.api.runtime.rule.FactHandle;
import org.kie.api.runtime.rule.QueryResults;
import org.kie.api.runtime.rule.QueryResultsRow;
import org.kie.api.runtime.rule.Variable;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class RuleEngineService {

    private KieSession learnedSession;
    private final Map<Long, FactHandle> learnedChildProfiles = new LinkedHashMap<>();
    private final Map<Long, FactHandle> learnedHydrationFacts = new LinkedHashMap<>();
    private final Map<Long, List<FactHandle>> learnedRespiratoryFacts = new LinkedHashMap<>();

    public synchronized String runRulesForMode(PatientCase patientCase, String sessionMode) {
        if ("FRESH_ONLY".equals(sessionMode)) {
            KieSession freshSession = RespiratoryKieSessionFactory.createSession();
            try {
                insertCaseIntoFreshSession(freshSession, patientCase);
                return renderSessionReport("Fresh session", freshSession, patientCase.childId, false);
            } finally {
                freshSession.dispose();
            }
        }

        if ("LEARNED_ONLY".equals(sessionMode)) {
            KieSession activeLearnedSession = getOrCreateLearnedSession();
            refreshLearnedSessionForChild(activeLearnedSession, patientCase);
            return renderSessionReport("Learned session", activeLearnedSession, patientCase.childId, true);
        }

        KieSession freshSession = RespiratoryKieSessionFactory.createSession();
        String freshOutput;
        try {
            insertCaseIntoFreshSession(freshSession, patientCase);
            freshOutput = renderSessionReport("Fresh session", freshSession, patientCase.childId, false);
        } finally {
            freshSession.dispose();
        }

        KieSession activeLearnedSession = getOrCreateLearnedSession();
        refreshLearnedSessionForChild(activeLearnedSession, patientCase);
        String learnedOutput = renderSessionReport("Learned session", activeLearnedSession, patientCase.childId, true);

        return freshOutput + "\n\n========================================\n\n" + learnedOutput;
    }

    public synchronized void resetLearnedSession() {
        if (learnedSession != null) {
            learnedSession.dispose();
        }
        learnedSession = null;
        learnedChildProfiles.clear();
        learnedHydrationFacts.clear();
        learnedRespiratoryFacts.clear();
    }

    public String buildStyledHtmlReport(String output) {
        String[] blocks = output.split("\\n\\n========================================\\n\\n");
        StringBuilder html = new StringBuilder();
        html.append("<html><body style='font-family:Segoe UI,Arial,sans-serif;background:#f4f7fb;color:#1f2937;margin:18px;'>");
        html.append("<div style='font-size:26px;font-weight:bold;color:#0f172a;margin-bottom:14px;'>Rule Engine Defense View</div>");
        for (String block : blocks) {
            html.append(renderSessionBlock(block));
        }
        html.append("</body></html>");
        return html.toString();
    }

    private KieSession getOrCreateLearnedSession() {
        if (learnedSession == null) {
            learnedSession = RespiratoryKieSessionFactory.createSession();
        }
        return learnedSession;
    }

    private void insertCaseIntoFreshSession(KieSession ksession, PatientCase patientCase) {
        ChildProfile child = new ChildProfile(patientCase.childId, patientCase.ageInMonths);
        RespiratoryAssessmentEvent first = new RespiratoryAssessmentEvent(
                patientCase.childId,
                Date.from(LocalDateTime.now().minusHours(2).atZone(ZoneId.systemDefault()).toInstant()),
                patientCase.rr1,
                patientCase.spo21,
                patientCase.chest1,
                patientCase.grunting1,
                patientCase.apnea1,
                patientCase.cyanosis1
        );
        RespiratoryAssessmentEvent second = new RespiratoryAssessmentEvent(
                patientCase.childId,
                Date.from(LocalDateTime.now().atZone(ZoneId.systemDefault()).toInstant()),
                patientCase.rr2,
                patientCase.spo22,
                patientCase.chest2,
                patientCase.grunting2,
                patientCase.apnea2,
                patientCase.cyanosis2
        );
        HydrationIntakeEvent hydration = new HydrationIntakeEvent(patientCase.childId, patientCase.intakePercent, patientCase.poorFeeding);

        ksession.insert(child);
        ksession.insert(first);
        ksession.insert(second);
        ksession.insert(hydration);
    }

    private void refreshLearnedSessionForChild(KieSession ksession, PatientCase patientCase) {
        removeDerivedFactsForChild(ksession, patientCase.childId);
        removeRespiratoryEventsForChild(ksession, patientCase.childId);

        FactHandle existingChild = learnedChildProfiles.get(patientCase.childId);
        if (existingChild != null) {
            ksession.delete(existingChild);
        }
        ChildProfile child = new ChildProfile(patientCase.childId, patientCase.ageInMonths);
        learnedChildProfiles.put(patientCase.childId, ksession.insert(child));

        FactHandle existingHydration = learnedHydrationFacts.get(patientCase.childId);
        if (existingHydration != null) {
            ksession.delete(existingHydration);
        }
        HydrationIntakeEvent hydration = new HydrationIntakeEvent(patientCase.childId, patientCase.intakePercent, patientCase.poorFeeding);
        learnedHydrationFacts.put(patientCase.childId, ksession.insert(hydration));

        RespiratoryAssessmentEvent first = new RespiratoryAssessmentEvent(
                patientCase.childId,
                Date.from(LocalDateTime.now().minusHours(2).atZone(ZoneId.systemDefault()).toInstant()),
                patientCase.rr1,
                patientCase.spo21,
                patientCase.chest1,
                patientCase.grunting1,
                patientCase.apnea1,
                patientCase.cyanosis1
        );
        RespiratoryAssessmentEvent second = new RespiratoryAssessmentEvent(
                patientCase.childId,
                Date.from(LocalDateTime.now().atZone(ZoneId.systemDefault()).toInstant()),
                patientCase.rr2,
                patientCase.spo22,
                patientCase.chest2,
                patientCase.grunting2,
                patientCase.apnea2,
                patientCase.cyanosis2
        );
        List<FactHandle> respiratoryHandles = new ArrayList<>();
        respiratoryHandles.add(ksession.insert(first));
        respiratoryHandles.add(ksession.insert(second));
        learnedRespiratoryFacts.put(patientCase.childId, respiratoryHandles);
    }

    private void removeDerivedFactsForChild(KieSession ksession, Long childId) {
        List<FactHandle> handlesToDelete = new ArrayList<>();
        for (Object object : ksession.getObjects()) {
            if (object instanceof ClinicalSignal) {
                ClinicalSignal signal = (ClinicalSignal) object;
                if (childId.equals(signal.getChildId())) {
                    handlesToDelete.add(ksession.getFactHandle(object));
                }
            }
            if (object instanceof Recommendation) {
                Recommendation recommendation = (Recommendation) object;
                if (childId.equals(recommendation.getChildId())) {
                    handlesToDelete.add(ksession.getFactHandle(object));
                }
            }
        }
        for (FactHandle handle : handlesToDelete) {
            if (handle != null) {
                ksession.delete(handle);
            }
        }
    }

    private void removeRespiratoryEventsForChild(KieSession ksession, Long childId) {
        List<FactHandle> handlesToDelete = new ArrayList<>();
        for (Object object : ksession.getObjects()) {
            if (object instanceof RespiratoryAssessmentEvent) {
                RespiratoryAssessmentEvent event = (RespiratoryAssessmentEvent) object;
                if (childId.equals(event.getChildId())) {
                    handlesToDelete.add(ksession.getFactHandle(object));
                }
            }
        }
        for (FactHandle handle : handlesToDelete) {
            if (handle != null) {
                ksession.delete(handle);
            }
        }
    }

    private String renderSessionReport(String title, KieSession ksession, Long childId, boolean learnedMode) {
        List<String> activatedRules = new ArrayList<>();
        DefaultAgendaEventListener listener = new DefaultAgendaEventListener() {
            @Override
            public void afterMatchFired(AfterMatchFiredEvent event) {
                activatedRules.add(event.getMatch().getRule().getName());
            }
        };
        ksession.addEventListener(listener);
        int fired = ksession.fireAllRules();
        ksession.removeEventListener(listener);

        StringBuilder out = new StringBuilder();
        out.append(title).append("\n");
        out.append("mode: ").append(learnedMode ? "stateful learned memory" : "fresh per-run analysis").append("\n");
        out.append("patient: ").append(childId).append("\n");
        out.append("rules fired: ").append(fired).append("\n");
        if (learnedMode) {
            out.append("stored respiratory events for child: ").append(countRespiratoryEventsForChild(ksession, childId)).append("\n");
        }

        out.append("\nActivated rules\n");
        for (String ruleName : activatedRules) {
            out.append("- ").append(ruleName).append("\n");
        }

        out.append("\nDerived facts\n");
        Collection<ClinicalSignal> signals = (Collection<ClinicalSignal>) ksession.getObjects(o -> o instanceof ClinicalSignal);
        List<ClinicalSignal> sortedSignals = new ArrayList<>();
        for (ClinicalSignal signal : signals) {
            if (childId.equals(signal.getChildId())) {
                sortedSignals.add(signal);
            }
        }
        sortedSignals.sort(Comparator.comparing(ClinicalSignal::getType).thenComparing(ClinicalSignal::getReason));
        for (ClinicalSignal signal : sortedSignals) {
            out.append("- ").append(signal.getType()).append(": ").append(signal.getReason()).append("\n");
        }

        out.append("\nFinal decision\n");
        Collection<Recommendation> recs = (Collection<Recommendation>) ksession.getObjects(o -> o instanceof Recommendation);
        List<Recommendation> sortedRecommendations = new ArrayList<>();
        for (Recommendation recommendation : recs) {
            if (childId.equals(recommendation.getChildId())) {
                sortedRecommendations.add(recommendation);
            }
        }
        sortedRecommendations.sort(Comparator.comparing(Recommendation::getAction).thenComparing(Recommendation::getExplanation));
        for (Recommendation recommendation : sortedRecommendations) {
            out.append("- ").append(recommendation.getAction()).append(": ").append(recommendation.getExplanation()).append("\n");
        }

        out.append("\nQueries\n");
        out.append("isSafeForHomeMonitoring\n");
        QueryResults safety = ksession.getQueryResults("isSafeForHomeMonitoring", childId);
        out.append("rows: ").append(safety.size()).append("\n");

        out.append("\ngetEscalationReasons\n");
        QueryResults reasons = ksession.getQueryResults("getEscalationReasons", childId, Variable.v, Variable.v);
        for (QueryResultsRow row : reasons) {
            out.append("- ").append(row.get("$type")).append(": ").append(row.get("$reason")).append("\n");
        }

        out.append("\ngetHomeMonitoringBlockers\n");
        QueryResults blockers = ksession.getQueryResults("getHomeMonitoringBlockers", childId, Variable.v, Variable.v);
        for (QueryResultsRow row : blockers) {
            out.append("- blocker ").append(row.get("$blockerType")).append(": ").append(row.get("$blockerReason")).append("\n");
        }

        out.append("\ngetRespiratoryCategories\n");
        QueryResults respiratoryCats = ksession.getQueryResults("getRespiratoryCategories", childId, Variable.v, Variable.v);
        for (QueryResultsRow row : respiratoryCats) {
            out.append("- respiratory ").append(row.get("$type")).append(": ").append(row.get("$reason")).append("\n");
        }

        out.append("\ngetHydrationCategories\n");
        QueryResults hydrationCats = ksession.getQueryResults("getHydrationCategories", childId, Variable.v, Variable.v);
        for (QueryResultsRow row : hydrationCats) {
            out.append("- hydration ").append(row.get("$type")).append(": ").append(row.get("$reason")).append("\n");
        }

        out.append("\ngetRequiredAction\n");
        QueryResults action = ksession.getQueryResults("getRequiredAction", childId, Variable.v, Variable.v);
        for (QueryResultsRow row : action) {
            out.append("- action: ").append(row.get("$action")).append(" | explanation: ").append(row.get("$explanation")).append("\n");
        }

        return out.toString();
    }

    private int countRespiratoryEventsForChild(KieSession ksession, Long childId) {
        int count = 0;
        for (Object object : ksession.getObjects()) {
            if (object instanceof RespiratoryAssessmentEvent) {
                RespiratoryAssessmentEvent event = (RespiratoryAssessmentEvent) object;
                if (childId.equals(event.getChildId())) {
                    count++;
                }
            }
        }
        return count;
    }

    private String renderSessionBlock(String block) {
        String[] lines = block.split("\\R");
        String title = lines.length > 0 ? escapeHtml(lines[0]) : "Session";
        List<String> summaryLines = new ArrayList<>();
        List<String> activatedRules = new ArrayList<>();
        List<String> derivedFacts = new ArrayList<>();
        List<String> finalDecision = new ArrayList<>();
        List<String> queries = new ArrayList<>();

        String currentSection = "summary";
        for (int i = 1; i < lines.length; i++) {
            String line = lines[i].trim();
            if (line.isEmpty()) {
                continue;
            }
            if ("Activated rules".equals(line)) {
                currentSection = "rules";
                continue;
            }
            if ("Derived facts".equals(line)) {
                currentSection = "facts";
                continue;
            }
            if ("Final decision".equals(line)) {
                currentSection = "decision";
                continue;
            }
            if ("Queries".equals(line)) {
                currentSection = "queries";
                continue;
            }

            if ("summary".equals(currentSection)) {
                summaryLines.add(line);
            } else if ("rules".equals(currentSection)) {
                activatedRules.add(stripBullet(line));
            } else if ("facts".equals(currentSection)) {
                derivedFacts.add(stripBullet(line));
            } else if ("decision".equals(currentSection)) {
                finalDecision.add(stripBullet(line));
            } else if ("queries".equals(currentSection)) {
                queries.add(line);
            }
        }

        StringBuilder section = new StringBuilder();
        section.append("<div style='border:2px solid #cbd5e1;border-radius:18px;background:#ffffff;padding:18px;margin-bottom:18px;'>");
        section.append("<div style='font-size:24px;font-weight:bold;color:#1d4ed8;margin-bottom:10px;'>").append(title).append("</div>");
        section.append(renderSummaryCard(summaryLines));
        section.append(renderOrderedCard("Activated Rules", activatedRules, "#dbeafe", "#1d4ed8", true));
        section.append(renderOrderedCard("Derived Facts", derivedFacts, "#dcfce7", "#15803d", false));
        section.append(renderDecisionCard(finalDecision));
        section.append(renderQueryCard(queries));
        section.append("</div>");
        return section.toString();
    }

    private String renderSummaryCard(List<String> lines) {
        StringBuilder html = new StringBuilder();
        html.append("<div style='border:1px solid #bfdbfe;background:#eff6ff;border-radius:14px;padding:12px 14px;margin-bottom:14px;'>");
        html.append("<div style='font-size:18px;font-weight:bold;color:#1e3a8a;margin-bottom:6px;'>Summary</div>");
        for (String line : lines) {
            html.append("<div style='margin:4px 0;'><b>").append(highlightKeyValue(line)).append("</b></div>");
        }
        html.append("</div>");
        return html.toString();
    }

    private String renderOrderedCard(String title, List<String> items, String bgColor, String titleColor, boolean italic) {
        StringBuilder html = new StringBuilder();
        html.append("<div style='border:1px solid ").append(titleColor).append(";background:").append(bgColor)
                .append(";border-radius:14px;padding:12px 14px;margin-bottom:14px;'>");
        html.append("<div style='font-size:18px;font-weight:bold;color:").append(titleColor).append(";margin-bottom:8px;'>")
                .append(escapeHtml(title)).append("</div>");
        html.append("<ol style='margin:0;padding-left:24px;'>");
        for (String item : items) {
            html.append("<li style='margin:6px 0;");
            if (italic) {
                html.append("font-style:italic;");
            }
            html.append("'><b>").append(escapeHtml(item)).append("</b></li>");
        }
        html.append("</ol></div>");
        return html.toString();
    }

    private String renderDecisionCard(List<String> decisions) {
        StringBuilder html = new StringBuilder();
        html.append("<div style='border:2px solid #f59e0b;background:#fffbeb;border-radius:14px;padding:12px 14px;margin-bottom:14px;'>");
        html.append("<div style='font-size:18px;font-weight:bold;color:#b45309;margin-bottom:8px;'>Final Decision</div>");
        for (String decision : decisions) {
            html.append("<div style='margin:8px 0;padding:10px;border-radius:10px;background:#ffffff;border:1px solid #fcd34d;'>");
            html.append("<span style='color:#92400e;font-weight:bold;font-size:15px;'>").append(escapeHtml(decision)).append("</span>");
            html.append("</div>");
        }
        html.append("</div>");
        return html.toString();
    }

    private String renderQueryCard(List<String> lines) {
        StringBuilder html = new StringBuilder();
        html.append("<div style='border:1px solid #c084fc;background:#faf5ff;border-radius:14px;padding:12px 14px;'>");
        html.append("<div style='font-size:18px;font-weight:bold;color:#7e22ce;margin-bottom:8px;'>Queries</div>");

        String currentHeader = null;
        List<String> currentItems = new ArrayList<>();
        for (String line : lines) {
            boolean isHeader = !line.startsWith("-") && !line.startsWith("rows:");
            if (isHeader) {
                if (currentHeader != null) {
                    appendQuerySection(html, currentHeader, currentItems);
                    currentItems.clear();
                }
                currentHeader = line;
            } else {
                currentItems.add(line);
            }
        }
        if (currentHeader != null) {
            appendQuerySection(html, currentHeader, currentItems);
        }

        html.append("</div>");
        return html.toString();
    }

    private void appendQuerySection(StringBuilder html, String header, List<String> items) {
        html.append("<div style='margin:10px 0 14px 0;padding:10px;border-radius:10px;background:#ffffff;border:1px solid #e9d5ff;'>");
        html.append("<div style='font-weight:bold;color:#6b21a8;margin-bottom:6px;'>").append(escapeHtml(header)).append("</div>");
        if (items.isEmpty()) {
            html.append("<div style='color:#64748b;'><i>No rows</i></div>");
        } else {
            for (String item : items) {
                html.append("<div style='margin:4px 0;color:#334155;'>").append(escapeHtml(item)).append("</div>");
            }
        }
        html.append("</div>");
    }

    private String stripBullet(String line) {
        if (line.startsWith("- ")) {
            return line.substring(2);
        }
        return line;
    }

    private String highlightKeyValue(String line) {
        int separator = line.indexOf(':');
        if (separator < 0) {
            return escapeHtml(line);
        }
        String key = escapeHtml(line.substring(0, separator));
        String value = escapeHtml(line.substring(separator + 1).trim());
        return "<span style='color:#1e3a8a;'>" + key + ":</span> <span style='color:#0f172a;font-weight:normal;'>" + value + "</span>";
    }

    private String escapeHtml(String text) {
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
