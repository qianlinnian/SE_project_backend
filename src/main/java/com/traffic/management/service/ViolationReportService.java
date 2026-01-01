package com.traffic.management.service;

import com.lowagie.text.*;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import com.traffic.management.entity.Violation;
import com.traffic.management.repository.ViolationRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 违规报告生成服务 - TrafficMind 交通智脑
 *
 * 功能：生成违规分析PDF报告
 */
@Service
public class ViolationReportService {

    @Autowired
    private ViolationRepository violationRepository;

    @Autowired
    private ViolationService violationService;

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy年MM月dd日");
    private static final DateTimeFormatter DATETIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy年MM月dd日 HH:mm:ss");

    /**
     * 日期范围选项
     */
    public enum DateRange {
        LAST_7_DAYS("最近7天", 7),
        LAST_30_DAYS("最近30天", 30),
        LAST_90_DAYS("最近90天", 90);

        private final String label;
        private final int days;

        DateRange(String label, int days) {
            this.label = label;
            this.days = days;
        }

        public String getLabel() {
            return label;
        }

        public int getDays() {
            return days;
        }

        public LocalDateTime getStartTime() {
            return LocalDateTime.now().minusDays(days).with(LocalTime.MIN);
        }

        public LocalDateTime getEndTime() {
            return LocalDateTime.now().with(LocalTime.MAX);
        }
    }

    /**
     * 获取可选的日期范围列表
     */
    public java.util.List<Map<String, Object>> getAvailableRanges() {
        java.util.List<Map<String, Object>> ranges = new ArrayList<>();
        for (DateRange range : DateRange.values()) {
            Map<String, Object> item = new HashMap<>();
            item.put("value", range.name());
            item.put("label", range.getLabel());
            item.put("days", range.getDays());
            item.put("startDate", range.getStartTime().toLocalDate().format(DATE_FORMATTER));
            item.put("endDate", range.getEndTime().toLocalDate().format(DATE_FORMATTER));
            ranges.add(item);
        }
        return ranges;
    }

    /**
     * 生成PDF报告
     *
     * @param range 日期范围
     * @return PDF文件的byte数组
     */
    public byte[] generateReport(DateRange range) throws DocumentException, IOException {
        LocalDateTime startTime = range.getStartTime();
        LocalDateTime endTime = range.getEndTime();

        Document document = new Document(PageSize.A4);
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        PdfWriter writer = PdfWriter.getInstance(document, outputStream);
        document.open();

        // 设置中文字体（使用默认字体，需要服务器有中文字体支持）
        try {
            // 尝试使用系统中文字体
            String fontPath = System.getProperty("user.dir") + "/fonts/simsun.ttf";
            java.io.File fontFile = new java.io.File(fontPath);
            if (!fontFile.exists()) {
                fontPath = "/usr/share/fonts/truetype/simsun/simsun.ttf";
                fontFile = new java.io.File(fontPath);
            }
            if (fontFile.exists()) {
                com.lowagie.text.Font titleFont = new com.lowagie.text.Font(
                        com.lowagie.text.Font.HELVETICA, 18, com.lowagie.text.Font.BOLD);
                com.lowagie.text.Font normalFont = new com.lowagie.text.Font(
                        com.lowagie.text.Font.HELVETICA, 10, com.lowagie.text.Font.NORMAL);
                com.lowagie.text.Font boldFont = new com.lowagie.text.Font(
                        com.lowagie.text.Font.HELVETICA, 10, com.lowagie.text.Font.BOLD);
            }
        } catch (Exception e) {
            // 字体不可用时使用默认字体
        }

        // 1. 添加标题
        addTitle(document, startTime, endTime);

        // 2. 添加统计概览
        addOverviewSection(document, startTime, endTime);

        // 3. 添加违规类型分布
        addTypeDistributionSection(document, startTime, endTime);

        // 4. 添加时间趋势
        addTrendSection(document, startTime, endTime);

        // 5. 添加TOP违规车牌
        addTopViolatorsSection(document, startTime, endTime);

        // 6. 添加路口分布
        addIntersectionSection(document, startTime, endTime);

        document.close();
        return outputStream.toByteArray();
    }

