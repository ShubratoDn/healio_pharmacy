package com.heal.io.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.util.*;

/**
 * Service for processing invoice images using ChatGPT Vision API
 */
@Service
@Slf4j
public class InvoiceProcessingService {

    @Value("${openai.api.key:}")
    private String apiKey;

    @Value("${openai.api.url:https://api.openai.com/v1/chat/completions}")
    private String apiUrl;

    private final WebClient webClient;
    private final ObjectMapper objectMapper;

    public InvoiceProcessingService() {
        this.webClient = WebClient.builder()
                .baseUrl("https://api.openai.com/v1")
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .clientConnector(new ReactorClientHttpConnector(
                        reactor.netty.http.client.HttpClient.create()
                                .responseTimeout(Duration.ofSeconds(60))
                ))
                .build();
        this.objectMapper = new ObjectMapper();
    }

    /**
     * Process invoice image and extract product information
     * @param base64Image Base64 encoded image
     * @return List of extracted products with their details
     */
    public List<ExtractedProduct> processInvoiceImage(String base64Image) {
        if (apiKey == null || apiKey.isEmpty()) {
            log.error("OpenAI API key is not configured");
            throw new RuntimeException("OpenAI API key is not configured. Please set openai.api.key in application.properties");
        }

        try {
            String prompt = "You are an expert at extracting pharmaceutical product information from invoices. " +
                    "Analyze this invoice image and extract all product details. " +
                    "For each product, extract the following information if available:\n" +
                    "- Product name\n" +
                    "- Strength (if mentioned)\n" +
                    "- Dosage form (tablet, capsule, syrup, injection, etc.)\n" +
                    "- Manufacturer/Company name\n" +
                    "- Quantity\n" +
                    "- Unit price\n" +
                    "- Total price\n" +
                    "- Package description (e.g., '10 tablets', '100ml bottle')\n\n" +
                    "Return the data as a JSON array where each object has these fields: " +
                    "productName (string), strength (string or null), dosageForm (string or null), " +
                    "manufacturer (string or null), quantity (number), unitPrice (number), " +
                    "totalPrice (number), packageDescription (string or null). " +
                    "Only include products that have at least a product name and quantity. " +
                    "Return ONLY valid JSON array, no additional text or explanation.";

            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("model", "gpt-4o-mini");
            
            List<Map<String, Object>> messages = new ArrayList<>();
            Map<String, Object> message = new HashMap<>();
            message.put("role", "user");
            
            List<Map<String, Object>> content = new ArrayList<>();
            Map<String, Object> textContent = new HashMap<>();
            textContent.put("type", "text");
            textContent.put("text", prompt);
            content.add(textContent);
            
            Map<String, Object> imageContent = new HashMap<>();
            imageContent.put("type", "image_url");
            Map<String, String> imageUrl = new HashMap<>();
            imageUrl.put("url", "data:image/jpeg;base64," + base64Image);
            imageContent.put("image_url", imageUrl);
            content.add(imageContent);
            
            message.put("content", content);
            messages.add(message);
            requestBody.put("messages", messages);
            requestBody.put("max_tokens", 4000);

            String response = webClient.post()
                    .uri("/chat/completions")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                    .body(BodyInserters.fromValue(requestBody))
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(Duration.ofSeconds(60))
                    .block();

            log.info("OpenAI API Response: {}", response);

            if (response == null || response.isEmpty()) {
                throw new RuntimeException("Empty response from OpenAI API");
            }

            JsonNode jsonResponse = objectMapper.readTree(response);
            JsonNode choices = jsonResponse.get("choices");
            
            if (choices == null || !choices.isArray() || choices.size() == 0) {
                throw new RuntimeException("Invalid response format from OpenAI API");
            }

            JsonNode firstChoice = choices.get(0);
            JsonNode messageNode = firstChoice.get("message");
            if (messageNode == null) {
                throw new RuntimeException("Invalid response format: message not found");
            }

            JsonNode contentNode = messageNode.get("content");
            if (contentNode == null) {
                throw new RuntimeException("Invalid response format: content not found");
            }

            String contentText = contentNode.asText().trim();
            
            // Clean the response - sometimes GPT adds markdown code blocks
            if (contentText.startsWith("```json")) {
                contentText = contentText.substring(7);
            }
            if (contentText.startsWith("```")) {
                contentText = contentText.substring(3);
            }
            if (contentText.endsWith("```")) {
                contentText = contentText.substring(0, contentText.length() - 3);
            }
            contentText = contentText.trim();

            JsonNode productsArray = objectMapper.readTree(contentText);
            
            List<ExtractedProduct> products = new ArrayList<>();
            if (productsArray.isArray()) {
                for (JsonNode productNode : productsArray) {
                    ExtractedProduct product = new ExtractedProduct();
                    product.setProductName(getStringValue(productNode, "productName"));
                    product.setStrength(getStringValue(productNode, "strength"));
                    product.setDosageForm(getStringValue(productNode, "dosageForm"));
                    product.setManufacturer(getStringValue(productNode, "manufacturer"));
                    product.setQuantity(getIntValue(productNode, "quantity"));
                    product.setUnitPrice(getDoubleValue(productNode, "unitPrice"));
                    product.setTotalPrice(getDoubleValue(productNode, "totalPrice"));
                    product.setPackageDescription(getStringValue(productNode, "packageDescription"));
                    
                    // Only add if product name and quantity are present
                    if (product.getProductName() != null && !product.getProductName().trim().isEmpty() 
                            && product.getQuantity() != null && product.getQuantity() > 0) {
                        products.add(product);
                    }
                }
            }

            log.info("Extracted {} products from invoice", products.size());
            return products;

        } catch (Exception e) {
            log.error("Error processing invoice image: ", e);
            throw new RuntimeException("Error processing invoice image: " + e.getMessage(), e);
        }
    }

