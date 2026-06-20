package com.example.memory.cache;

import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * Classifies user queries into QueryType categories for TTL determination.
 * Uses keyword matching for fast classification without an LLM call.
 */
@Component
public class QueryClassifier {

    private static final Set<String> ORDER_KEYWORDS = Set.of(
        "order", "track", "tracking", "shipment", "delivery", "shipping",
        "where is my", "when will", "package", "dispatch"
    );

    private static final Set<String> REFUND_KEYWORDS = Set.of(
        "refund", "return", "money back", "reimburse", "credit",
        "charged", "overcharged"
    );

    private static final Set<String> POLICY_KEYWORDS = Set.of(
        "policy", "terms", "warranty", "guarantee", "how to",
        "do you", "can i", "what is your", "rules", "guidelines"
    );

    private static final Set<String> PROCEDURAL_KEYWORDS = Set.of(
        "cancel", "cancellation", "exchange", "replace", "modify",
        "change order", "update address", "escalate"
    );

    public QueryType classify(String query) {
        String lower = query.toLowerCase();

        if (containsAny(lower, ORDER_KEYWORDS)) {
            return QueryType.ORDER_STATUS;
        }
        if (containsAny(lower, REFUND_KEYWORDS)) {
            return QueryType.REFUND_STATUS;
        }
        if (containsAny(lower, PROCEDURAL_KEYWORDS)) {
            return QueryType.PROCEDURAL;
        }
        if (containsAny(lower, POLICY_KEYWORDS)) {
            return QueryType.POLICY_FAQ;
        }
        return QueryType.GENERAL;
    }

    private boolean containsAny(String text, Set<String> keywords) {
        for (String keyword : keywords) {
            if (text.contains(keyword)) {
                return true;
            }
        }
        return false;
    }
}