    /**
     * 添加标题
     */
    private void addTitle(Document document, LocalDateTime startTime, LocalDateTime endTime) throws DocumentException {
        // 主标题
        Paragraph title = new Paragraph("TrafficMind 交通智脑 - 违规分析报告",
                new Font(Font.HELVETICA, 20, Font.BOLD, Color.BLACK));
        title.setAlignment(Element.ALIGN_CENTER);
        title.setSpacingAfter(20);
        document.add(title);

        // 生成时间和报告周期
        String generateTime = LocalDateTime.now().format(DATETIME_FORMATTER);
        String period = startTime.toLocalDate().format(DATE_FORMATTER) + " 至 " +
                        endTime.toLocalDate().format(DATE_FORMATTER);

        Font infoFont = new Font(Font.HELVETICA, 10, Font.NORMAL, Color.GRAY);

        Paragraph generateInfo = new Paragraph("生成时间: " + generateTime, infoFont);
        generateInfo.setAlignment(Element.ALIGN_CENTER);
        document.add(generateInfo);

        Paragraph periodInfo = new Paragraph("报告周期: " + period, infoFont);
        periodInfo.setAlignment(Element.ALIGN_CENTER);
        periodInfo.setSpacingAfter(20);
        document.add(periodInfo);

        // 分隔线
        Paragraph line = new Paragraph("==================================================================================");
        line.setSpacingAfter(10);
        document.add(line);
    }

    /**
     * 添加统计概览章节
     */
    private void addOverviewSection(Document document, LocalDateTime startTime, LocalDateTime endTime) throws DocumentException {
        Map<String, Object> overview = violationService.getStatisticsOverview(startTime, endTime);

        addSectionTitle(document, "一、统计概览");

        // 创建概览表格 (2列)
        PdfPTable table = new PdfPTable(2);
        table.setWidthPercentage(80);
        table.setWidths(new float[]{1, 1});

        // 添加概览数据
        addTableRow(table, "总违规数", String.valueOf(overview.get("total")));
        addTableRow(table, "待处理", String.valueOf(overview.get("pending")));
        addTableRow(table, "已确认", String.valueOf(overview.get("confirmed")));
        addTableRow(table, "已拒绝", String.valueOf(overview.get("rejected")));

        Object growthRate = overview.get("growthRate");
        String growthText = growthRate != null ?
                (Double.parseDouble(growthRate.toString()) >= 0 ? "+" : "") +
                growthRate.toString() + "%" : "0%";
        addTableRow(table, "环比变化", growthText);

        document.add(table);
        document.add(new Paragraph(" "));
    }

    /**
     * 添加违规类型分布章节
     */
    private void addTypeDistributionSection(Document document, LocalDateTime startTime, LocalDateTime endTime) throws DocumentException {
        addSectionTitle(document, "二、违规类型分布");

        Map<String, Object> typeData = violationService.getStatisticsByType(startTime, endTime);
        @SuppressWarnings("unchecked")
        java.util.List<Map<String, Object>> types = (java.util.List<Map<String, Object>>) typeData.get("data");

        if (types == null || types.isEmpty()) {
            document.add(new Paragraph("暂无数据", new Font(Font.HELVETICA, 10, Font.ITALIC)));
            return;
        }

        // 计算总数用于百分比
        long total = types.stream().mapToLong(t -> ((Number) t.get("count")).longValue()).sum();

        // 创建表格
        PdfPTable table = new PdfPTable(4);
        table.setWidthPercentage(80);
        table.setWidths(new float[]{1, 1, 1, 1});

        // 表头
        addTableHeader(table, "违规类型");
        addTableHeader(table, "数量");
        addTableHeader(table, "占比");
        addTableHeader(table, "状态");

        // 数据行
        for (Map<String, Object> item : types) {
            String typeName = (String) item.get("typeName");
            long count = ((Number) item.get("count")).longValue();
            double percentage = total > 0 ? (count * 100.0 / total) : 0;

            addTableCell(table, typeName);
            addTableCell(table, String.valueOf(count));
            addTableCell(table, String.format("%.1f%%", percentage));
            addTableCell(table, count > 0 ? "● 活跃" : "○ 无");
        }

        document.add(table);
        document.add(new Paragraph(" "));
    }

