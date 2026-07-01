package com.sbnz.frontend.drools;

import org.kie.api.KieServices;
import org.kie.api.builder.Message;
import org.kie.api.builder.Results;
import org.kie.api.io.ResourceType;
import org.kie.api.runtime.KieSession;
import org.kie.internal.utils.KieHelper;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public final class RespiratoryKieSessionFactory {

    private static final String RULE_ROOT = "rules/";

    private static final String[] DRL_FILES = {
            "00-event-declarations.drl",
            "05-signal-category-hierarchy.drl",
            "10-triage-rules.drl",
            "20-vitals-rules.drl",
            "30-hydration-rules.drl",
            "40-cep-rules.drl",
            "50-final-decision-rules.drl",
            "60-bc-queries.drl"
    };

    private RespiratoryKieSessionFactory() {
    }

    public static KieSession createSession() {
        KieServices ks = KieServices.Factory.get();
        KieHelper helper = new KieHelper();

        for (String drl : DRL_FILES) {
            helper.addResource(
                    ks.getResources().newInputStreamResource(openRequiredResource(RULE_ROOT + drl)),
                    ResourceType.DRL
            );
        }

        helper.addContent(generateAgeThresholdRules(), ResourceType.DRL);
        helper.addContent(generateRedFlagRules(), ResourceType.DRL);

        Results results = helper.verify();
        if (results.hasMessages(Message.Level.ERROR)) {
            StringBuilder errors = new StringBuilder("KieSession build failed:\n");
            for (Message message : results.getMessages(Message.Level.ERROR)) {
                errors.append(message.getText()).append('\n');
            }
            throw new IllegalStateException(errors.toString());
        }

        return helper.build().newKieSession();
    }

    private static String generateAgeThresholdRules() {
        StringBuilder drl = new StringBuilder();
        drl.append("package rules;\n\n");
        drl.append("import com.sbnz.model.ChildProfile;\n");
        drl.append("import com.sbnz.model.ClinicalSignal;\n");
        drl.append("import com.sbnz.model.RespiratoryAssessmentEvent;\n\n");

        for (String[] row : readCsv("rules/templates/age-thresholds.template")) {
            String ruleId = row[0];
            String ageBand = row[1];
            String minAge = row[2];
            String maxAge = row[3];
            String rr = row[4];
            String severeRr = row[5];
            String monitorSpo2 = row[6];
            String moderateSpo2 = row[7];
            String severeSpo2 = row[8];

            drl.append("rule \"TPL AGE tachypnea ").append(ruleId).append("\"\n")
                    .append("when\n")
                    .append("    $c : ChildProfile($id : childId, ageInMonths >= ").append(minAge)
                    .append(" && ageInMonths <= ").append(maxAge).append(")\n")
                    .append("    RespiratoryAssessmentEvent(childId == $id, respiratoryRate >= ").append(rr).append(")\n")
                    .append("    not ClinicalSignal(childId == $id, type == \"TEMPLATE_TACHYPNEA_").append(ageBand).append("\")\n")
                    .append("then\n")
                    .append("    insert(new ClinicalSignal($id, \"TEMPLATE_TACHYPNEA_").append(ageBand)
                    .append("\", \"template age threshold matched: ").append(ageBand).append("\"));\n")
                    .append("end\n\n");

            drl.append("rule \"TPL AGE severe tachypnea ").append(ruleId).append("\"\n")
                    .append("when\n")
                    .append("    $c : ChildProfile($id : childId, ageInMonths >= ").append(minAge)
                    .append(" && ageInMonths <= ").append(maxAge).append(")\n")
                    .append("    RespiratoryAssessmentEvent(childId == $id, respiratoryRate >= ").append(severeRr).append(")\n")
                    .append("    not ClinicalSignal(childId == $id, type == \"TEMPLATE_SEVERE_TACHYPNEA_").append(ageBand).append("\")\n")
                    .append("then\n")
                    .append("    insert(new ClinicalSignal($id, \"TEMPLATE_SEVERE_TACHYPNEA_").append(ageBand)
                    .append("\", \"template severe tachypnea threshold matched: ").append(ageBand).append("\"));\n")
                    .append("end\n\n");

            drl.append("rule \"TPL AGE oxygen monitor ").append(ruleId).append("\"\n")
                    .append("when\n")
                    .append("    $c : ChildProfile($id : childId, ageInMonths >= ").append(minAge)
                    .append(" && ageInMonths <= ").append(maxAge).append(")\n")
                    .append("    RespiratoryAssessmentEvent(childId == $id, spo2 <= ").append(monitorSpo2)
                    .append(" && spo2 > ").append(moderateSpo2).append(")\n")
                    .append("    not ClinicalSignal(childId == $id, type == \"TEMPLATE_OXYGEN_MONITOR_").append(ageBand).append("\")\n")
                    .append("then\n")
                    .append("    insert(new ClinicalSignal($id, \"TEMPLATE_OXYGEN_MONITOR_").append(ageBand)
                    .append("\", \"template oxygen monitor threshold matched: ").append(ageBand).append("\"));\n")
                    .append("end\n\n");

            drl.append("rule \"TPL AGE severe oxygen ").append(ruleId).append("\"\n")
                    .append("when\n")
                    .append("    $c : ChildProfile($id : childId, ageInMonths >= ").append(minAge)
                    .append(" && ageInMonths <= ").append(maxAge).append(")\n")
                    .append("    RespiratoryAssessmentEvent(childId == $id, spo2 < ").append(severeSpo2).append(")\n")
                    .append("    not ClinicalSignal(childId == $id, type == \"TEMPLATE_SEVERE_HYPOXEMIA_").append(ageBand).append("\")\n")
                    .append("then\n")
                    .append("    insert(new ClinicalSignal($id, \"TEMPLATE_SEVERE_HYPOXEMIA_").append(ageBand)
                    .append("\", \"template severe oxygen threshold matched: ").append(ageBand).append("\"));\n")
                    .append("end\n\n");
        }

        return drl.toString();
    }

    private static String generateRedFlagRules() {
        StringBuilder drl = new StringBuilder();
        drl.append("package rules;\n\n");
        drl.append("import com.sbnz.model.ChildProfile;\n");
        drl.append("import com.sbnz.model.ClinicalSignal;\n");
        drl.append("import com.sbnz.model.HydrationIntakeEvent;\n");
        drl.append("import com.sbnz.model.Recommendation;\n");
        drl.append("import com.sbnz.model.RespiratoryAssessmentEvent;\n\n");

        for (String[] row : readCsv("rules/templates/red-flags.template")) {
            String flagId = row[0];
            String flagType = row[1];
            String eventPattern = String.join(",", Arrays.copyOfRange(row, 4, row.length - 8)).trim();
            String salience = row[row.length - 8];
            String action = row[row.length - 4];
            String explanationCode = row[row.length - 2];
            String reason = row[row.length - 1];

            drl.append("rule \"TPL RED FLAG ").append(flagId).append(" ").append(flagType).append("\"\n")
                    .append("salience ").append(salience).append("\n")
                    .append("when\n")
                    .append("    $c : ChildProfile($id : childId)\n")
                    .append("    ").append(eventPattern).append("\n")
                    .append("    not ClinicalSignal(childId == $id, type == \"").append(explanationCode).append("\")\n")
                    .append("then\n")
                    .append("    insert(new ClinicalSignal($id, \"").append(explanationCode).append("\", \"").append(reason).append("\"));\n")
                    .append("end\n\n");

            drl.append("rule \"TPL RED FLAG ACTION ").append(flagId).append(" ").append(flagType).append("\"\n")
                    .append("salience ").append(salience).append("\n")
                    .append("when\n")
                    .append("    $c : ChildProfile($id : childId)\n")
                    .append("    ClinicalSignal(childId == $id, type == \"").append(explanationCode).append("\")\n")
                    .append("    eval(\"EMERGENCY_RESPONSE\".equals(\"").append(action).append("\"))\n")
                    .append("    not Recommendation(childId == $id, action == \"EMERGENCY_RESPONSE\")\n")
                    .append("then\n")
                    .append("    insert(new Recommendation($id, \"EMERGENCY_RESPONSE\", \"").append(reason).append("\"));\n")
                    .append("end\n\n");
        }

        return drl.toString();
    }

    private static List<String[]> readCsv(String resourcePath) {
        try (InputStream input = openRequiredResource(resourcePath);
             BufferedReader reader = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8))) {
            List<String[]> rows = new ArrayList<>();
            String line;
            boolean header = true;
            while ((line = reader.readLine()) != null) {
                if (header) {
                    header = false;
                    continue;
                }
                if (line.trim().isEmpty()) {
                    continue;
                }
                rows.add(line.split(",", -1));
            }
            return rows;
        } catch (IOException e) {
            throw new IllegalStateException("Cannot read template data: " + resourcePath, e);
        }
    }

    private static InputStream openRequiredResource(String resourcePath) {
        ClassLoader contextClassLoader = Thread.currentThread().getContextClassLoader();
        if (contextClassLoader != null) {
            InputStream input = contextClassLoader.getResourceAsStream(resourcePath);
            if (input != null) {
                return input;
            }
        }

        InputStream input = RespiratoryKieSessionFactory.class.getClassLoader().getResourceAsStream(resourcePath);
        if (input != null) {
            return input;
        }

        input = RespiratoryKieSessionFactory.class.getResourceAsStream("/" + resourcePath);
        if (input != null) {
            return input;
        }

        throw new IllegalStateException("Template data not found: " + resourcePath);
    }
}
