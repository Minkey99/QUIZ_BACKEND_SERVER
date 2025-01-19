package com.example.quiz.dto.response;

public record ResponseCheckQuiz(String email, Boolean result, String correctAnswer, String description) {
}
