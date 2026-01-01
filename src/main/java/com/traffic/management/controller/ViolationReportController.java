package com.traffic.management.controller;

import com.traffic.management.service.ViolationReportService;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.io.OutputStream;
import java.util.List;
import java.util.Map;

/**
 * 违规报告控制器 - TrafficMind 交通智脑
 *
 * 功能：提供PDF报告导出API
 */
@RestController
@RequestMapping("/api/violations/report")
public class ViolationReportController {

    @Autowired
    private ViolationReportService reportService;

    /**
     * 获取可选的日期范围列表
     * GET /api/violations/report/options
     */
    @GetMapping("/options")
    public Map<String, Object> getAvailableOptions() {
        List<Map<String, Object>> ranges = reportService.getAvailableRanges();
        return Map.of(
                "ranges", ranges,
                "message", "可选的日期范围列表"
        );
    }

    /**
     * 下载PDF报告
     * GET /api/violations/report/pdf?range=LAST_7_DAYS
     *
     * @param range 日期范围 (LAST_7_DAYS, LAST_30_DAYS, LAST_90_DAYS)
     * @param response HTTP响应
     */
    @GetMapping("/pdf")
    public void downloadReport(
            @RequestParam(defaultValue = "LAST_7_DAYS") String range,
            HttpServletResponse response) {

        try {
            // 解析日期范围
            ViolationReportService.DateRange dateRange;
            try {
                dateRange = ViolationReportService.DateRange.valueOf(range.toUpperCase());
            } catch (IllegalArgumentException e) {
                // 默认使用最近7天
                dateRange = ViolationReportService.DateRange.LAST_7_DAYS;
            }

            // 生成PDF
            byte[] pdfBytes = reportService.generateReport(dateRange);

            // 设置响应头
            String fileName = String.format("violation_report_%s_%s.pdf",
                    dateRange.name().toLowerCase(),
                    java.time.LocalDate.now().toString());

            response.setContentType("application/pdf");
            response.setHeader("Content-Disposition", "attachment; filename=\"" + fileName + "\"");
            response.setContentLength(pdfBytes.length);

            // 写入响应流
            try (OutputStream out = response.getOutputStream()) {
                out.write(pdfBytes);
                out.flush();
            }

        } catch (Exception e) {
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            response.setContentType("application/json;charset=UTF-8");
            try {
                response.getWriter().write("{\"error\":\"" + e.getMessage() + "\"}");
            } catch (Exception ex) {
                // 忽略写入错误
            }
        }
    }
}
