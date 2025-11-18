package com.heal.io.service;

import com.heal.io.entity.*;
import com.heal.io.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
@Slf4j
public class MedicineCsvImportService {

    private final ProductRepository productRepository;
    private final ProductCategoryRepository categoryRepository;
    private final ManufacturerRepository manufacturerRepository;
    private final DosageFormRepository dosageFormRepository;
    private final GenericRepository genericRepository;
    private final MedicineTypeRepository medicineTypeRepository;
    private final ProductPackageRepository productPackageRepository;

    @Transactional
    public ImportResult importMedicines(MultipartFile file) {
        ImportResult result = new ImportResult();
        
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8))) {
            
            String line = reader.readLine(); // Skip header
            if (line == null) {
                result.setError("CSV file is empty");
                return result;
            }
            
            // Ensure Medicine category exists
            ProductCategory medicineCategory = categoryRepository.findByName("Medicine")
                    .orElseGet(() -> {
                        ProductCategory cat = ProductCategory.builder()
                                .name("Medicine")
                                .description("Pharmaceutical medicines")
                                .build();
                        return categoryRepository.save(cat);
                    });
            
            int lineNumber = 1;
            Set<String> processedBrandIds = new HashSet<>();
            
            while ((line = reader.readLine()) != null) {
                lineNumber++;
                try {
                    String[] values = parseCsvLine(line);
                    
                    if (values.length < 9) {
                        result.addSkipped("Line " + lineNumber + ": Insufficient columns");
                        continue;
                    }
                    
                    String brandIdStr = values[0].trim();
                    if (brandIdStr.isEmpty() || processedBrandIds.contains(brandIdStr)) {
                        continue; // Skip duplicates
                    }
                    processedBrandIds.add(brandIdStr);
                    
                    Long brandId = Long.parseLong(brandIdStr);
                    String brandName = values[1].trim();
                    String type = values[2].trim();
                    String slug = values[3].trim();
                    String dosageFormName = values[4].trim();
                    String genericName = values[5].trim();
                    String strength = values[6].trim();
                    String manufacturerName = values[7].trim();
                    String packageContainer = values.length > 8 ? values[8].trim() : "";
                    String packageSize = values.length > 9 ? values[9].trim() : "";
                    
                    // Get or create Medicine Type
                    MedicineType medicineType = null;
                    if (!type.isEmpty()) {
                        medicineType = medicineTypeRepository.findByName(type)
                                .orElseGet(() -> {
                                    MedicineType mt = MedicineType.builder()
                                            .name(type)
                                            .build();
                                    return medicineTypeRepository.save(mt);
                                });
                    }
                    
                    // Get or create Manufacturer
                    Manufacturer manufacturer = null;
                    if (!manufacturerName.isEmpty()) {
                        manufacturer = manufacturerRepository.findByName(manufacturerName)
                                .orElseGet(() -> {
                                    Manufacturer mfg = Manufacturer.builder()
                                            .name(manufacturerName)
                                            .build();
                                    return manufacturerRepository.save(mfg);
                                });
                    }
                    
                    // Get or create Dosage Form
                    DosageForm dosageForm = null;
                    if (!dosageFormName.isEmpty()) {
                        dosageForm = dosageFormRepository.findByName(dosageFormName)
                                .orElseGet(() -> {
                                    DosageForm df = DosageForm.builder()
                                            .name(dosageFormName)
                                            .slug(slug)
                                            .build();
                                    return dosageFormRepository.save(df);
                                });
                    }
                    
                    // Get or create Generic
                    Generic generic = null;
                    if (!genericName.isEmpty()) {
                        generic = genericRepository.findByName(genericName)
                                .orElseGet(() -> {
                                    Generic gen = Generic.builder()
                                            .name(genericName)
                                            .build();
                                    return genericRepository.save(gen);
                                });
                    }
                    
                    // Create or update Product
                    Product product = productRepository.findByBrandId(brandId)
                            .orElse(Product.builder()
                                    .brandId(brandId)
                                    .name(brandName)
                                    .slug(slug)
                                    .productCategory(medicineCategory)
                                    .medicineType(medicineType)
                                    .manufacturer(manufacturer)
                                    .dosageForm(dosageForm)
                                    .generic(generic)
                                    .strength(strength.isEmpty() ? null : strength)
                                    .requiresPrescription(false) // Default: prescription not needed
                                    .build());
                    
                    product.setName(brandName);
                    product.setSlug(slug);
                    product.setProductCategory(medicineCategory);
                    product.setMedicineType(medicineType);
                    product.setManufacturer(manufacturer);
                    product.setDosageForm(dosageForm);
                    product.setGeneric(generic);
                    product.setStrength(strength.isEmpty() ? null : strength);
                    // Ensure requiresPrescription is set (default to false if null)
                    if (product.getRequiresPrescription() == null) {
                        product.setRequiresPrescription(false);
                    }
                    
                    product = productRepository.save(product);
                    result.incrementImported();
                    
                    // Parse and create Product Packages
                    if (!packageContainer.isEmpty()) {
                        List<PackageInfo> packages = parsePackageInfo(packageContainer, packageSize, dosageFormName);
                        for (PackageInfo pkgInfo : packages) {
                            ProductPackage productPackage = ProductPackage.builder()
                                    .product(product)
                                    .packageDescription(pkgInfo.description)
                                    .packageSize(pkgInfo.size)
                                    .unitPrice(pkgInfo.unitPrice)
                                    .packagePrice(pkgInfo.packagePrice)
                                    .quantityPerPackage(pkgInfo.quantity)
                                    .unitOfMeasure(pkgInfo.unit)
                                    .isDefault(packages.size() == 1)
                                    .build();
                            productPackageRepository.save(productPackage);
                        }
                    }
                    
                } catch (Exception e) {
                    log.error("Error processing line {}: {}", lineNumber, e.getMessage());
                    result.addSkipped("Line " + lineNumber + ": " + e.getMessage());
                }
            }
            
            result.setSuccess(true);
            result.setMessage("Successfully imported " + result.getImported() + " products");
            
        } catch (Exception e) {
            log.error("Error importing CSV: {}", e.getMessage(), e);
            result.setError("Error importing CSV: " + e.getMessage());
        }
        
        return result;
    }
    
    private String[] parseCsvLine(String line) {
        List<String> values = new ArrayList<>();
        boolean inQuotes = false;
        StringBuilder current = new StringBuilder();
        
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '"') {
                inQuotes = !inQuotes;
            } else if (c == ',' && !inQuotes) {
                values.add(current.toString());
                current = new StringBuilder();
            } else {
                current.append(c);
            }
        }
        values.add(current.toString());
        
        return values.toArray(new String[0]);
    }
    
    private List<PackageInfo> parsePackageInfo(String packageContainer, String packageSize, String dosageForm) {
        List<PackageInfo> packages = new ArrayList<>();
        Set<String> seenDescriptions = new HashSet<>(); // To avoid duplicates
        
        if (packageContainer == null || packageContainer.trim().isEmpty()) {
            return packages;
        }
        
        // Clean up the package container string - remove extra commas and parentheses
        String cleaned = packageContainer.replaceAll(",\\s*,+", ",").trim();
        
        // Pattern 1: "Unit Price: ৳ X.XX" - explicit unit price entry
        Pattern unitPricePattern = Pattern.compile("Unit\\s+Price:\\s*৳\\s*([\\d,]+(?:\\.\\d+)?)", Pattern.CASE_INSENSITIVE);
        Matcher unitPriceMatcher = unitPricePattern.matcher(cleaned);
        
        BigDecimal unitPriceValue = null;
        while (unitPriceMatcher.find()) {
            String priceStr = unitPriceMatcher.group(1).replace(",", "");
            try {
                unitPriceValue = new BigDecimal(priceStr);
                // Create a unit price entry with quantity = 1
                String description = "Unit Price";
                if (!seenDescriptions.contains(description)) {
                    PackageInfo pkgInfo = new PackageInfo();
                    pkgInfo.description = description;
                    pkgInfo.size = packageSize;
                    pkgInfo.unitPrice = unitPriceValue;
                    pkgInfo.packagePrice = unitPriceValue;
                    pkgInfo.quantity = 1;
                    pkgInfo.unit = "pieces";
                    packages.add(pkgInfo);
                    seenDescriptions.add(description);
                }
            } catch (Exception e) {
                log.warn("Could not parse unit price: {}", priceStr);
            }
        }
        
        // Pattern 2: "(N's pack: ৳ X.XX)" - pack entries
        Pattern packPattern = Pattern.compile("\\(\\s*(\\d+)\\s*['']?s?\\s*pack:\\s*৳\\s*([\\d,]+(?:\\.\\d+)?)\\s*\\)", Pattern.CASE_INSENSITIVE);
        Matcher packMatcher = packPattern.matcher(cleaned);
        
        while (packMatcher.find()) {
            int quantity = Integer.parseInt(packMatcher.group(1));
            String priceStr = packMatcher.group(2).replace(",", "");
            
            try {
                BigDecimal packagePrice = new BigDecimal(priceStr);
                BigDecimal calculatedUnitPrice = unitPriceValue != null ? unitPriceValue : 
                    packagePrice.divide(new BigDecimal(quantity), 2, RoundingMode.HALF_UP);
                
                String description = quantity + "'s pack";
                if (!seenDescriptions.contains(description)) {
                    PackageInfo pkgInfo = new PackageInfo();
                    pkgInfo.description = description;
                    pkgInfo.size = packageSize;
                    pkgInfo.unitPrice = calculatedUnitPrice;
                    pkgInfo.packagePrice = packagePrice;
                    pkgInfo.quantity = quantity;
                    pkgInfo.unit = "pack";
                    packages.add(pkgInfo);
                    seenDescriptions.add(description);
                }
            } catch (Exception e) {
                log.warn("Could not parse pack price: {}", priceStr);
            }
        }
        
        // Pattern 3: Volume/weight based pricing - "N ml bottle: ৳ X.XX" or "N gm container: ৳ X.XX"
        Pattern volumePattern = Pattern.compile("(\\d+(?:\\.\\d+)?)\\s*(ml|gm|g|mg)\\s+(?:bottle|container|vial|tube|sachet|drop|sachet):\\s*৳\\s*([\\d,]+(?:\\.\\d+)?)", Pattern.CASE_INSENSITIVE);
        Matcher volumeMatcher = volumePattern.matcher(cleaned);
        
        while (volumeMatcher.find()) {
            String quantityStr = volumeMatcher.group(1);
            String unitType = volumeMatcher.group(2).toLowerCase();
            String priceStr = volumeMatcher.group(3).replace(",", "");
            
            try {
                BigDecimal quantity = new BigDecimal(quantityStr);
                BigDecimal packagePrice = new BigDecimal(priceStr);
                // For volume-based items (syrups, liquids), unit price = package price / quantity
                // This gives price per ml/gm, which is correct for liquid forms
                BigDecimal calculatedUnitPrice = packagePrice.divide(quantity, 4, RoundingMode.HALF_UP);
                
                String unit = unitType.equals("ml") ? "ml" : 
                             (unitType.equals("gm") || unitType.equals("g")) ? "gm" : "mg";
                String containerType = cleaned.contains("bottle") ? "bottle" :
                                      cleaned.contains("container") ? "container" :
                                      cleaned.contains("vial") ? "vial" :
                                      cleaned.contains("tube") ? "tube" :
                                      cleaned.contains("sachet") ? "sachet" :
                                      cleaned.contains("drop") ? "drop" : "unit";
                
                String description = quantityStr + " " + unit + " " + containerType;
                if (!seenDescriptions.contains(description)) {
                    PackageInfo pkgInfo = new PackageInfo();
                    pkgInfo.description = description;
                    pkgInfo.size = packageSize;
                    pkgInfo.unitPrice = calculatedUnitPrice;
                    pkgInfo.packagePrice = packagePrice;
                    pkgInfo.quantity = quantity.intValue();
                    pkgInfo.unit = unit;
                    packages.add(pkgInfo);
                    seenDescriptions.add(description);
                }
            } catch (Exception e) {
                log.warn("Could not parse volume price: {}", priceStr);
            }
        }
        
        // Pattern 4: Generic pattern for other formats - "description: ৳ X.XX"
        // Only if we haven't matched anything yet
        if (packages.isEmpty()) {
            Pattern genericPattern = Pattern.compile("([^:]+?):\\s*৳\\s*([\\d,]+(?:\\.\\d+)?)");
            Matcher genericMatcher = genericPattern.matcher(cleaned);
            
            while (genericMatcher.find()) {
                String description = genericMatcher.group(1).trim();
                String priceStr = genericMatcher.group(2).replace(",", "");
                
                // Skip if it's already been processed by other patterns
                if (description.toLowerCase().contains("unit price") || 
                    description.toLowerCase().contains("pack") ||
                    description.matches(".*\\d+\\s*(ml|gm|g|mg).*")) {
                    continue;
                }
                
                try {
                    BigDecimal price = new BigDecimal(priceStr);
                    Integer quantity = extractQuantity(description);
                    String unit = extractUnit(description);
                    
                    // If quantity is null, assume it's a single unit
                    if (quantity == null) {
                        quantity = 1;
                    }
                    
                    BigDecimal calculatedUnitPrice = quantity > 1 ? 
                        price.divide(new BigDecimal(quantity), 4, RoundingMode.HALF_UP) : price;
                    
                    if (!seenDescriptions.contains(description)) {
                        PackageInfo pkgInfo = new PackageInfo();
                        pkgInfo.description = description;
                        pkgInfo.size = packageSize;
                        pkgInfo.unitPrice = calculatedUnitPrice;
                        pkgInfo.packagePrice = price;
                        pkgInfo.quantity = quantity;
                        pkgInfo.unit = unit;
                        packages.add(pkgInfo);
                        seenDescriptions.add(description);
                    }
                } catch (Exception e) {
                    log.warn("Could not parse generic price from: {}", description);
                }
            }
        }
        
        // If still no packages found, create a simple entry
        if (packages.isEmpty() && !packageContainer.trim().isEmpty() && 
            !packageContainer.equalsIgnoreCase("Price Unavailable")) {
            PackageInfo pkgInfo = new PackageInfo();
            pkgInfo.description = packageContainer.trim();
            pkgInfo.size = packageSize;
            packages.add(pkgInfo);
        }
        
        return packages;
    }
    
    private Integer extractQuantity(String text) {
        // Pattern for "N's pack" or "N pack" or "N ml" etc.
        Pattern pattern = Pattern.compile("(\\d+(?:\\.\\d+)?)\\s*['']?s?\\s*(?:pack|tablet|capsule|ml|gm|g|mg|vial|bottle|container)", Pattern.CASE_INSENSITIVE);
        Matcher matcher = pattern.matcher(text);
        if (matcher.find()) {
            try {
                return (int) Double.parseDouble(matcher.group(1));
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
    }
    
    private String extractUnit(String text) {
        String lower = text.toLowerCase();
        if (lower.contains("ml")) return "ml";
        if (lower.contains("gm") || (lower.contains("g") && !lower.contains("mg"))) return "gm";
        if (lower.contains("mg")) return "mg";
        if (lower.contains("pack")) return "pack";
        if (lower.contains("tablet")) return "tablets";
        if (lower.contains("capsule")) return "capsules";
        if (lower.contains("vial")) return "vial";
        if (lower.contains("bottle")) return "bottle";
        if (lower.contains("container")) return "container";
        return "pieces";
    }
    
    private static class PackageInfo {
        String description;
        String size;
        BigDecimal unitPrice;
        BigDecimal packagePrice;
        Integer quantity;
        String unit;
    }
    
    public static class ImportResult {
        private boolean success = false;
        private String message = "";
        private String error = "";
        private int imported = 0;
        private List<String> skipped = new ArrayList<>();
        
        public void incrementImported() {
            imported++;
        }
        
        public void addSkipped(String reason) {
            skipped.add(reason);
        }
        
        // Getters and Setters
        public boolean isSuccess() { return success; }
        public void setSuccess(boolean success) { this.success = success; }
        public String getMessage() { return message; }
        public void setMessage(String message) { this.message = message; }
        public String getError() { return error; }
        public void setError(String error) { this.error = error; }
        public int getImported() { return imported; }
        public void setImported(int imported) { this.imported = imported; }
        public List<String> getSkipped() { return skipped; }
        public void setSkipped(List<String> skipped) { this.skipped = skipped; }
    }
}

