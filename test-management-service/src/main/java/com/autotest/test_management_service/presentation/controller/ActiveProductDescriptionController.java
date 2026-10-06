package com.autotest.test_management_service.presentation.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.autotest.test_management_service.application.productdescription.ProductDescriptionService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/product-descriptions")
@RequiredArgsConstructor
public class ActiveProductDescriptionController {
    private final ProductDescriptionService service;

    @GetMapping("/active")
    public ProductDescriptionService.JobView activeForMember(@RequestHeader("X-Member-Id") Long memberId) {
        return service.activeForMember(memberId);
    }
}