    private String getStringValue(JsonNode node, String fieldName) {
        JsonNode field = node.get(fieldName);
        if (field == null || field.isNull()) {
            return null;
        }
        String value = field.asText();
        return value != null && !value.trim().isEmpty() && !"null".equalsIgnoreCase(value) ? value.trim() : null;
    }

    private Integer getIntValue(JsonNode node, String fieldName) {
        JsonNode field = node.get(fieldName);
        if (field == null || field.isNull()) {
            return null;
        }
        try {
            return field.asInt();
        } catch (Exception e) {
            // Try parsing as string
            try {
                return Integer.parseInt(field.asText().trim());
            } catch (Exception ex) {
                return null;
            }
        }
    }

    private Double getDoubleValue(JsonNode node, String fieldName) {
        JsonNode field = node.get(fieldName);
        if (field == null || field.isNull()) {
            return null;
        }
        try {
            return field.asDouble();
        } catch (Exception e) {
            // Try parsing as string
            try {
                return Double.parseDouble(field.asText().trim());
            } catch (Exception ex) {
                return null;
            }
        }
    }

    /**
     * Data class for extracted product information
     */
    public static class ExtractedProduct {
        private String productName;
        private String strength;
        private String dosageForm;
        private String manufacturer;
        private Integer quantity;
        private Double unitPrice;
        private Double totalPrice;
        private String packageDescription;

        // Getters and setters
        public String getProductName() { return productName; }
        public void setProductName(String productName) { this.productName = productName; }

        public String getStrength() { return strength; }
        public void setStrength(String strength) { this.strength = strength; }

        public String getDosageForm() { return dosageForm; }
        public void setDosageForm(String dosageForm) { this.dosageForm = dosageForm; }

        public String getManufacturer() { return manufacturer; }
        public void setManufacturer(String manufacturer) { this.manufacturer = manufacturer; }

        public Integer getQuantity() { return quantity; }
        public void setQuantity(Integer quantity) { this.quantity = quantity; }

        public Double getUnitPrice() { return unitPrice; }
        public void setUnitPrice(Double unitPrice) { this.unitPrice = unitPrice; }

        public Double getTotalPrice() { return totalPrice; }
        public void setTotalPrice(Double totalPrice) { this.totalPrice = totalPrice; }

        public String getPackageDescription() { return packageDescription; }
        public void setPackageDescription(String packageDescription) { this.packageDescription = packageDescription; }
    }
}

