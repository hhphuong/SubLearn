package org.example.sublearn.admin.dto;

public record PurgeDlqResponse(String queueName, int purgedCount) {
}
