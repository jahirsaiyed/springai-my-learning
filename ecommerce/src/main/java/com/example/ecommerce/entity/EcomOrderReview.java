package com.example.ecommerce.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "order_reviews", schema = "ecommerce")
public class EcomOrderReview {

    @Id
    @Column(name = "review_id", length = 64)
    private String reviewId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false)
    private EcomOrder order;

    @Column(name = "review_score", nullable = false)
    private Integer reviewScore;

    @Column(name = "review_comment_title", columnDefinition = "TEXT")
    private String reviewCommentTitle;

    @Column(name = "review_comment_message", columnDefinition = "TEXT")
    private String reviewCommentMessage;

    @Column(name = "review_creation_date")
    private LocalDateTime reviewCreationDate;

    @Column(name = "review_answer_timestamp")
    private LocalDateTime reviewAnswerTimestamp;

    protected EcomOrderReview() {}

    public String getReviewId() { return reviewId; }
    public EcomOrder getOrder() { return order; }
    public Integer getReviewScore() { return reviewScore; }
    public String getReviewCommentTitle() { return reviewCommentTitle; }
    public String getReviewCommentMessage() { return reviewCommentMessage; }
    public LocalDateTime getReviewCreationDate() { return reviewCreationDate; }
    public LocalDateTime getReviewAnswerTimestamp() { return reviewAnswerTimestamp; }
}
