package com.koval.trainingplannerbackend.training.history;

import com.koval.trainingplannerbackend.training.model.Training;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/history")
@CrossOrigin(origins = "*")
public class HistoryController {

    private final HistoryService historyService;

    public HistoryController(HistoryService historyService) {
        this.historyService = historyService;
    }

    @GetMapping
    public List<Training> getAllHistory() {
        return historyService.getAllTrainings();
    }

    @PostMapping
    public Training saveCompletedWorkout(@RequestBody Training training) {
        historyService.saveTraining(training);
        return training;
    }
}
