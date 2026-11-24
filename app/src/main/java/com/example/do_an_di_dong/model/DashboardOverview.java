package com.example.do_an_di_dong.model;

import com.google.gson.annotations.SerializedName;
import java.util.List;

public class DashboardOverview {
    @SerializedName(value = "summary", alternate = {"Summary"})
    private DashboardSummary summary;

    @SerializedName(value = "trend", alternate = {"Trend"})
    private DashboardTrend trend;

    @SerializedName(value = "branchPerformance", alternate = {"BranchPerformance"})
    private List<BranchPerformance> branchPerformance;

    @SerializedName(value = "data", alternate = {"Data"})
    private DashboardOverview data;

    public DashboardSummary getSummary() {
        if (summary != null) return summary;
        if (data != null) return data.getSummary();
        return null;
    }

    public DashboardTrend getTrend() {
        if (trend != null) return trend;
        if (data != null) return data.getTrend();
        return null;
    }

    public List<BranchPerformance> getBranchPerformance() {
        if (branchPerformance != null) return branchPerformance;
        if (data != null) return data.getBranchPerformance();
        return null;
    }
}
