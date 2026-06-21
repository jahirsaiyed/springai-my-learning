package com.example.ecommercemcp.tools;

import com.example.ecommerce.service.EcommerceReviewService;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

@Component
public class McpReviewTools {

    private final EcommerceReviewService reviewService;

    public McpReviewTools(EcommerceReviewService reviewService) {
        this.reviewService = reviewService;
    }

    @Tool(description = "Get the review for a specific order.")
    public String getOrderReviews(@ToolParam(description = "The order ID") String orderId) {
        var review = reviewService.getOrderReview(orderId);
        if (review.isEmpty()) return "No review found for order " + orderId + ".";
        var r = review.get();
        return "Order Review:\n"
                + "- Order: " + orderId + "\n"
                + "- Score: " + r.getReviewScore() + "/5\n"
                + "- Title: " + (r.getReviewCommentTitle() != null ? r.getReviewCommentTitle() : "N/A") + "\n"
                + "- Comment: " + (r.getReviewCommentMessage() != null ? r.getReviewCommentMessage() : "N/A") + "\n"
                + "- Date: " + r.getReviewCreationDate();
    }

    @Tool(description = "Get reviews for a product across all orders that contain it.")
    public String getProductReviews(
            @ToolParam(description = "The product ID") String productId,
            @ToolParam(description = "Max results (default 10)", required = false) Integer limit) {
        int maxResults = (limit != null && limit > 0) ? limit : 10;
        var reviews = reviewService.getProductReviews(productId, maxResults);
        if (reviews.isEmpty()) return "No reviews found for product " + productId + ".";
        var sb = new StringBuilder("Reviews for product " + productId + " (" + reviews.size() + "):\n");
        for (var r : reviews) {
            sb.append("- Score: ").append(r.getReviewScore()).append("/5");
            if (r.getReviewCommentMessage() != null) {
                sb.append(" | ").append(r.getReviewCommentMessage().length() > 100
                        ? r.getReviewCommentMessage().substring(0, 100) + "..."
                        : r.getReviewCommentMessage());
            }
            sb.append("\n");
        }
        return sb.toString();
    }
}
