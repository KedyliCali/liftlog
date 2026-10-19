package com.behlul.liftlog.controller;

import com.behlul.liftlog.dto.WeightDtos.WeightEntryRequest;
import com.behlul.liftlog.dto.WeightDtos.WeightEntryResponse;
import com.behlul.liftlog.dto.WeightDtos.WeightHistoryResponse;
import com.behlul.liftlog.service.WeightService;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/v1/weight")
public class WeightController {

    private final WeightService weightService;

    public WeightController(WeightService weightService) {
        this.weightService = weightService;
    }

    /** days must be 30, 90 or 365; today (YYYY-MM-DD) is the phone's date, the server's date when absent. */
    @GetMapping
    public WeightHistoryResponse history(@RequestParam(defaultValue = "30") int days,
                                         @RequestParam(required = false)
                                         @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate today) {
        return weightService.history(days, today);
    }

    /** PUT because the date is the entry's identity: saving the same day twice replaces the weight. */
    @PutMapping("/{date}")
    public WeightEntryResponse save(@PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
                                    @Valid @RequestBody WeightEntryRequest request) {
        return weightService.save(date, request.weightKg());
    }

    @DeleteMapping("/{date}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        weightService.delete(date);
    }
}
