package com.heal.io.service;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
import com.heal.io.dto.SaleReportDTO;
import com.heal.io.entity.Sale;
import com.heal.io.entity.SaleItem;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.sf.jasperreports.engine.*;
import net.sf.jasperreports.engine.data.JRBeanCollectionDataSource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class SaleReportService {

    private static final String REPORT_TEMPLATE = "reports/sale-invoice.jrxml";
    private static final String LIST_REPORT_TEMPLATE = "reports/sales-list.jrxml";

    /**
     * Generate PDF report for sales list
     */
    public byte[] generateSalesListPdf(List<Sale> sales, String startDate, String endDate, String printedBy) {
        try {
            // Load the JRXML template
            ClassPathResource resource = new ClassPathResource(LIST_REPORT_TEMPLATE);
            InputStream templateStream = resource.getInputStream();

            // Compile the report
            JasperReport jasperReport = JasperCompileManager.compileReport(templateStream);

            // Load logo image
            InputStream logoStream = null;
            try {
                ClassPathResource logoResource = new ClassPathResource("static/logo-slogan.jpg");
                if (logoResource.exists()) {
                    logoStream = logoResource.getInputStream();
                }
            } catch (Exception e) {
                log.warn("Could not load logo image: {}", e.getMessage());
            }

            // Prepare parameters
            Map<String, Object> parameters = new HashMap<>();
            parameters.put("COMPANY_NAME", "RUPGANJ PHARMACY");
            parameters.put("COMPANY_ADDRESS", "892/1, Beside Shahidbagh Mosque, Rajarbagh, Dhaka - 1217");
            parameters.put("COMPANY_PHONE", "Mobile: 01966551343");
            parameters.put("COMPANY_EMAIL", "");
            parameters.put("LOGO_IMAGE", logoStream);
            parameters.put("GENERATED_DATE",
                    java.time.LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd-MMM-yyyy hh:mm a")));
            parameters.put("PRINTED_BY", printedBy != null ? printedBy : "Unknown");

            // Format date range string similar to Excel
            String dateRangeStr = "All Time";
            if (startDate != null && !startDate.isEmpty()) {
                try {
                    DateTimeFormatter inputFormatter = DateTimeFormatter.ofPattern("[dd-MMM-yyyy][yyyy-MM-dd]");
                    DateTimeFormatter outputFormatter = DateTimeFormatter.ofPattern("dd MMM yyyy");

                    java.time.LocalDate start = java.time.LocalDate.parse(startDate, inputFormatter);
                    String startStr = start.format(outputFormatter);

                    String endStr = "Now";
                    if (endDate != null && !endDate.isEmpty()) {
                        java.time.LocalDate end = java.time.LocalDate.parse(endDate, inputFormatter);
                        endStr = end.format(outputFormatter);
                    }
                    dateRangeStr = "From " + startStr + " to " + endStr;
                } catch (Exception e) {
                    dateRangeStr = startDate + " to " + (endDate != null ? endDate : "Now");
                }
            }
            parameters.put("DATE_RANGE", dateRangeStr);

            // Prepare data source
            DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("dd-MMM-yyyy hh:mm a");
            List<Map<String, Object>> listData = sales.stream().map(sale -> {
                Map<String, Object> map = new HashMap<>();
                map.put("saleNumber", sale.getSaleNumber());
                map.put("saleDate", sale.getSaleDate().format(dateFormatter));
                map.put("customerName", sale.getCustomerName() != null ? sale.getCustomerName()
                        : (sale.getCustomer() != null ? sale.getCustomer().getName() : "Walk-in"));
                map.put("itemCount", sale.getItems() != null ? sale.getItems().size() : 0);
                map.put("subtotal", sale.getGrossSubtotal());
                map.put("discountAmount", sale.getTotalDiscount());
                map.put("totalAmount", sale.getTotalAmount() != null ? sale.getTotalAmount() : BigDecimal.ZERO);
                map.put("paymentInfo", sale.getPaymentMethod() + " (" + sale.getPaymentStatus() + ")");
                map.put("saleStatus", sale.getSaleStatus());
                return map;
            }).collect(Collectors.toList());

            JRBeanCollectionDataSource dataSource = new JRBeanCollectionDataSource(listData);

            // Fill the report
            JasperPrint jasperPrint = JasperFillManager.fillReport(jasperReport, parameters, dataSource);

            // Export to PDF
            return JasperExportManager.exportReportToPdf(jasperPrint);

        } catch (Exception e) {
            log.error("Error generating sales list PDF", e);
            throw new RuntimeException("Failed to generate sales list PDF", e);
        }
    }

    /**
     * Generate PDF report for a sale
     */
    public byte[] generateSaleInvoicePdf(Sale sale) {
        try {
            // Load the JRXML template
            ClassPathResource resource = new ClassPathResource(REPORT_TEMPLATE);
            InputStream templateStream = resource.getInputStream();

            // Compile the report
            JasperReport jasperReport = JasperCompileManager.compileReport(templateStream);

            // Convert Sale entity to DTO
            SaleReportDTO reportData = convertToReportDTO(sale);

            // Load logo image
            InputStream logoStream = null;
            try {
                ClassPathResource logoResource = new ClassPathResource("static/logo-slogan.jpg");
                if (logoResource.exists()) {
                    logoStream = logoResource.getInputStream();
                }
            } catch (Exception e) {
                log.warn("Could not load logo image: {}", e.getMessage());
            }

            // Generate QR code
            InputStream qrCodeStream = null;
            try {
                String qrCodeContent = buildQRCodeContent(reportData);
                qrCodeStream = generateQRCode(qrCodeContent, 200, 200);
            } catch (Exception e) {
                log.warn("Could not generate QR code: {}", e.getMessage());
            }

            // Calculate total discount amount (sum of all item discounts + sale-level
            // discount)
            BigDecimal itemDiscounts = reportData.getItems().stream()
                    .map(item -> item.getDiscountAmount() != null ? item.getDiscountAmount() : BigDecimal.ZERO)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            BigDecimal saleLevelDiscount = reportData.getDiscountAmount() != null ? reportData.getDiscountAmount()
                    : BigDecimal.ZERO;

            BigDecimal totalDiscountAmount = itemDiscounts.add(saleLevelDiscount);

            // Calculate due amount for partial payments
            BigDecimal dueAmount = BigDecimal.ZERO;
            if ("PARTIAL".equals(reportData.getPaymentStatus()) &&
                    reportData.getTotalAmount() != null && reportData.getPaidAmount() != null) {
                dueAmount = reportData.getTotalAmount().subtract(
                        reportData.getPaidAmount() != null ? reportData.getPaidAmount() : BigDecimal.ZERO);
            }

            // Prepare parameters
            Map<String, Object> parameters = new HashMap<>();
            parameters.put("COMPANY_NAME", "RUPGANJ PHARMACY");
            parameters.put("COMPANY_ADDRESS", "892/1, Beside Shahidbagh Mosque, Rajarbagh, Dhaka - 1217");
            parameters.put("COMPANY_PHONE", "Mobile: 01966551343");
            parameters.put("COMPANY_EMAIL", "");

            // Add sale data as parameters
            parameters.put("SALE_NUMBER", reportData.getSaleNumber());
            parameters.put("SALE_DATE", reportData.getSaleDate());
            parameters.put("CUSTOMER_NAME", reportData.getCustomerName());
            parameters.put("CUSTOMER_PHONE", reportData.getCustomerPhone());
            parameters.put("CUSTOMER_EMAIL", reportData.getCustomerEmail());
            parameters.put("CUSTOMER_ADDRESS", reportData.getCustomerAddress());
            parameters.put("SUBTOTAL", reportData.getSubtotal());
            parameters.put("DISCOUNT_AMOUNT", reportData.getDiscountAmount());
            parameters.put("DISCOUNT_PERCENTAGE", reportData.getDiscountPercentage());
            parameters.put("TAX_AMOUNT", reportData.getTaxAmount());
            parameters.put("TOTAL_AMOUNT", reportData.getTotalAmount());
            parameters.put("PAID_AMOUNT", reportData.getPaidAmount());
            parameters.put("CHANGE_AMOUNT", reportData.getChangeAmount());
            parameters.put("PAYMENT_METHOD", reportData.getPaymentMethod());
            parameters.put("PAYMENT_STATUS", reportData.getPaymentStatus());
            parameters.put("SOLD_BY_NAME", reportData.getSoldByName());
            parameters.put("NOTES", reportData.getNotes());
            parameters.put("LOGO_IMAGE", logoStream);
            parameters.put("QR_CODE_IMAGE", qrCodeStream);
            parameters.put("TOTAL_DISCOUNT_AMOUNT", totalDiscountAmount);
            parameters.put("DUE_AMOUNT", dueAmount);

            // Prepare items data source
            List<Map<String, Object>> itemsData = reportData.getItems().stream()
                    .map(item -> {
                        Map<String, Object> itemMap = new HashMap<>();
                        itemMap.put("productName", item.getProductName());
                        itemMap.put("strength", item.getStrength() != null ? item.getStrength() : "");
                        itemMap.put("quantity", item.getQuantity());
                        itemMap.put("unitPrice", item.getUnitPrice());
                        itemMap.put("discountAmount", item.getDiscountAmount());
                        itemMap.put("totalPrice", item.getTotalPrice());
                        itemMap.put("batchNumber", item.getBatchNumber() != null ? item.getBatchNumber() : "");
                        itemMap.put("expiryDate", item.getExpiryDate() != null ? item.getExpiryDate() : "");
                        return itemMap;
                    })
                    .collect(Collectors.toList());

            JRBeanCollectionDataSource itemsDS = new JRBeanCollectionDataSource(itemsData);

            // Fill the report with data - items go in the detail band
            JasperPrint jasperPrint = JasperFillManager.fillReport(
                    jasperReport,
                    parameters,
                    itemsDS);

            // Export to PDF
            return JasperExportManager.exportReportToPdf(jasperPrint);

        } catch (Exception e) {
            log.error("Error generating sale invoice PDF", e);
            throw new RuntimeException("Failed to generate PDF report: " + e.getMessage(), e);
        }
    }

    /**
     * Convert Sale entity to SaleReportDTO
     */
    private SaleReportDTO convertToReportDTO(Sale sale) {
        DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");

        List<SaleItem> saleItems = sale.getItems() != null ? sale.getItems() : java.util.Collections.emptyList();
        List<SaleReportDTO.SaleItemReportDTO> items = saleItems.stream()
                .map(item -> SaleReportDTO.SaleItemReportDTO.builder()
                        .productName(item.getProductName())
                        .strength(item.getProduct() != null ? item.getProduct().getStrength() : null)
                        .quantity(item.getQuantity())
                        .unitPrice(item.getUnitPrice() != null ? item.getUnitPrice() : BigDecimal.ZERO)
                        .discountAmount(item.getDiscountAmount() != null ? item.getDiscountAmount() : BigDecimal.ZERO)
                        .totalPrice(item.getTotalPrice() != null ? item.getTotalPrice() : BigDecimal.ZERO)
                        .batchNumber(item.getBatchNumber())
                        .expiryDate(item.getExpiryDate() != null ? item.getExpiryDate().format(dateFormatter) : null)
                        .build())
                .collect(Collectors.toList());

        return SaleReportDTO.builder()
                .saleNumber(sale.getSaleNumber())
                .saleDate(sale.getSaleDate())
                .customerName(sale.getCustomerName() != null ? sale.getCustomerName()
                        : (sale.getCustomer() != null ? sale.getCustomer().getName() : "Walk-in Customer"))
                .customerPhone(sale.getCustomerPhone() != null ? sale.getCustomerPhone()
                        : (sale.getCustomer() != null ? sale.getCustomer().getPhone() : null))
                .customerEmail(sale.getCustomer() != null ? sale.getCustomer().getEmail() : null)
                .customerAddress(sale.getCustomer() != null ? sale.getCustomer().getAddress() : null)
                .subtotal(sale.getGrossSubtotal())
                .discountAmount(sale.getDiscountAmount() != null ? sale.getDiscountAmount() : BigDecimal.ZERO)
                .discountPercentage(
                        sale.getDiscountPercentage() != null ? sale.getDiscountPercentage() : BigDecimal.ZERO)
                .taxAmount(sale.getTaxAmount() != null ? sale.getTaxAmount() : BigDecimal.ZERO)
                .totalAmount(sale.getTotalAmount() != null ? sale.getTotalAmount() : BigDecimal.ZERO)
                .paidAmount(sale.getPaidAmount() != null ? sale.getPaidAmount() : BigDecimal.ZERO)
                .changeAmount(sale.getChangeAmount() != null ? sale.getChangeAmount() : BigDecimal.ZERO)
                .paymentMethod(sale.getPaymentMethod())
                .paymentStatus(sale.getPaymentStatus())
                .soldByName(sale.getSoldBy() != null ? sale.getSoldBy().getName() : null)
                .notes(sale.getNotes())
                .items(items)
                .build();
    }

    /**
     * Build QR code content string from sale data
     */
    private String buildQRCodeContent(SaleReportDTO reportData) {
        StringBuilder sb = new StringBuilder();
        sb.append("Invoice: ").append(reportData.getSaleNumber()).append("\n");
        if (reportData.getSaleDate() != null) {
            sb.append("Date: ").append(reportData.getSaleDate()
                    .format(DateTimeFormatter.ofPattern("d MMMM yyyy"))).append("\n");
        }
        sb.append("Customer: ")
                .append(reportData.getCustomerName() != null ? reportData.getCustomerName() : "Walk-in Customer")
                .append("\n");
        if (reportData.getTotalAmount() != null) {
            sb.append("Total: ৳").append(reportData.getTotalAmount().toString()).append("\n");
        }
        if (reportData.getPaymentStatus() != null) {
            sb.append("Status: ").append(reportData.getPaymentStatus());
        }
        return sb.toString();
    }

    /**
     * Generate QR code image as InputStream
     */
    private InputStream generateQRCode(String content, int width, int height) throws WriterException, IOException {
        Map<EncodeHintType, Object> hints = new HashMap<>();
        hints.put(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.H);
        hints.put(EncodeHintType.CHARACTER_SET, "UTF-8");
        hints.put(EncodeHintType.MARGIN, 1);

        QRCodeWriter qrCodeWriter = new QRCodeWriter();
        BitMatrix bitMatrix = qrCodeWriter.encode(content, BarcodeFormat.QR_CODE, width, height, hints);

        BufferedImage qrImage = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        qrImage.createGraphics();

        Graphics2D graphics = (Graphics2D) qrImage.getGraphics();
        graphics.setColor(Color.WHITE);
        graphics.fillRect(0, 0, width, height);
        graphics.setColor(Color.BLACK);

        for (int i = 0; i < width; i++) {
            for (int j = 0; j < height; j++) {
                if (bitMatrix.get(i, j)) {
                    graphics.fillRect(i, j, 1, 1);
                }
            }
        }

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(qrImage, "PNG", baos);
        return new ByteArrayInputStream(baos.toByteArray());
    }

    /**
     * Generate Excel report for sales list
     */
    public byte[] generateSalesExcel(List<Sale> sales, String search, String startDate, String endDate,
            String printedBy) {
        try (org.apache.poi.xssf.usermodel.XSSFWorkbook workbook = new org.apache.poi.xssf.usermodel.XSSFWorkbook()) {
            org.apache.poi.xssf.usermodel.XSSFSheet sheet = workbook.createSheet("Sales Report");

            // Create styles
            org.apache.poi.ss.usermodel.CellStyle headerStyle = workbook.createCellStyle();
            headerStyle.setFillForegroundColor(org.apache.poi.ss.usermodel.IndexedColors.ROYAL_BLUE.getIndex());
            headerStyle.setFillPattern(org.apache.poi.ss.usermodel.FillPatternType.SOLID_FOREGROUND);
            org.apache.poi.ss.usermodel.Font headerFont = workbook.createFont();
            headerFont.setColor(org.apache.poi.ss.usermodel.IndexedColors.WHITE.getIndex());
            headerFont.setBold(true);
            headerStyle.setFont(headerFont);
            headerStyle.setAlignment(org.apache.poi.ss.usermodel.HorizontalAlignment.CENTER);
            headerStyle.setVerticalAlignment(org.apache.poi.ss.usermodel.VerticalAlignment.CENTER);
            headerStyle.setBorderBottom(org.apache.poi.ss.usermodel.BorderStyle.THIN);
            headerStyle.setBorderTop(org.apache.poi.ss.usermodel.BorderStyle.THIN);
            headerStyle.setBorderLeft(org.apache.poi.ss.usermodel.BorderStyle.THIN);
            headerStyle.setBorderRight(org.apache.poi.ss.usermodel.BorderStyle.THIN);

            org.apache.poi.ss.usermodel.CellStyle titleStyle = workbook.createCellStyle();
            org.apache.poi.ss.usermodel.Font titleFont = workbook.createFont();
            titleFont.setBold(true);
            titleFont.setFontHeightInPoints((short) 16);
            titleStyle.setFont(titleFont);
            titleStyle.setAlignment(org.apache.poi.ss.usermodel.HorizontalAlignment.CENTER);

            org.apache.poi.ss.usermodel.CellStyle subTitleStyle = workbook.createCellStyle();
            org.apache.poi.ss.usermodel.Font subTitleFont = workbook.createFont();
            subTitleFont.setFontHeightInPoints((short) 10);
            subTitleFont.setColor(org.apache.poi.ss.usermodel.IndexedColors.GREY_50_PERCENT.getIndex());
            subTitleStyle.setFont(subTitleFont);
            subTitleStyle.setAlignment(org.apache.poi.ss.usermodel.HorizontalAlignment.CENTER);

            org.apache.poi.ss.usermodel.CellStyle currencyStyle = workbook.createCellStyle();
            currencyStyle.setDataFormat(workbook.createDataFormat().getFormat("#,##0.00"));

            org.apache.poi.ss.usermodel.CellStyle dateStyle = workbook.createCellStyle();
            dateStyle.setDataFormat(workbook.createDataFormat().getFormat("dd MMMM, yyyy h:mm AM/PM"));

            // Company Info
            int rowNum = 0;
            org.apache.poi.ss.usermodel.Row row = sheet.createRow(rowNum++);
            org.apache.poi.ss.usermodel.Cell cell = row.createCell(0);
            cell.setCellValue("RUPGANJ PHARMACY");
            cell.setCellStyle(titleStyle);
            sheet.addMergedRegion(new org.apache.poi.ss.util.CellRangeAddress(0, 0, 0, 8));

            row = sheet.createRow(rowNum++);
            cell = row.createCell(0);
            cell.setCellValue("892/1, Beside Shahidbagh Mosque, Rajarbagh, Dhaka - 1217");
            cell.setCellStyle(subTitleStyle);
            sheet.addMergedRegion(new org.apache.poi.ss.util.CellRangeAddress(1, 1, 0, 8));

            row = sheet.createRow(rowNum++);
            cell = row.createCell(0);
            cell.setCellValue("Sales Report");
            org.apache.poi.ss.usermodel.CellStyle reportTitleStyle = workbook.createCellStyle();
            org.apache.poi.ss.usermodel.Font reportTitleFont = workbook.createFont();
            reportTitleFont.setBold(true);
            reportTitleFont.setFontHeightInPoints((short) 14);
            reportTitleFont.setUnderline(org.apache.poi.ss.usermodel.Font.U_SINGLE);
            reportTitleStyle.setFont(reportTitleFont);
            reportTitleStyle.setAlignment(org.apache.poi.ss.usermodel.HorizontalAlignment.CENTER);
            cell.setCellStyle(reportTitleStyle);
            sheet.addMergedRegion(new org.apache.poi.ss.util.CellRangeAddress(2, 2, 0, 8));

            rowNum++; // Empty row

            // Filter Info
            row = sheet.createRow(rowNum++);
            row.createCell(0).setCellValue("Generated Date:");
            row.createCell(1).setCellValue(
                    java.time.LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd-MMM-yyyy hh:mm a")));

            // Printed By
            if (printedBy != null && !printedBy.isEmpty()) {
                row = sheet.createRow(rowNum++);
                row.createCell(0).setCellValue("Printed By:");
                row.createCell(1).setCellValue(printedBy);
            }

            // Date Range
            row = sheet.createRow(rowNum++);
            row.createCell(0).setCellValue("Date Range:");
            if (startDate != null && !startDate.isEmpty()) {
                try {
                    java.time.format.DateTimeFormatter inputFormatter = java.time.format.DateTimeFormatter
                            .ofPattern("[dd-MMM-yyyy][yyyy-MM-dd]");
                    java.time.format.DateTimeFormatter outputFormatter = java.time.format.DateTimeFormatter
                            .ofPattern("dd MMM yyyy");

                    java.time.LocalDate start = java.time.LocalDate.parse(startDate, inputFormatter);
                    String startStr = start.format(outputFormatter);

                    String endStr = "Now";
                    if (endDate != null && !endDate.isEmpty()) {
                        java.time.LocalDate end = java.time.LocalDate.parse(endDate, inputFormatter);
                        endStr = end.format(outputFormatter);
                    }

                    row.createCell(1).setCellValue("From: " + startStr + "      To: " + endStr);
                } catch (Exception e) {
                    // Fallback if parsing fails
                    row.createCell(1).setCellValue(startDate + " To: " + (endDate != null ? endDate : "Now"));
                }
            } else {
                row.createCell(1).setCellValue("All Time");
            }

            if (search != null && !search.isEmpty()) {
                row = sheet.createRow(rowNum++);
                row.createCell(0).setCellValue("Search By:");
                row.createCell(1).setCellValue(search);
            }

            rowNum++; // Space before headers

            // Headers
            String[] headers = { "Sale #", "Date & Time", "Customer", "Items", "Subtotal", "Discount", "Total",
                    "Payment", "Status" };
            row = sheet.createRow(rowNum++);
            for (int i = 0; i < headers.length; i++) {
                cell = row.createCell(i);
                cell.setCellValue(headers[i]);
                cell.setCellStyle(headerStyle);
            }

            // Data
            for (Sale sale : sales) {
                row = sheet.createRow(rowNum++);

                row.createCell(0).setCellValue(sale.getSaleNumber());

                cell = row.createCell(1);
                cell.setCellValue(
                        java.time.LocalDateTime.of(sale.getSaleDate().toLocalDate(), sale.getSaleDate().toLocalTime()));
                cell.setCellStyle(dateStyle);

                row.createCell(2).setCellValue(sale.getCustomerName() != null ? sale.getCustomerName()
                        : (sale.getCustomer() != null ? sale.getCustomer().getName() : "Walk-in"));

                row.createCell(3).setCellValue(sale.getItems() != null ? sale.getItems().size() : 0);

                cell = row.createCell(4);
                cell.setCellValue(sale.getGrossSubtotal().doubleValue());
                cell.setCellStyle(currencyStyle);

                cell = row.createCell(5);
                cell.setCellValue(sale.getTotalDiscount().doubleValue());
                cell.setCellStyle(currencyStyle);

                cell = row.createCell(6);
                cell.setCellValue(sale.getTotalAmount() != null ? sale.getTotalAmount().doubleValue() : 0.0);
                cell.setCellStyle(currencyStyle);

                row.createCell(7).setCellValue(sale.getPaymentMethod() + " (" + sale.getPaymentStatus() + ")");
                row.createCell(8).setCellValue(sale.getSaleStatus());
            }

            // Autosize columns
            for (int i = 0; i < headers.length; i++) {
                sheet.autoSizeColumn(i);
            }

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            workbook.write(baos);
            return baos.toByteArray();
        } catch (Exception e) {
            log.error("Error generating Excel report", e);
            throw new RuntimeException("Failed to generate Excel report", e);
        }
    }
}
