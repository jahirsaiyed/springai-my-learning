package com.example.ecommercemcp.tools;

import com.example.ecommerce.service.EcommerceCustomerService;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

@Component
public class McpCustomerTools {

    private final EcommerceCustomerService customerService;

    public McpCustomerTools(EcommerceCustomerService customerService) {
        this.customerService = customerService;
    }

    @Tool(description = "Get customer details by customer ID, including order count.")
    public String getCustomer(@ToolParam(description = "The customer ID") String customerId) {
        try {
            var c = customerService.getCustomer(customerId);
            long orderCount = customerService.getOrderCount(customerId);
            return "Customer Details:\n"
                    + "- ID: " + c.getCustomerId() + "\n"
                    + "- City: " + c.getCity() + "\n"
                    + "- State: " + c.getState() + "\n"
                    + "- Zip: " + c.getZipCodePrefix() + "\n"
                    + "- Total Orders: " + orderCount;
        } catch (Exception e) {
            return "CUSTOMER_NOT_FOUND: No customer found with ID '" + customerId + "'.";
        }
    }

    @Tool(description = "Search customers by city or state name.")
    public String searchCustomers(
            @ToolParam(description = "Search query (city or state)") String query,
            @ToolParam(description = "Max results (default 10)", required = false) Integer limit) {
        int maxResults = (limit != null && limit > 0) ? limit : 10;
        var customers = customerService.searchCustomers(query, maxResults);
        if (customers.isEmpty()) return "No customers found matching '" + query + "'.";
        var sb = new StringBuilder("Found " + customers.size() + " customers:\n");
        for (var c : customers) {
            sb.append("- ID: ").append(c.getCustomerId())
                    .append(" | ").append(c.getCity())
                    .append(", ").append(c.getState())
                    .append("\n");
        }
        return sb.toString();
    }
}
