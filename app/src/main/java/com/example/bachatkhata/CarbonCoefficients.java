package com.example.bachatkhata;

import java.util.HashMap;
import java.util.Map;

public class CarbonCoefficients {
    public static final Map<String, Double> CO2_PER_1000_RUPEES = new HashMap<>();

    static {
        CO2_PER_1000_RUPEES.put("Food", 2.1);
        CO2_PER_1000_RUPEES.put("Transport", 12.5); // Petrol/Fuel
        CO2_PER_1000_RUPEES.put("Shopping", 3.2); // Clothes
        // We also support custom categories by matching closest keywords
        CO2_PER_1000_RUPEES.put("Bills", 6.8); // Electricity
        CO2_PER_1000_RUPEES.put("Health", 1.5);
        CO2_PER_1000_RUPEES.put("Entertainment", 1.2);
        CO2_PER_1000_RUPEES.put("Travel", 45.0); // Flights
        CO2_PER_1000_RUPEES.put("Other", 2.0);
    }

    public static double getCoefficient(String category) {
        if (category == null) return 2.0;
        
        // Simple case-insensitive matching
        for (Map.Entry<String, Double> entry : CO2_PER_1000_RUPEES.entrySet()) {
            if (category.toLowerCase().contains(entry.getKey().toLowerCase())) {
                return entry.getValue();
            }
        }
        return 2.0; // default other
    }
}
