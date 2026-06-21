package com.example.ecommercemcp.tools;

import com.example.ecommerce.service.EcommerceProductService;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

@Component
public class McpProductTools {

    private final EcommerceProductService productService;

    public McpProductTools(EcommerceProductService productService) {
        this.productService = productService;
    }

    @Tool(description = "Search for products by category keyword. Returns matching products with id, category, and price.")
    public String searchProducts(
            @ToolParam(description = "Search query (matches category name)") String query,
            @ToolParam(description = "Filter by exact category name (optional)", required = false) String category,
            @ToolParam(description = "Max results to return (default 10)", required = false) Integer limit) {
        int maxResults = (limit != null && limit > 0) ? limit : 10;
        var products = productService.searchProducts(query, category, maxResults);
        if (products.isEmpty()) return "No products found matching '" + query + "'.";
        var sb = new StringBuilder("Found " + products.size() + " products:\n");
        for (var p : products) {
            sb.append("- ID: ").append(p.getProductId())
                    .append(" | Category: ").append(p.getCategoryNameEn())
                    .append(" | Weight: ").append(p.getWeightG() != null ? p.getWeightG() + "g" : "N/A")
                    .append("\n");
        }
        return sb.toString();
    }

    @Tool(description = "Get full details for a product by its product ID.")
    public String getProduct(@ToolParam(description = "The product ID") String productId) {
        try {
            var p = productService.getProduct(productId);
            var rating = productService.getAverageRating(productId);
            var reviewCount = productService.getReviewCount(productId);
            return "Product Details:\n"
                    + "- ID: " + p.getProductId() + "\n"
                    + "- Category: " + p.getCategoryNameEn() + "\n"
                    + "- Weight: " + (p.getWeightG() != null ? p.getWeightG() + "g" : "N/A") + "\n"
                    + "- Dimensions: " + (p.getLengthCm() != null ? p.getLengthCm() + "x" + p.getHeightCm() + "x" + p.getWidthCm() + " cm" : "N/A") + "\n"
                    + "- Photos: " + (p.getPhotosQty() != null ? p.getPhotosQty() : 0) + "\n"
                    + "- Avg Rating: " + (rating != null ? String.format("%.1f", rating) : "N/A") + "\n"
                    + "- Review Count: " + (reviewCount != null ? reviewCount : 0);
        } catch (Exception e) {
            return "PRODUCT_NOT_FOUND: No product found with ID '" + productId + "'.";
        }
    }

    @Tool(description = "List all product categories with their English names and product counts.")
    public String listCategories() {
        var categories = productService.listCategories();
        if (categories.isEmpty()) return "No categories found.";
        var sb = new StringBuilder("Product Categories (" + categories.size() + "):\n");
        for (var row : categories) {
            sb.append("- ").append(row[0]).append(": ").append(row[1]).append(" products\n");
        }
        return sb.toString();
    }
}
