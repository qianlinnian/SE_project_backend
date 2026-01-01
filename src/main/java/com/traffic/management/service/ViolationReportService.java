package com.traffic.management.service;

import com.lowagie.text.*;
import com.lowagie.text.pdf.BaseFont;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import com.traffic.management.repository.ViolationRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
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

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter DATETIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    // 字体配置
    private Font boldFont;
    private Font titleFont;
    private Font normalFont;

    public ViolationReportService() {
        initFonts();
    }

    /**
     * 初始化字体 - 尝试多种中文字体
     */
    private void initFonts() {
        // 尝试多个可能的字体路径
        String[] fontPaths = {
            "/usr/share/fonts/truetype/noto/NotoSansCJK-Regular.ttc",
            "/usr/share/fonts/opentype/noto/NotoSansCJK-Regular.ttc",
            "/usr/share/fonts/truetype/wqy/wqy-microhei.ttc",
            "/usr/share/fonts/truetype/wqy/wqy-zenhei.ttc",
            "/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf",
            "/usr/share/fonts/dejavu/DejaVuSans.ttf"
        };

        boolean fontLoaded = false;

        for (String fontPath : fontPaths) {
            try {
                java.io.File fontFile = new java.io.File(fontPath);
                if (fontFile.exists()) {
                    // 使用 BaseFont 创建支持中文的字体
                    BaseFont bf = BaseFont.createFont(fontPath, BaseFont.IDENTITY_H, BaseFont.NOT_EMBEDDED);
                    boldFont = new Font(bf, 12, Font.NORMAL);
                    titleFont = new Font(bf, 16, Font.BOLD);
                    normalFont = new Font(Font.HELVETICA, 10, Font.NORMAL);
                    fontLoaded = true;
                    break;
                }
            } catch (Exception e) {
                // 继续尝试下一个字体
            }
        }

        if (!fontLoaded) {
            // 如果找不到字体，使用默认字体
            boldFont = new Font(Font.HELVETICA, 12, Font.NORMAL);
            titleFont = new Font(Font.HELVETICA, 16, Font.BOLD);
            normalFont = new Font(Font.HELVETICA, 10, Font.NORMAL);
        }
    }

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
    public List<Map<String, Object>> getAvailableRanges() {
        List<Map<String, Object>> ranges = new ArrayList<>();
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

        Document document = new Document(PageSize.A4, 50, 50, 50, 50);
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        PdfWriter writer = PdfWriter.getInstance(document, outputStream);
        document.open();

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
        Paragraph title = new Paragraph("TrafficMind Traffic Report", titleFont);
        title.setAlignment(Element.ALIGN_CENTER);
        title.setSpacingAfter(10);
        document.add(title);

        Paragraph subtitle = new Paragraph("Violation Analysis Report", normalFont);
        subtitle.setAlignment(Element.ALIGN_CENTER);
        subtitle.setSpacingAfter(15);
        document.add(subtitle);

        // 生成时间和报告周期
        String generateTime = LocalDateTime.now().format(DATETIME_FORMATTER);
        String period = startTime.toLocalDate().format(DATE_FORMATTER) + " - " +
                        endTime.toLocalDate().format(DATE_FORMATTER);

        Paragraph generateInfo = new Paragraph("Generated: " + generateTime, normalFont);
        generateInfo.setAlignment(Element.ALIGN_CENTER);
        document.add(generateInfo);

        Paragraph periodInfo = new Paragraph("Period: " + period, normalFont);
        periodInfo.setAlignment(Element.ALIGN_CENTER);
        periodInfo.setSpacingAfter(20);
        document.add(periodInfo);
    }

    /**
     * 添加统计概览章节
     */
    private void addOverviewSection(Document document, LocalDateTime startTime, LocalDateTime endTime) throws DocumentException {
        addSectionTitle(document, "1. Overview");

        Map<String, Object> overview = violationService.getStatisticsOverview(startTime, endTime);

        // 创建概览表格 (2列)
        PdfPTable table = new PdfPTable(2);
        table.setWidthPercentage(70);
        table.setWidths(new float[]{1, 1});

        // 添加概览数据
        addOverviewRow(table, "Total Violations", String.valueOf(overview.get("total")));
        addOverviewRow(table, "Pending", String.valueOf(overview.get("pending")));
        addOverviewRow(table, "Confirmed", String.valueOf(overview.get("confirmed")));
        addOverviewRow(table, "Rejected", String.valueOf(overview.get("rejected")));

        Object growthRate = overview.get("growthRate");
        String growthText = growthRate != null ?
                (Double.parseDouble(growthRate.toString()) >= 0 ? "+" : "") +
                growthRate.toString() + "%" : "0%";
        addOverviewRow(table, "Change Rate", growthText);

        document.add(table);
        document.add(new Paragraph(" "));
    }

    /**
     * 添加违规类型分布章节
     */
    private void addTypeDistributionSection(Document document, LocalDateTime startTime, LocalDateTime endTime) throws DocumentException {
        addSectionTitle(document, "2. Violation Types");

        // 直接从 repository 查询，支持可选日期
        List<Object[]> results = violationRepository.countByViolationTypeGrouped(startTime, endTime);

        if (results.isEmpty()) {
            document.add(new Paragraph("  No violation data available", normalFont));
            return;
        }

        // 计算总数
        long total = results.stream().mapToLong(row -> ((Number) row[1]).longValue()).sum();

        // 创建表格
        PdfPTable table = new PdfPTable(3);
        table.setWidthPercentage(70);
        table.setWidths(new float[]{2, 1, 1});

        // 表头
        addTableHeader(table, "Type");
        addTableHeader(table, "Count");
        addTableHeader(table, "Percent");

        // 数据行
        for (Object[] row : results) {
            String type = row[0].toString();
            String typeName = getViolationTypeName(type);
            long count = ((Number) row[1]).longValue();
            double percentage = total > 0 ? (count * 100.0 / total) : 0;

            addTableCell(table, typeName);
            addTableCell(table, String.valueOf(count));
            addTableCell(table, String.format("%.1f%%", percentage));
        }

        document.add(table);
        document.add(new Paragraph(" "));
    }

    /**
     * 获取违规类型的英文名称
     */
    private String getViolationTypeName(String type) {
        if (type == null) return "Unknown";
        return switch (type) {
            case "RED_LIGHT" -> "Red Light";
            case "WRONG_WAY" -> "Wrong Way";
            case "CROSS_SOLID_LINE" -> "Cross Solid Line";
            case "ILLEGAL_TURN" -> "Illegal Turn";
            default -> type;
        };
    }

    /**
     * 添加时间趋势章节
     */
    private void addTrendSection(Document document, LocalDateTime startTime, LocalDateTime endTime) throws DocumentException {
        addSectionTitle(document, "3. Daily Trend");

        Map<String, Object> trendData = violationService.getStatisticsTrend("day", startTime, endTime);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> trend = (List<Map<String, Object>>) trendData.get("data");

        if (trend == null || trend.isEmpty()) {
            document.add(new Paragraph("  No data available", normalFont));
            return;
        }

        // 创建表格
        PdfPTable table = new PdfPTable(2);
        table.setWidthPercentage(70);
        table.setWidths(new float[]{2, 1});

        // 表头
        addTableHeader(table, "Date");
        addTableHeader(table, "Count");

        // 填充数据
        for (Map<String, Object> item : trend) {
            String date = (String) item.get("date");
            long count = ((Number) item.get("count")).longValue();

            addTableCell(table, date);
            addTableCell(table, String.valueOf(count));
        }

        document.add(table);
        document.add(new Paragraph(" "));
    }

    /**
     * 添加TOP违规车牌章节
     */
    private void addTopViolatorsSection(Document document, LocalDateTime startTime, LocalDateTime endTime) throws DocumentException {
        addSectionTitle(document, "4. Top Violators (Top 10)");

        Map<String, Object> topData = violationService.getTopViolators(10, startTime, endTime);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> topViolators = (List<Map<String, Object>>) topData.get("data");

        if (topViolators == null || topViolators.isEmpty()) {
            document.add(new Paragraph("  No data available", normalFont));
            return;
        }

        // 创建表格
        PdfPTable table = new PdfPTable(2);
        table.setWidthPercentage(70);
        table.setWidths(new float[]{2, 1});

        // 表头
        addTableHeader(table, "Plate Number");
        addTableHeader(table, "Count");

        // 数据行
        for (Map<String, Object> violator : topViolators) {
            String plateNumber = (String) violator.get("plateNumber");
            long count = ((Number) violator.get("count")).longValue();

            addTableCell(table, plateNumber);
            addTableCell(table, String.valueOf(count));
        }

        document.add(table);
        document.add(new Paragraph(" "));
    }

    /**
     * 添加路口分布章节
     */
    private void addIntersectionSection(Document document, LocalDateTime startTime, LocalDateTime endTime) throws DocumentException {
        addSectionTitle(document, "5. By Intersection");

        // 按路口ID统计
        List<Object[]> results = violationRepository.countByIntersectionGrouped(startTime, endTime);

        if (results == null || results.isEmpty()) {
            document.add(new Paragraph("  No data available", normalFont));
            return;
        }

        // 创建表格
        PdfPTable table = new PdfPTable(2);
        table.setWidthPercentage(50);
        table.setWidths(new float[]{1, 1});

        // 表头
        addTableHeader(table, "Intersection ID");
        addTableHeader(table, "Count");

        // 数据行
        for (Object[] row : results) {
            String intersectionId = row[0].toString();
            long count = ((Number) row[1]).longValue();

            addTableCell(table, "Intersection " + intersectionId);
            addTableCell(table, String.valueOf(count));
        }

        document.add(table);
        document.add(new Paragraph(" "));
    }

    /**
     * 添加章节标题
     */
    private void addSectionTitle(Document document, String title) throws DocumentException {
        Paragraph titlePara = new Paragraph(title, boldFont);
        titlePara.setSpacingBefore(15);
        titlePara.setSpacingAfter(10);
        document.add(titlePara);
    }

    /**
     * 添加概览表格行
     */
    private void addOverviewRow(PdfPTable table, String label, String value) {
        PdfPCell labelCell = new PdfPCell(new Phrase(label, normalFont));
        labelCell.setBorder(Rectangle.NO_BORDER);
        labelCell.setPadding(8);
        labelCell.setBackgroundColor(new Color(245, 245, 245));
        table.addCell(labelCell);

        PdfPCell valueCell = new PdfPCell(new Phrase(value, boldFont));
        valueCell.setBorder(Rectangle.NO_BORDER);
        valueCell.setPadding(8);
        table.addCell(valueCell);
    }

    /**
     * 添加表格表头
     */
    private void addTableHeader(PdfPTable table, String header) {
        PdfPCell cell = new PdfPCell(new Phrase(header, normalFont));
        cell.setBackgroundColor(new Color(70, 130, 180));
        cell.setPadding(6);
        cell.setHorizontalAlignment(Element.ALIGN_CENTER);
        table.addCell(cell);
    }

    /**
     * 添加表格单元格
     */
    private void addTableCell(PdfPTable table, String text) {
        PdfPCell cell = new PdfPCell(new Phrase(text, normalFont));
        cell.setPadding(5);
        cell.setHorizontalAlignment(Element.ALIGN_CENTER);
        table.addCell(cell);
    }
}
