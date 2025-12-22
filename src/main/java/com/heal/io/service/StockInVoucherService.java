package com.heal.io.service;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
import com.heal.io.dto.StockInVoucherDTO;
import com.heal.io.entity.StockIn;
import com.heal.io.entity.StockInItem;
import com.heal.io.util.NumberToWordConverter;
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
public class StockInVoucherService {

    private static final String REPORT_TEMPLATE = "reports/stock-in-voucher.jrxml";

    /**
     * Generate PDF voucher for a stock-in
     */
    public byte[] generateStockInVoucherPdf(StockIn stockIn) {
        try {
            // Load the JRXML template
            ClassPathResource resource = new ClassPathResource(REPORT_TEMPLATE);
            InputStream templateStream = resource.getInputStream();

            // Compile the report
            JasperReport jasperReport = JasperCompileManager.compileReport(templateStream);

            // Convert StockIn entity to DTO
            StockInVoucherDTO reportData = convertToVoucherDTO(stockIn);

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

            // Calculate due amount for partial payments
            BigDecimal dueAmount = BigDecimal.ZERO;
            if ("PARTIAL".equals(reportData.getPaymentStatus()) &&
                    reportData.getFinalAmount() != null && reportData.getPaidAmount() != null) {
                dueAmount = reportData.getFinalAmount().subtract(
                        reportData.getPaidAmount() != null ? reportData.getPaidAmount() : BigDecimal.ZERO);
            }

            // Prepare parameters
            Map<String, Object> parameters = new HashMap<>();
            parameters.put("COMPANY_NAME", "Rupganj Pharmacy");
            parameters.put("COMPANY_ADDRESS", "892/1, Beside Shahidbagh Mosque, Rajarbagh, Dhaka - 1217");
            parameters.put("COMPANY_PHONE", "01966551343");
            parameters.put("COMPANY_EMAIL", "");

            // Add stock-in data as parameters
            parameters.put("STOCK_IN_NUMBER", reportData.getStockInNumber());
            parameters.put("STOCK_IN_DATE", reportData.getStockInDate());
            parameters.put("RECEIVED_DATE", reportData.getReceivedDate());
            parameters.put("SUPPLIER_NAME", reportData.getSupplierName());
            parameters.put("MANUFACTURER_NAMES", reportData.getManufacturerNames());
            parameters.put("SUPPLIER_PHONE", reportData.getSupplierPhone());
            parameters.put("SUPPLIER_EMAIL", reportData.getSupplierEmail());
            parameters.put("SUPPLIER_ADDRESS", reportData.getSupplierAddress());
            parameters.put("TOTAL_AMOUNT", reportData.getTotalAmount());
            parameters.put("DISCOUNT_AMOUNT", reportData.getDiscountAmount());
            parameters.put("TAX_AMOUNT", reportData.getTaxAmount());
            parameters.put("FINAL_AMOUNT", reportData.getFinalAmount());
            parameters.put("PAID_AMOUNT", reportData.getPaidAmount());
            parameters.put("PAYMENT_METHOD", reportData.getPaymentMethod());
            parameters.put("PAYMENT_STATUS", reportData.getPaymentStatus());
            parameters.put("RECEIVED_BY_NAME", reportData.getReceivedByName());
            parameters.put("INSERTED_BY_NAME", reportData.getInsertedByName());
            parameters.put("NOTES", reportData.getNotes());
            parameters.put("LOGO_IMAGE", logoStream);
            parameters.put("QR_CODE_IMAGE", qrCodeStream);
            parameters.put("DUE_AMOUNT", dueAmount);
            parameters.put("AMOUNT_IN_WORDS", NumberToWordConverter.convertAmount(reportData.getFinalAmount()));

            // Prepare items data source
            List<Map<String, Object>> itemsData = reportData.getItems().stream()
                    .map(item -> {
                        Map<String, Object> itemMap = new HashMap<>();
                        itemMap.put("productName", item.getProductName());
                        itemMap.put("packageDescription",
                                item.getPackageDescription() != null ? item.getPackageDescription() : "");
                        itemMap.put("strength", item.getStrength() != null ? item.getStrength() : "");
                        itemMap.put("quantity", item.getQuantity());
                        itemMap.put("unitCost", item.getUnitCost());
                        itemMap.put("totalCost", item.getTotalCost());
                        itemMap.put("sellingPrice", item.getSellingPrice());
                        itemMap.put("batchNumber", item.getBatchNumber() != null ? item.getBatchNumber() : "");
                        itemMap.put("expiryDate", item.getExpiryDate() != null ? item.getExpiryDate() : "");
                        itemMap.put("location", item.getLocation() != null ? item.getLocation() : "");
                        return itemMap;
                    })
                    .collect(Collectors.toList());

            JRBeanCollectionDataSource itemsDS = new JRBeanCollectionDataSource(itemsData);

            // Fill the report with data
            JasperPrint jasperPrint = JasperFillManager.fillReport(
                    jasperReport,
                    parameters,
                    itemsDS);

            // Export to PDF
            return JasperExportManager.exportReportToPdf(jasperPrint);

        } catch (Exception e) {
            log.error("Error generating stock-in voucher PDF", e);
            throw new RuntimeException("Failed to generate PDF voucher: " + e.getMessage(), e);
        }
    }