    /**
     * 添加时间趋势章节
     */
    private void addTrendSection(Document document, LocalDateTime startTime, LocalDateTime endTime) throws DocumentException {
        addSectionTitle(document, "三、时间趋势（按天统计）");

        Map<String, Object> trendData = violationService.getStatisticsTrend("day", startTime, endTime);
        @SuppressWarnings("unchecked")
        java.util.List<Map<String, Object>> trend = (java.util.List<Map<String, Object>>) trendData.get("data");

        if (trend == null || trend.isEmpty()) {
            document.add(new Paragraph("暂无数据", new Font(Font.HELVETICA, 10, Font.ITALIC)));
            return;
        }

        // 创建表格
        PdfPTable table = new PdfPTable(3);
        table.setWidthPercentage(80);
        table.setWidths(new float[]{2, 1, 1});

        // 表头
        addTableHeader(table, "日期");
        addTableHeader(table, "违规数");
        addTableHeader(table, "环比");

        // 填充数据
        for (int i = 0; i < trend.size(); i++) {
            Map<String, Object> item = trend.get(i);
            String date = (String) item.get("date");
            long count = ((Number) item.get("count")).longValue();

            String change = "-";
            if (i > 0) {
                Map<String, Object> prev = trend.get(i - 1);
                long prevCount = ((Number) prev.get("count")).longValue();
                if (prevCount > 0) {
                    double changeRate = ((double) (count - prevCount) / prevCount) * 100;
                    change = String.format("%+.1f%%", changeRate);
                } else if (count > 0) {
                    change = "+100%";
                }
            }

            addTableCell(table, date);
            addTableCell(table, String.valueOf(count));
            addTableCell(table, change);
        }

        document.add(table);
        document.add(new Paragraph(" "));
    }

    /**
     * 添加TOP违规车牌章节
     */
    private void addTopViolatorsSection(Document document, LocalDateTime startTime, LocalDateTime endTime) throws DocumentException {
        addSectionTitle(document, "四、TOP违规车牌（Top 10）");

        Map<String, Object> topData = violationService.getTopViolators(10, startTime, endTime);
        @SuppressWarnings("unchecked")
        java.util.List<Map<String, Object>> topViolators = (java.util.List<Map<String, Object>>) topData.get("data");

        if (topViolators == null || topViolators.isEmpty()) {
            document.add(new Paragraph("暂无数据", new Font(Font.HELVETICA, 10, Font.ITALIC)));
            return;
        }

        // 创建表格
        PdfPTable table = new PdfPTable(3);
        table.setWidthPercentage(80);
        table.setWidths(new float[]{2, 1, 2});

        // 表头
        addTableHeader(table, "车牌号");
        addTableHeader(table, "违规次数");
        addTableHeader(table, "类型分布");

        // 数据行
        for (Map<String, Object> violator : topViolators) {
            String plateNumber = (String) violator.get("plateNumber");
            long count = ((Number) violator.get("count")).longValue();

            @SuppressWarnings("unchecked")
            Map<String, Long> breakdown = (Map<String, Long>) violator.get("typeBreakdown");
            String breakdownStr = formatTypeBreakdown(breakdown);

            addTableCell(table, plateNumber);
            addTableCell(table, String.valueOf(count));
            addTableCell(table, breakdownStr);
        }

        document.add(table);
        document.add(new Paragraph(" "));
    }

