package com.example.quiz.service;

import com.example.quiz.dto.request.RequestAnswer;
import com.example.quiz.dto.response.ResponseCheckQuiz;
import com.example.quiz.dto.response.ResponseMessage;
import com.example.quiz.dto.response.ResponseQuiz;
import com.example.quiz.entity.Game;
import com.example.quiz.entity.Quiz;
import com.example.quiz.entity.Room;
import com.example.quiz.entity.User;
import com.example.quiz.enums.Role;
import com.example.quiz.repository.GameRepository;
import com.example.quiz.repository.QuizRepository;
import com.example.quiz.repository.RoomRepository;
import com.example.quiz.repository.UserRepository;
import com.example.quiz.vo.InGameUser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@RequiredArgsConstructor
@Service
public class GameService {

    private static final Map<Long, List<Long>> roomQuizMap = new ConcurrentHashMap<>();
    private final GameRepository gameRepository;
    private final QuizRepository quizRepository;
    private final RoomRepository roomRepository;
    private final UserRepository userRepository;

    @Transactional
    public ResponseMessage toggleReadyStatus(String roomId, Long userId) {
        // User, Game 조회
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));
        Game game = gameRepository.findById(roomId)
                .orElseThrow(() -> new RuntimeException("Game not found"));

        // 현재 로그인한 InGameUser 반환
        Set<InGameUser> inGameUserSet = game.getGameUser();
        // TODO Optional 반환값으로 변환
        InGameUser currentUser = findUser(inGameUserSet, userId);

        // 준비상태 토글
        toggle(game, currentUser);
        // User 준비상태 따라서 DTO 반환
        return handleReadyStatus(user, currentUser, inGameUserSet);
    }

    private InGameUser findUser(Set<InGameUser> inGameUserSet, long userId) {
        for(InGameUser inGameUser : inGameUserSet) {
            if(inGameUser.getId() == userId) {
                return inGameUser;
            }
        }
        return null;
    }

    private void toggle(Game game, InGameUser inGameUser) {
        // 준비상태 변화 및 게임방 최신화
        inGameUser.changeReadyStatus(!inGameUser.isReadyStatus());
        game.getGameUser().add(inGameUser);
        gameRepository.save(game);
    }
    // User
    private ResponseMessage handleReadyStatus(User user, InGameUser inGameUser, Set<InGameUser> inGameUserSet) {
        if(isAllReady(inGameUserSet)) {
            return new ResponseMessage(user.getId(), user.getEmail(), user.getRole(), inGameUser.isReadyStatus(), true);
        }
        else {
            return new ResponseMessage(user.getId(), user.getEmail(), user.getRole(), inGameUser.isReadyStatus(), false);
        }
    }
    // User 인 사람이 모두 Ready 인지 판단
    private boolean isAllReady(Set<InGameUser> inGameUserSet) {
        for(InGameUser inGameUser : inGameUserSet) {
            // Admin 통과
            if(!isUser(inGameUser)) {
                continue;
            }
            if(!inGameUser.isReadyStatus()) {
                return false;
            }
        }
        return true;
    }

    private boolean isUser(InGameUser inGameUser) {
        return inGameUser.getRole() == Role.USER;
    }

    @Transactional
    public ResponseQuiz sendQuiz(String roomId) {
        Room room = roomRepository.findById(Long.valueOf(roomId)).orElseThrow(() -> new RuntimeException("Room not found"));
        Quiz quiz = selectRandomQuiz(Long.parseLong(roomId), room.getTopicId());
        int quizCount = decreaseQuizCount(room);

        return new ResponseQuiz(quiz.getProblem(), quizCount);
    }
    // 남은 퀴즈수 1 감소
    private int decreaseQuizCount(Room room) {
        room.changeQuizCount(room.getQuizCount()-1);
        roomRepository.save(room);
        return room.getQuizCount();
    }
    // topic Id 맞게 중복되지 않는 Quiz 반환
    public Quiz selectRandomQuiz(Long roomId, Long topicId) {
        roomQuizMap.putIfAbsent(roomId, new ArrayList<>());
        List<Long> usedQuizIds = roomQuizMap.get(roomId);

        List<Quiz> allQuizzes = quizRepository.findAllByTopicId(topicId);
        List<Quiz> availableQuizzes = quizRepository.findAllByTopicId(topicId).stream()
                .filter(quiz -> !usedQuizIds.contains(quiz.getQuizId()))
                .toList();

        // 사용 가능한 문제가 없으면 모든 문제를 다시 사용 가능하도록 초기화
        if (availableQuizzes.isEmpty()) {
            log.info("문제를 다 풀었습니다. 문제집을 초기화 합니다.");
            usedQuizIds.clear();
            availableQuizzes = allQuizzes;
        }

        int randomIndex = new Random().nextInt(availableQuizzes.size());
        Quiz selectedQuiz = availableQuizzes.get(randomIndex);

        usedQuizIds.add(selectedQuiz.getQuizId());
        return selectedQuiz;
    }

    public ResponseCheckQuiz checkAnswer(String id, RequestAnswer requestAnswer) {
        User user = userRepository.findById(requestAnswer.userId()).orElseThrow(() -> new RuntimeException("User not found"));
        Room room = roomRepository.findById(Long.valueOf(id)).orElseThrow(() -> new RuntimeException("Room not found"));
        Long correctQuizId = correctQuizId(roomQuizMap.get(room.getRoomId()));
        Quiz quiz = quizRepository.findById(correctQuizId).orElseThrow(() -> new RuntimeException("Quiz not found"));
        boolean isRight = check(requestAnswer.answer(), quiz);

        // 정답이 맞으면 정답 반환. 오답이면 null 반환.
        if(isRight) {
            return new ResponseCheckQuiz(user.getEmail(), true, quiz.getCorrectAnswer(), quiz.getDescription());
        }
        else {
            return new ResponseCheckQuiz(user.getEmail(), false, null, null);
        }
    }

    private boolean check(String answer, Quiz quiz) {
        return quiz.getCorrectAnswer().equals(answer);
    }

    private Long correctQuizId(List<Long> usedQuizIds) {
        return usedQuizIds.stream()
                .skip(usedQuizIds.size() - 1)
                .findFirst()
                .orElse(-1L);
    }
}