    /**
     * Convert StockIn entity to StockInVoucherDTO
     */
    private StockInVoucherDTO convertToVoucherDTO(StockIn stockIn) {
        DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");

        List<StockInItem> stockInItems = stockIn.getItems() != null ? stockIn.getItems()
                : java.util.Collections.emptyList();
        List<StockInVoucherDTO.StockInItemVoucherDTO> items = stockInItems.stream()
                .map(item -> StockInVoucherDTO.StockInItemVoucherDTO.builder()
                        .productName(item.getProduct() != null ? item.getProduct().getName() : "")
                        .packageDescription(
                                item.getProductPackage() != null ? item.getProductPackage().getPackageDescription()
                                        : null)
                        .strength(item.getProduct() != null ? item.getProduct().getStrength() : null)
                        .quantity(item.getQuantity())
                        .unitCost(item.getUnitCost() != null ? item.getUnitCost() : BigDecimal.ZERO)
                        .totalCost(item.getTotalCost() != null ? item.getTotalCost() : BigDecimal.ZERO)
                        .sellingPrice(item.getSellingPrice())
                        .batchNumber(item.getBatchNumber())
                        .expiryDate(item.getExpiryDate() != null ? item.getExpiryDate().format(dateFormatter) : null)
                        .location(item.getLocation())
                        .build())
                .collect(Collectors.toList());

        // Extract manufacturer names from supplier
        String manufacturerNames = null;
        if (stockIn.getSupplier() != null) {
            // Initialize manufacturers collection to avoid lazy loading issues
            if (stockIn.getSupplier().getManufacturers() != null) {
                stockIn.getSupplier().getManufacturers().size(); // Force initialization
                if (!stockIn.getSupplier().getManufacturers().isEmpty()) {
                    manufacturerNames = stockIn.getSupplier().getManufacturers().stream()
                            .map(manufacturer -> manufacturer.getName())
                            .collect(Collectors.joining(", "));
                }
            }
        }

        return StockInVoucherDTO.builder()
                .stockInNumber(stockIn.getStockInNumber())
                .stockInDate(stockIn.getStockInDate())
                .receivedDate(stockIn.getReceivedDate())
                .supplierName(stockIn.getSupplier() != null ? stockIn.getSupplier().getName() : "N/A")
                .manufacturerNames(manufacturerNames)
                .supplierPhone(stockIn.getSupplier() != null ? stockIn.getSupplier().getPhone() : null)
                .supplierEmail(stockIn.getSupplier() != null ? stockIn.getSupplier().getEmail() : null)
                .supplierAddress(stockIn.getSupplier() != null ? stockIn.getSupplier().getAddress() : null)
                .totalAmount(stockIn.getTotalAmount() != null ? stockIn.getTotalAmount() : BigDecimal.ZERO)
                .discountAmount(stockIn.getDiscountAmount() != null ? stockIn.getDiscountAmount() : BigDecimal.ZERO)
                .taxAmount(stockIn.getTaxAmount() != null ? stockIn.getTaxAmount() : BigDecimal.ZERO)
                .finalAmount(stockIn.getFinalAmount() != null ? stockIn.getFinalAmount() : BigDecimal.ZERO)
                .paidAmount(stockIn.getPaidAmount())
                .paymentMethod(stockIn.getPaymentMethod())
                .paymentStatus(stockIn.getPaymentStatus())
                .receivedByName(stockIn.getReceivedBy() != null ? stockIn.getReceivedBy().getName() : null)
                .insertedByName(stockIn.getInsertedBy() != null ? stockIn.getInsertedBy().getName() : null)
                .notes(stockIn.getNotes())
                .items(items)
                .build();
    }

    /**
     * Build QR code content string from stock-in data
     */
    private String buildQRCodeContent(StockInVoucherDTO reportData) {
        StringBuilder sb = new StringBuilder();
        sb.append("Stock In Voucher: ").append(reportData.getStockInNumber()).append("\n");
        if (reportData.getStockInDate() != null) {
            sb.append("Date: ").append(reportData.getStockInDate()
                    .format(DateTimeFormatter.ofPattern("d MMMM yyyy"))).append("\n");
        }
        sb.append("Supplier: ").append(reportData.getSupplierName()).append("\n");
        if (reportData.getFinalAmount() != null) {
            sb.append("Total: ৳").append(reportData.getFinalAmount().toString()).append("\n");
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
}