    /**
     * 添加路口分布章节
     */
    private void addIntersectionSection(Document document, LocalDateTime startTime, LocalDateTime endTime) throws DocumentException {
        addSectionTitle(document, "五、路口分布");

        // 按路口ID统计
        java.util.List<Object[]> results = violationRepository.countByIntersectionGrouped(startTime, endTime);

        if (results == null || results.isEmpty()) {
            document.add(new Paragraph("暂无数据", new Font(Font.HELVETICA, 10, Font.ITALIC)));
            return;
        }

        // 创建表格
        PdfPTable table = new PdfPTable(2);
        table.setWidthPercentage(60);
        table.setWidths(new float[]{1, 1});

        // 表头
        addTableHeader(table, "路口ID");
        addTableHeader(table, "违规数");

        // 数据行
        for (Object[] row : results) {
            String intersectionId = row[0].toString();
            long count = ((Number) row[1]).longValue();

            addTableCell(table, "路口 " + intersectionId);
            addTableCell(table, String.valueOf(count));
        }

        document.add(table);
        document.add(new Paragraph(" "));
    }

    /**
     * 添加章节标题
     */
    private void addSectionTitle(Document document, String title) throws DocumentException {
        Font titleFont = new Font(Font.HELVETICA, 14, Font.BOLD, Color.DARK_GRAY);
        Paragraph titlePara = new Paragraph(title, titleFont);
        titlePara.setSpacingBefore(15);
        titlePara.setSpacingAfter(10);
        document.add(titlePara);
    }

    /**
     * 添加表格行（带标签和值）
     */
    private void addTableRow(PdfPTable table, String label, String value) {
        PdfPCell labelCell = new PdfPCell(new Phrase(label, new Font(Font.HELVETICA, 10, Font.NORMAL)));
        labelCell.setBorder(Rectangle.NO_BORDER);
        labelCell.setPadding(5);
        table.addCell(labelCell);

        PdfPCell valueCell = new PdfPCell(new Phrase(value, new Font(Font.HELVETICA, 10, Font.BOLD)));
        valueCell.setBorder(Rectangle.NO_BORDER);
        valueCell.setPadding(5);
        table.addCell(valueCell);
    }

    /**
     * 添加表格表头
     */
    private void addTableHeader(PdfPTable table, String header) {
        PdfPCell cell = new PdfPCell(new Phrase(header, new Font(Font.HELVETICA, 10, Font.BOLD)));
        cell.setBackgroundColor(new Color(240, 240, 240));
        cell.setPadding(5);
        cell.setHorizontalAlignment(Element.ALIGN_CENTER);
        table.addCell(cell);
    }

    /**
     * 添加表格单元格
     */
    private void addTableCell(PdfPTable table, String text) {
        PdfPCell cell = new PdfPCell(new Phrase(text, new Font(Font.HELVETICA, 10, Font.NORMAL)));
        cell.setPadding(5);
        cell.setHorizontalAlignment(Element.ALIGN_CENTER);
        table.addCell(cell);
    }

    /**
     * 格式化类型分布
     */
    private String formatTypeBreakdown(Map<String, Long> breakdown) {
        if (breakdown == null || breakdown.isEmpty()) {
            return "-";
        }
        java.util.List<String> parts = new ArrayList<>();
        breakdown.forEach((type, count) -> {
            String typeName = getViolationTypeName(type);
            parts.add(typeName + count + "次");
        });
        return String.join(", ", parts);
    }

    /**
     * 获取违规类型中文名
     */
    private String getViolationTypeName(String type) {
        return switch (type) {
            case "RED_LIGHT" -> "闯红灯";
            case "WRONG_WAY" -> "逆行";
            case "CROSS_SOLID_LINE" -> "跨实线";
            case "ILLEGAL_TURN" -> "违法转弯";
            case "WAITING_AREA_RED_ENTRY" -> "待转区";
            case "WAITING_AREA_ILLEGAL_EXIT" -> "待转驶离";
            case "SPEEDING" -> "超速";
            case "PARKING_VIOLATION" -> "违停";
            default -> "其他";
        };
    }
}
