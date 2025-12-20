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
                ClassPathResource logoResource = new ClassPathResource("static/logo.jpg");
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
            
            // Calculate total discount amount (sum of all item discounts + sale-level discount)
            BigDecimal itemDiscounts = reportData.getItems().stream()
                    .map(item -> item.getDiscountAmount() != null ? item.getDiscountAmount() : BigDecimal.ZERO)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            
            BigDecimal saleLevelDiscount = reportData.getDiscountAmount() != null ? 
                    reportData.getDiscountAmount() : BigDecimal.ZERO;
            
            BigDecimal totalDiscountAmount = itemDiscounts.add(saleLevelDiscount);
            
            // Calculate due amount for partial payments
            BigDecimal dueAmount = BigDecimal.ZERO;
            if ("PARTIAL".equals(reportData.getPaymentStatus()) && 
                reportData.getTotalAmount() != null && reportData.getPaidAmount() != null) {
                dueAmount = reportData.getTotalAmount().subtract(
                    reportData.getPaidAmount() != null ? reportData.getPaidAmount() : BigDecimal.ZERO
                );
            }
            
            // Prepare parameters
            Map<String, Object> parameters = new HashMap<>();
            parameters.put("COMPANY_NAME", "Rupganj Pharmacy");
            parameters.put("COMPANY_ADDRESS", "892/1, Beside Shahidbagh Mosque, Rajarbagh, Dhaka - 1217");
            parameters.put("COMPANY_PHONE", "01966551343");
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
                    itemsDS
            );
            
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
                .customerName(sale.getCustomerName() != null ? sale.getCustomerName() : 
                             (sale.getCustomer() != null ? sale.getCustomer().getName() : "Walk-in Customer"))
                .customerPhone(sale.getCustomerPhone() != null ? sale.getCustomerPhone() : 
                              (sale.getCustomer() != null ? sale.getCustomer().getPhone() : null))
                .customerEmail(sale.getCustomer() != null ? sale.getCustomer().getEmail() : null)
                .customerAddress(sale.getCustomer() != null ? sale.getCustomer().getAddress() : null)
                .subtotal(sale.getSubtotal() != null ? sale.getSubtotal() : BigDecimal.ZERO)
                .discountAmount(sale.getDiscountAmount() != null ? sale.getDiscountAmount() : BigDecimal.ZERO)
                .discountPercentage(sale.getDiscountPercentage() != null ? sale.getDiscountPercentage() : BigDecimal.ZERO)
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
        sb.append("Customer: ").append(reportData.getCustomerName() != null ? 
                reportData.getCustomerName() : "Walk-in Customer").append("\n");
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
}

