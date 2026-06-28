package com.sbnz.frontend;

import com.sbnz.model.ChildProfile;
import com.sbnz.model.ClinicalSignal;
import com.sbnz.model.HydrationIntakeEvent;
import com.sbnz.model.Recommendation;
import com.sbnz.model.RespiratoryAssessmentEvent;
import com.sbnz.frontend.drools.RespiratoryKieSessionFactory;
import com.sbnz.frontend.persistence.PostgresStorage;
import org.kie.api.event.rule.AfterMatchFiredEvent;
import org.kie.api.event.rule.DefaultAgendaEventListener;
import org.kie.api.runtime.KieSession;
import org.kie.api.runtime.rule.FactHandle;
import org.kie.api.runtime.rule.QueryResults;
import org.kie.api.runtime.rule.QueryResultsRow;
import org.kie.api.runtime.rule.Variable;

import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JEditorPane;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;
import javax.swing.border.EmptyBorder;
import javax.swing.border.TitledBorder;
import java.awt.BorderLayout;
import java.awt.Font;
import java.awt.GridLayout;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.ArrayList;
import java.util.Comparator;

public class DesktopApp {

    private final Map<Long, PatientCase> cases = new LinkedHashMap<>();
    private final DefaultListModel<Long> patientListModel = new DefaultListModel<>();
    private final PostgresStorage storage = new PostgresStorage();

    private JTextField childIdField;
    private JTextField ageField;
    private JTextField rr1Field;
    private JTextField spo21Field;
    private JCheckBox chest1Box;
    private JCheckBox grunting1Box;
    private JCheckBox apnea1Box;
    private JCheckBox cyanosis1Box;

    private JTextField rr2Field;
    private JTextField spo22Field;
    private JCheckBox chest2Box;
    private JCheckBox grunting2Box;
    private JCheckBox apnea2Box;
    private JCheckBox cyanosis2Box;

