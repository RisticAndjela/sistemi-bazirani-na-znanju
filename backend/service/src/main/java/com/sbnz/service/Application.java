package com.sbnz.service;

import com.sbnz.model.ChildProfile;
import com.sbnz.model.ClinicalSignal;
import com.sbnz.model.HydrationIntakeEvent;
import com.sbnz.model.Recommendation;
import com.sbnz.model.RespiratoryAssessmentEvent;
import com.sbnz.service.drools.RespiratoryKieSessionFactory;
import org.kie.api.event.rule.AfterMatchFiredEvent;
import org.kie.api.event.rule.DefaultAgendaEventListener;
import org.kie.api.runtime.KieSession;
import org.kie.api.runtime.rule.QueryResults;
import org.kie.api.runtime.rule.QueryResultsRow;

import java.util.ArrayList;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.Date;
import java.util.Collection;
import java.util.List;

public class Application {

    public static void main(String[] args) {
        KieSession ksession = RespiratoryKieSessionFactory.createSession();
        try {
            List<String> activatedRules = new ArrayList<>();
            ksession.addEventListener(new DefaultAgendaEventListener() {
                @Override
                public void afterMatchFired(AfterMatchFiredEvent event) {
                    activatedRules.add(event.getMatch().getRule().getName());
                }
            });

            // this scenario is simple on purpose so the defense story is clear
            ChildProfile child = new ChildProfile(1L, 10);
            RespiratoryAssessmentEvent first = new RespiratoryAssessmentEvent(1L, Date.from(LocalDateTime.now().minusHours(2).atZone(ZoneId.systemDefault()).toInstant()), 54, 95, true, false);
            RespiratoryAssessmentEvent second = new RespiratoryAssessmentEvent(1L, Date.from(LocalDateTime.now().atZone(ZoneId.systemDefault()).toInstant()), 58, 94, true, true);
            HydrationIntakeEvent hydration = new HydrationIntakeEvent(1L, 60, true);

            ksession.insert(child);
            ksession.insert(first);
            ksession.insert(second);
            ksession.insert(hydration);

            int fired = ksession.fireAllRules();
            System.out.println("Summary");
            System.out.println("patient: 1");
            System.out.println("rules fired: " + fired);

            System.out.println("\nActivated rules");
            for (String ruleName : activatedRules) {
                System.out.println("- " + ruleName);
            }

            System.out.println("\nDerived facts");
            Collection<ClinicalSignal> signals = (Collection<ClinicalSignal>) ksession.getObjects(o -> o instanceof ClinicalSignal);
            List<ClinicalSignal> sortedSignals = new ArrayList<>(signals);
            sortedSignals.sort(Comparator.comparing(ClinicalSignal::getType).thenComparing(ClinicalSignal::getReason));
            for (ClinicalSignal signal : sortedSignals) {
                System.out.println("- " + signal.getType() + ": " + signal.getReason());
            }

            System.out.println("\nFinal decision");
            Collection<Recommendation> recs = (Collection<Recommendation>) ksession.getObjects(o -> o instanceof Recommendation);
            List<Recommendation> sortedRecommendations = new ArrayList<>(recs);
            sortedRecommendations.sort(Comparator.comparing(Recommendation::getAction).thenComparing(Recommendation::getExplanation));
            for (Recommendation recommendation : sortedRecommendations) {
                System.out.println("- " + recommendation.getAction() + ": " + recommendation.getExplanation());
            }

            System.out.println("\nQueries");
            System.out.println("isSafeForHomeMonitoring");
            QueryResults safety = ksession.getQueryResults("isSafeForHomeMonitoring", 1L);
            System.out.println("rows: " + safety.size());

            System.out.println("\ngetEscalationReasons");
            QueryResults reasons = ksession.getQueryResults("getEscalationReasons", 1L, org.kie.api.runtime.rule.Variable.v, org.kie.api.runtime.rule.Variable.v);
            for (QueryResultsRow row : reasons) {
                System.out.println("- " + row.get("$type") + ": " + row.get("$reason"));
            }

            System.out.println("\ngetRequiredAction");
            QueryResults action = ksession.getQueryResults("getRequiredAction", 1L, org.kie.api.runtime.rule.Variable.v, org.kie.api.runtime.rule.Variable.v);
            for (QueryResultsRow row : action) {
                System.out.println("- action: " + row.get("$action") + " | explanation: " + row.get("$explanation"));
            }
        } finally {
            ksession.dispose();
        }
    }
}


