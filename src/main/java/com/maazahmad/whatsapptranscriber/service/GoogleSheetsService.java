package com.maazahmad.whatsapptranscriber.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.auth.http.HttpCredentialsAdapter;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.sheets.v4.Sheets;
import com.google.api.services.sheets.v4.SheetsScopes;
import com.google.api.services.sheets.v4.model.ValueRange;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.time.LocalDate;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class GoogleSheetsService {

    private final ObjectMapper objectMapper;
    private Sheets sheetsService;

    @Value("${google.sheets.id}")
    private String spreadsheetId;

    @Value("${google.credentials.path}")
    private String credentialsPath;

    @PostConstruct
    public void init() throws IOException, GeneralSecurityException {
        // Load the JSON key file from resources
        GoogleCredentials credentials = GoogleCredentials.fromStream(
                        new ClassPathResource(credentialsPath).getInputStream())
                .createScoped(Collections.singleton(SheetsScopes.SPREADSHEETS));

        // Build the Sheets API client
        this.sheetsService = new Sheets.Builder(
                GoogleNetHttpTransport.newTrustedTransport(),
                GsonFactory.getDefaultInstance(),
                new HttpCredentialsAdapter(credentials))
                .setApplicationName("SpendTrace")
                .build();
    }

    @SneakyThrows
    public void logExpense(String jsonExpense) {
        // 1. Parse the Llama-3 JSON
        JsonNode root = objectMapper.readTree(jsonExpense);

        // 2. Extract fields (Handle potential nulls safely)
        String date = root.path("date").asText("N/A");
        String item = root.path("item").asText("Unknown");
        double amount = root.path("amount").asDouble(0.0);
        String currency = root.path("currency").asText("PKR");
        String merchant = root.path("merchant").asText("Unknown");
        String category = root.path("category").asText("Uncategorized");

        // 3. Prepare the Row Data
        // Order: Date | Item | Amount | Currency | Merchant | Category
        List<Object> rowData = List.of(date, item, amount, currency, merchant, category);
        ValueRange body = new ValueRange().setValues(List.of(rowData));

        // 4. Append to the Sheet
        // "Sheet1!A1" tells Google to start looking at A1 and append to the next empty row
        sheetsService.spreadsheets().values()
                .append(spreadsheetId, "Sheet1!A1", body)
                .setValueInputOption("USER_ENTERED") // Allows Google to format numbers/dates automatically
                .execute();

        System.out.println("✅ Expense logged to Google Sheets!");
    }

    // 1. Fetch all rows from the sheet
    @SneakyThrows
    public List<List<Object>> readAllRows() {
        ValueRange response = sheetsService.spreadsheets().values()
                .get(spreadsheetId, "Sheet1!A:F") // Fetches columns A to F
                .execute();
        return response.getValues();
    }

    // Updated Calculator: Handles "ALL" dates and Multi-Currency Summing
    public String calculateAnalytics(String category, String merchant, String item, String startDateStr, String endDateStr) {
        List<List<Object>> rows = readAllRows();
        if (rows == null || rows.isEmpty()) return "No data found.";

        // Use a Map to store totals per currency (e.g., "PKR" -> 5000.0, "USD" -> 20.0)
        Map<String, Double> totals = new HashMap<>();
        int count = 0;

        // SAFE DATE PARSING
        LocalDate start;
        LocalDate end;
        try {
            // If AI says "ALL" or empty, use a wide range
            start = (startDateStr == null || startDateStr.equalsIgnoreCase("ALL"))
                    ? LocalDate.of(2000, 1, 1)
                    : LocalDate.parse(startDateStr);

            end = (endDateStr == null || endDateStr.equalsIgnoreCase("ALL"))
                    ? LocalDate.of(2100, 12, 31)
                    : LocalDate.parse(endDateStr);
        } catch (Exception e) {
            // Fallback if AI gives garbage date
            System.err.println("Date parse error: " + e.getMessage());
            start = LocalDate.of(2000, 1, 1);
            end = LocalDate.of(2100, 12, 31);
        }

        // Normalize filters
        String targetCategory = category.trim();
        String targetMerchant = merchant.trim();
        String targetItem = item.trim();

        for (List<Object> row : rows) {
            try {
                // Row Schema: Date(0) | Item(1) | Amount(2) | Currency(3) | Merchant(4) | Category(5)
                if (row.size() < 6) continue;

                String rowDateStr = row.get(0).toString();
                String rowItem = row.get(1).toString();
                String amountStr = row.get(2).toString();
                String rowCurrency = row.get(3).toString().toUpperCase(); // Normalize currency
                String rowMerchant = row.get(4).toString();
                String rowCategory = row.get(5).toString();

                LocalDate rowDate = LocalDate.parse(rowDateStr);
                double amount = Double.parseDouble(amountStr);

                // --- FILTER LOGIC ---
                boolean dateMatch = (rowDate.isEqual(start) || rowDate.isAfter(start)) &&
                        (rowDate.isEqual(end) || rowDate.isBefore(end));

                boolean categoryMatch = targetCategory.equalsIgnoreCase("ALL") ||
                        rowCategory.equalsIgnoreCase(targetCategory);

                boolean merchantMatch = targetMerchant.equalsIgnoreCase("ALL") ||
                        rowMerchant.toLowerCase().contains(targetMerchant.toLowerCase());

                boolean itemMatch = targetItem.equalsIgnoreCase("ALL") ||
                        rowItem.toLowerCase().contains(targetItem.toLowerCase());

                if (dateMatch && categoryMatch && merchantMatch && itemMatch) {
                    // Add to the specific currency bucket
                    totals.put(rowCurrency, totals.getOrDefault(rowCurrency, 0.0) + amount);
                    count++;
                }

            } catch (Exception e) {
                // Skip bad rows
            }
        }

        if (count == 0) return "No matching expenses found.";

        // Format the output (e.g., "💰 Total: 20000 PKR, 500 USD")
        StringBuilder result = new StringBuilder();
        result.append("📊 Found ").append(count).append(" transactions:\n");

        totals.forEach((curr, sum) -> {
            result.append(String.format("💰 %.2f %s\n", sum, curr));
        });

        result.append(String.format("📅 Period: %s to %s", start, end));
        return result.toString();
    }
}