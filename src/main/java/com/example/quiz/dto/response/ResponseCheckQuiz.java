package com.example.quiz.dto.response;

import java.util.List;

public record ResponseCheckQuiz(String email, Boolean currentResult, Boolean finalResult, List<String> finalWinners, String correctAnswer, String description) {
}