    private JTextField intakeField;
    private JCheckBox poorFeedingBox;
    private JComboBox<String> modelSelector;
    private JComboBox<String> sessionModeSelector;
    private JTextArea outputArea;
    private JList<Long> patientList;
    private KieSession learnedSession;
    private final Map<Long, FactHandle> learnedChildProfiles = new LinkedHashMap<>();
    private final Map<Long, FactHandle> learnedHydrationFacts = new LinkedHashMap<>();

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            try {
                new DesktopApp().createAndShowUI();
            } catch (IllegalStateException ex) {
                JOptionPane.showMessageDialog(null, ex.getMessage(), "PostgreSQL Error", JOptionPane.ERROR_MESSAGE);
            }
        });
    }

    private void createAndShowUI() {
        storage.initialize();
        JFrame frame = new JFrame("SBNZ Frontend - Patient Rule Tester");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setSize(1180, 760);

        JPanel formPanel = new JPanel(new GridLayout(0, 2, 8, 8));
        formPanel.setBorder(new TitledBorder("Patient Inputs"));

        childIdField = new JTextField("1");
        ageField = new JTextField("10");
        rr1Field = new JTextField("54");
        spo21Field = new JTextField("95");
        chest1Box = new JCheckBox("Chest indrawing - earlier assessment", true);
        grunting1Box = new JCheckBox("Grunting - earlier assessment", false);
        apnea1Box = new JCheckBox("Apnea - earlier assessment", false);
        cyanosis1Box = new JCheckBox("Cyanosis - earlier assessment", false);

        rr2Field = new JTextField("58");
        spo22Field = new JTextField("94");
        chest2Box = new JCheckBox("Chest indrawing - current assessment", true);
        grunting2Box = new JCheckBox("Grunting - current assessment", true);
        apnea2Box = new JCheckBox("Apnea - current assessment", false);
        cyanosis2Box = new JCheckBox("Cyanosis - current assessment", false);

        intakeField = new JTextField("60");
        poorFeedingBox = new JCheckBox("Poor feeding", true);

        modelSelector = new JComboBox<>(new String[]{
                "Standard preset",
                "Conservative preset",
                "High-risk preset"
        });
        sessionModeSelector = new JComboBox<>(new String[]{
                "Compare both sessions",
                "Fresh session only",
                "Learned session only"
        });

        addRow(formPanel, "Child ID", childIdField);
        addRow(formPanel, "Age in months", ageField);
        addRow(formPanel, "Earlier assessment - RR (breaths/min)", rr1Field);
        addRow(formPanel, "Earlier assessment - SpO2", spo21Field);
        addRow(formPanel, "Current assessment - RR (breaths/min)", rr2Field);
        addRow(formPanel, "Current assessment - SpO2", spo22Field);
        addRow(formPanel, "Hydration intake %", intakeField);
        addRow(formPanel, "Input model", modelSelector);
        addRow(formPanel, "Session mode", sessionModeSelector);

        formPanel.add(chest1Box);
        formPanel.add(grunting1Box);
        formPanel.add(apnea1Box);
        formPanel.add(cyanosis1Box);
        formPanel.add(chest2Box);
        formPanel.add(grunting2Box);
        formPanel.add(apnea2Box);
        formPanel.add(cyanosis2Box);
        formPanel.add(poorFeedingBox);
        formPanel.add(new JLabel(""));

        JButton applyPresetButton = new JButton("Apply Preset");
        applyPresetButton.addActionListener(e -> applyPreset());

        JButton savePatientButton = new JButton("Save/Update Patient");
        savePatientButton.addActionListener(e -> savePatientCase());

        JButton runSelectedButton = new JButton("Run Rules");
        runSelectedButton.addActionListener(e -> runSelectedPatient());

        JButton showHistoryButton = new JButton("Show History");
        showHistoryButton.addActionListener(e -> showHistoryForSelectedPatient());

        JButton resetLearnedSessionButton = new JButton("Reset Learned Session");
        resetLearnedSessionButton.addActionListener(e -> resetLearnedSession());

        JPanel buttonPanel = new JPanel(new GridLayout(1, 5, 8, 8));
        buttonPanel.add(applyPresetButton);
        buttonPanel.add(savePatientButton);
        buttonPanel.add(runSelectedButton);
        buttonPanel.add(showHistoryButton);
        buttonPanel.add(resetLearnedSessionButton);

        outputArea = new JTextArea();
        outputArea.setEditable(false);
        outputArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 13));
        outputArea.setBorder(new EmptyBorder(10, 10, 10, 10));
        outputArea.setText("Connected to PostgreSQL: " + storage.getConnectionSummary() + "\nSelect a patient and click Run Rules.\n");

        patientList = new JList<>(patientListModel);
        patientList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        patientList.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                Long selectedId = patientList.getSelectedValue();
                if (selectedId != null) {
                    loadPatientCase(cases.get(selectedId));
                }
            }
        });

        JPanel leftPanel = new JPanel(new BorderLayout());
        leftPanel.setBorder(new TitledBorder("Saved Patients"));
        leftPanel.add(new JLabel("Click patient ID to load data"), BorderLayout.NORTH);
        leftPanel.add(new JScrollPane(patientList), BorderLayout.CENTER);

        JPanel topRight = new JPanel(new BorderLayout());
        topRight.setBorder(new EmptyBorder(8, 8, 8, 8));
        topRight.add(formPanel, BorderLayout.CENTER);
        topRight.add(buttonPanel, BorderLayout.SOUTH);

        JPanel rightPanel = new JPanel(new BorderLayout());
        rightPanel.add(topRight, BorderLayout.NORTH);
        JScrollPane outputScroll = new JScrollPane(outputArea);
        outputScroll.setBorder(new TitledBorder("Rule Engine Output"));
        rightPanel.add(outputScroll, BorderLayout.CENTER);

        JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, leftPanel, rightPanel);
        splitPane.setDividerLocation(260);
        frame.add(splitPane);

        loadCasesFromDatabase();
        seedDemoCasesIfDatabaseEmpty();
        if (patientListModel.isEmpty()) {
            savePatientCase();
        }
        patientList.setSelectedIndex(0);

        frame.setVisible(true);
    }

    private void addRow(JPanel panel, String label, java.awt.Component input) {
        panel.add(new JLabel(label));
        panel.add(input);
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
            List<String> lines = Files.readAllLines(path);
            for (int i = 1; i < lines.size(); i++) {
                String line = lines.get(i).trim();
                if (line.isEmpty()) continue;
                String[] p = line.split(",");
                if (p.length < 16) continue;
                PatientCase c = new PatientCase();
                c.childId = Long.parseLong(p[0].trim());
                c.ageInMonths = Integer.parseInt(p[1].trim());
                c.rr1 = Integer.parseInt(p[2].trim());
                c.spo21 = Integer.parseInt(p[3].trim());
                c.chest1 = Boolean.parseBoolean(p[4].trim());
                c.grunting1 = Boolean.parseBoolean(p[5].trim());
                c.apnea1 = Boolean.parseBoolean(p[6].trim());
                c.cyanosis1 = Boolean.parseBoolean(p[7].trim());
                c.rr2 = Integer.parseInt(p[8].trim());
                c.spo22 = Integer.parseInt(p[9].trim());
                c.chest2 = Boolean.parseBoolean(p[10].trim());
                c.grunting2 = Boolean.parseBoolean(p[11].trim());
                c.apnea2 = Boolean.parseBoolean(p[12].trim());
                c.cyanosis2 = Boolean.parseBoolean(p[13].trim());
                c.intakePercent = Integer.parseInt(p[14].trim());
                c.poorFeeding = Boolean.parseBoolean(p[15].trim());
                storage.savePatient(c);
            }
            loadCasesFromDatabase();
        } catch (Exception ignored) {
            // If demo file is malformed, app still starts with manual input.
        }
    }

    private void loadCasesFromDatabase() {
        cases.clear();
        patientListModel.clear();
        for (PatientCase patientCase : storage.findAllPatients()) {
            cases.put(patientCase.childId, patientCase);
            patientListModel.addElement(patientCase.childId);
        }
    }

    private void applyPreset() {
        String selected = (String) modelSelector.getSelectedItem();
        if ("Conservative preset".equals(selected)) {
            rr1Field.setText("42");
            spo21Field.setText("97");
            rr2Field.setText("40");
            spo22Field.setText("97");
            chest1Box.setSelected(false);
            grunting1Box.setSelected(false);
            apnea1Box.setSelected(false);
            cyanosis1Box.setSelected(false);
            chest2Box.setSelected(false);
            grunting2Box.setSelected(false);
            apnea2Box.setSelected(false);
            cyanosis2Box.setSelected(false);
            intakeField.setText("90");
            poorFeedingBox.setSelected(false);
        } else if ("High-risk preset".equals(selected)) {
            rr1Field.setText("62");
            spo21Field.setText("89");
            rr2Field.setText("68");
            spo22Field.setText("88");
            chest1Box.setSelected(true);
            grunting1Box.setSelected(true);
            apnea1Box.setSelected(true);
            cyanosis1Box.setSelected(false);
            chest2Box.setSelected(true);
            grunting2Box.setSelected(true);
            apnea2Box.setSelected(false);
            cyanosis2Box.setSelected(true);
            intakeField.setText("45");
            poorFeedingBox.setSelected(true);
        } else {
            rr1Field.setText("54");
            spo21Field.setText("95");
            rr2Field.setText("58");
            spo22Field.setText("94");
            chest1Box.setSelected(true);
            grunting1Box.setSelected(false);
            apnea1Box.setSelected(false);
            cyanosis1Box.setSelected(false);
            chest2Box.setSelected(true);
            grunting2Box.setSelected(true);
            apnea2Box.setSelected(false);
            cyanosis2Box.setSelected(false);
            intakeField.setText("60");
            poorFeedingBox.setSelected(true);
        }
    }

    private void savePatientCase() {
        try {
            PatientCase c = readCaseFromForm();
            storage.savePatient(c);
            cases.put(c.childId, c);
            if (!patientListModel.contains(c.childId)) {
                patientListModel.addElement(c.childId);
            }
            patientList.setSelectedValue(c.childId, true);
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(null, "Save failed.\n" + ex.getMessage());
        }
    }

    private void loadPatientCase(PatientCase c) {
        if (c == null) return;
        childIdField.setText(String.valueOf(c.childId));
        ageField.setText(String.valueOf(c.ageInMonths));
        rr1Field.setText(String.valueOf(c.rr1));
        spo21Field.setText(String.valueOf(c.spo21));
        chest1Box.setSelected(c.chest1);
        grunting1Box.setSelected(c.grunting1);
        apnea1Box.setSelected(c.apnea1);
        cyanosis1Box.setSelected(c.cyanosis1);

        rr2Field.setText(String.valueOf(c.rr2));
        spo22Field.setText(String.valueOf(c.spo22));
        chest2Box.setSelected(c.chest2);
        grunting2Box.setSelected(c.grunting2);
        apnea2Box.setSelected(c.apnea2);
        cyanosis2Box.setSelected(c.cyanosis2);

        intakeField.setText(String.valueOf(c.intakePercent));
        poorFeedingBox.setSelected(c.poorFeeding);
    }

    private void runSelectedPatient() {
        Long selectedId = patientList.getSelectedValue();
        if (selectedId == null) {
            JOptionPane.showMessageDialog(null, "Please save and select a patient first.");
            return;
        }
        PatientCase c = cases.get(selectedId);
        if (c == null) return;
        outputArea.setText("Running rules for patient " + selectedId + "...\n");
        try {
            String output = runRulesForMode(c);
            outputArea.setText(output);
            showStyledResultDialog(c.childId, output);
            storage.saveRunHistory(selectedId, output);
        } catch (Exception ex) {
            String errorText = "Rule execution failed:\n" + ex.getClass().getSimpleName() + ": " + ex.getMessage();
            outputArea.setText(errorText);
            JOptionPane.showMessageDialog(null, errorText, "Execution Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void showHistoryForSelectedPatient() {
        Long selectedId = patientList.getSelectedValue();
        if (selectedId == null) {
            JOptionPane.showMessageDialog(null, "Please select a patient first.");
            return;
        }
        List<RunHistoryEntry> history = storage.findHistoryForChild(selectedId);
        if (history == null || history.isEmpty()) {
            outputArea.setText("No PostgreSQL run history yet for patient " + selectedId + ".\nRun rules at least once.");
            return;
        }
        StringBuilder sb = new StringBuilder();
        sb.append("PostgreSQL run history for patient ").append(selectedId).append("\n\n");
        for (int i = 0; i < history.size(); i++) {
            sb.append("Run #").append(i + 1).append("\n");
            sb.append(history.get(i).getCreatedAt()).append("\n");
            sb.append(history.get(i).getReport()).append("\n");
            sb.append("----------------------------------------\n");
        }
        outputArea.setText(sb.toString());
    }

    private void resetLearnedSession() {
        if (learnedSession != null) {
            learnedSession.dispose();
        }
        learnedSession = null;
        learnedChildProfiles.clear();
        learnedHydrationFacts.clear();
        outputArea.setText("Learned session reset.\nFuture learned runs will start from an empty state.");
    }

    private PatientCase readCaseFromForm() {
        PatientCase c = new PatientCase();
        c.childId = Long.parseLong(childIdField.getText().trim());
        c.ageInMonths = Integer.parseInt(ageField.getText().trim());
        c.rr1 = Integer.parseInt(rr1Field.getText().trim());
        c.spo21 = Integer.parseInt(spo21Field.getText().trim());
        c.chest1 = chest1Box.isSelected();
        c.grunting1 = grunting1Box.isSelected();
        c.apnea1 = apnea1Box.isSelected();
        c.cyanosis1 = cyanosis1Box.isSelected();

        c.rr2 = Integer.parseInt(rr2Field.getText().trim());
        c.spo22 = Integer.parseInt(spo22Field.getText().trim());
        c.chest2 = chest2Box.isSelected();
        c.grunting2 = grunting2Box.isSelected();
        c.apnea2 = apnea2Box.isSelected();
        c.cyanosis2 = cyanosis2Box.isSelected();

        c.intakePercent = Integer.parseInt(intakeField.getText().trim());
        c.poorFeeding = poorFeedingBox.isSelected();
        return c;
    }

    private String runRulesForMode(PatientCase c) {
        String selectedMode = (String) sessionModeSelector.getSelectedItem();
        if ("Fresh session only".equals(selectedMode)) {
            KieSession freshSession = RespiratoryKieSessionFactory.createSession();
            try {
                insertCaseIntoFreshSession(freshSession, c);
                return renderSessionReport("Fresh session", freshSession, c.childId, false);
            } finally {
                freshSession.dispose();
            }
        }
        if ("Learned session only".equals(selectedMode)) {
            KieSession activeLearnedSession = getOrCreateLearnedSession();
            refreshLearnedSessionForChild(activeLearnedSession, c);
            return renderSessionReport("Learned session", activeLearnedSession, c.childId, true);
        }

        KieSession freshSession = RespiratoryKieSessionFactory.createSession();
        String freshOutput;
        try {
            insertCaseIntoFreshSession(freshSession, c);
            freshOutput = renderSessionReport("Fresh session", freshSession, c.childId, false);
        } finally {
            freshSession.dispose();
        }

        KieSession activeLearnedSession = getOrCreateLearnedSession();
        refreshLearnedSessionForChild(activeLearnedSession, c);
        String learnedOutput = renderSessionReport("Learned session", activeLearnedSession, c.childId, true);

        return freshOutput + "\n\n========================================\n\n" + learnedOutput;
    }

    private KieSession getOrCreateLearnedSession() {
        if (learnedSession == null) {
            learnedSession = RespiratoryKieSessionFactory.createSession();
        }
        return learnedSession;
    }

    private void insertCaseIntoFreshSession(KieSession ksession, PatientCase c) {
        ChildProfile child = new ChildProfile(c.childId, c.ageInMonths);
        RespiratoryAssessmentEvent first = new RespiratoryAssessmentEvent(c.childId, Date.from(LocalDateTime.now().minusHours(2).atZone(ZoneId.systemDefault()).toInstant()), c.rr1, c.spo21, c.chest1, c.grunting1, c.apnea1, c.cyanosis1);
        RespiratoryAssessmentEvent second = new RespiratoryAssessmentEvent(c.childId, Date.from(LocalDateTime.now().atZone(ZoneId.systemDefault()).toInstant()), c.rr2, c.spo22, c.chest2, c.grunting2, c.apnea2, c.cyanosis2);
        HydrationIntakeEvent hydration = new HydrationIntakeEvent(c.childId, c.intakePercent, c.poorFeeding);

        ksession.insert(child);
        ksession.insert(first);
        ksession.insert(second);
        ksession.insert(hydration);
    }

    private void refreshLearnedSessionForChild(KieSession ksession, PatientCase c) {
        removeDerivedFactsForChild(ksession, c.childId);

        FactHandle existingChild = learnedChildProfiles.get(c.childId);
        if (existingChild != null) {
            ksession.delete(existingChild);
        }
        ChildProfile child = new ChildProfile(c.childId, c.ageInMonths);
        learnedChildProfiles.put(c.childId, ksession.insert(child));

        FactHandle existingHydration = learnedHydrationFacts.get(c.childId);
        if (existingHydration != null) {
            ksession.delete(existingHydration);
        }
        HydrationIntakeEvent hydration = new HydrationIntakeEvent(c.childId, c.intakePercent, c.poorFeeding);
        learnedHydrationFacts.put(c.childId, ksession.insert(hydration));

        RespiratoryAssessmentEvent first = new RespiratoryAssessmentEvent(c.childId, Date.from(LocalDateTime.now().minusHours(2).atZone(ZoneId.systemDefault()).toInstant()), c.rr1, c.spo21, c.chest1, c.grunting1, c.apnea1, c.cyanosis1);
        RespiratoryAssessmentEvent second = new RespiratoryAssessmentEvent(c.childId, Date.from(LocalDateTime.now().atZone(ZoneId.systemDefault()).toInstant()), c.rr2, c.spo22, c.chest2, c.grunting2, c.apnea2, c.cyanosis2);
        ksession.insert(first);
        ksession.insert(second);
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
            int totalRespiratoryEvents = countRespiratoryEventsForChild(ksession, childId);
            out.append("stored respiratory events for child: ").append(totalRespiratoryEvents).append("\n");
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

    private void showStyledResultDialog(Long childId, String output) {
        JDialog dialog = new JDialog((JFrame) SwingUtilities.getWindowAncestor(outputArea), "Defense View - Patient " + childId, true);
        dialog.setSize(980, 760);
        dialog.setLocationRelativeTo(outputArea);

        JEditorPane editorPane = new JEditorPane("text/html", buildStyledHtmlReport(output));
        editorPane.setEditable(false);
        editorPane.setCaretPosition(0);

        JScrollPane scrollPane = new JScrollPane(editorPane);
        scrollPane.setBorder(new TitledBorder("Styled Rule Report"));
        dialog.add(scrollPane);
        dialog.setVisible(true);
    }

    private String buildStyledHtmlReport(String output) {
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
        return text
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;");
    }
}


