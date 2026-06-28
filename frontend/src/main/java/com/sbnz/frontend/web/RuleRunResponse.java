package com.sbnz.frontend.web;

import com.sbnz.frontend.PatientCase;

public class RuleRunResponse {
    public PatientCase patientCase;
    public String sessionMode;
    public String rawReport;
    public String htmlReport;

    public RuleRunResponse(PatientCase patientCase, String sessionMode, String rawReport, String htmlReport) {
        this.patientCase = patientCase;
        this.sessionMode = sessionMode;
        this.rawReport = rawReport;
        this.htmlReport = htmlReport;
    }
}
