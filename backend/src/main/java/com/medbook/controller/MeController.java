package com.medbook.controller;

import com.medbook.dto.AccountUpdateRequest;
import com.medbook.dto.ChangePasswordRequest;
import com.medbook.dto.CurrentUserResponse;
import com.medbook.dto.DoctorProfileUpdateRequest;
import com.medbook.dto.PatientProfileUpdateRequest;
import com.medbook.service.ProfileService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;















@RestController
@RequestMapping("/api/v1/me")
public class MeController {

    private final ProfileService profileService;

    public MeController(ProfileService profileService) {
        this.profileService = profileService;
    }


    @GetMapping("/profile")
    public ResponseEntity<CurrentUserResponse> getProfile() {
        return ResponseEntity.ok(profileService.getCurrentUserProfile());
    }





    @PutMapping("/account")
    public ResponseEntity<CurrentUserResponse> updateAccount(@Valid @RequestBody AccountUpdateRequest request) {
        return ResponseEntity.ok(profileService.updateAccount(request));
    }





    @PutMapping("/patient-profile")
    public ResponseEntity<CurrentUserResponse> updatePatientProfile(
            @Valid @RequestBody PatientProfileUpdateRequest request) {
        return ResponseEntity.ok(profileService.updatePatientProfile(request));
    }





    @PutMapping("/doctor-profile")
    public ResponseEntity<CurrentUserResponse> updateDoctorProfile(
            @Valid @RequestBody DoctorProfileUpdateRequest request) {
        return ResponseEntity.ok(profileService.updateDoctorProfile(request));
    }






    @PostMapping("/change-password")
    public ResponseEntity<Void> changePassword(@Valid @RequestBody ChangePasswordRequest request) {
        profileService.changePassword(request);
        return ResponseEntity.noContent().build();
    }
}
