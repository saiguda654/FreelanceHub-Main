package com.example.FreelanceHub.controllers;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import com.example.FreelanceHub.repositories.ClientRepository;
import com.example.FreelanceHub.repositories.FreelancerRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.bcrypt.BCrypt;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.example.FreelanceHub.Dto.ClientDTO;
import com.example.FreelanceHub.Dto.FreeDTO;
import com.example.FreelanceHub.Dto.LoginRequest;
import com.example.FreelanceHub.models.Client;
import com.example.FreelanceHub.models.Freelancer;
import com.example.FreelanceHub.models.Notification;
import com.example.FreelanceHub.models.Rating;
import com.example.FreelanceHub.repositories.NotificationRepository;
import com.example.FreelanceHub.services.ClientService;
import com.example.FreelanceHub.services.FreelancerService;
import com.example.FreelanceHub.services.NotificationService;
import com.example.FreelanceHub.services.RatingService;

import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api")
public class UserController {

    @Autowired
    private ClientRepository clientRepository;

    @Autowired
    private FreelancerRepository freelancerRepository;

    @Autowired
    private ClientService clientService;

    @Autowired
    private NotificationService notificationService;

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private FreelancerService freeService;

    @Autowired
    private HttpSession session;

    @Autowired
    private RatingService ratingService;

    @GetMapping("/userStats")
    public Map<String, Long> getUserStats() {
        long clientCount = clientRepository.count();
        long freelancerCount = freelancerRepository.count();

        Map<String, Long> stats = new HashMap<>();
        stats.put("clientCount", clientCount);
        stats.put("freelancerCount", freelancerCount);

        return stats;
    }

    @PostMapping("/signup/client")
    public ResponseEntity<Map<String, String>> registerClient(@Valid @RequestBody ClientDTO clientDTO,
            BindingResult result) {
        Map<String, String> response = new HashMap<>();

        if (result.hasErrors()) {
            response.put("message", "Validation errors occurred. Please correct the errors and try again.");
            return ResponseEntity.badRequest().body(response);
        }
        boolean emailExists = clientService.isEmailAlreadyRegistered(clientDTO.getCompEmail());
        if (emailExists) {
            response.put("message", "Email already exists. Please use a different email.");
            return ResponseEntity.status(HttpStatus.CONFLICT).body(response);
        }
        Client client = new Client();
        client.setCompEmail(clientDTO.getCompEmail());
        client.setCompanyName(clientDTO.getCompanyName());
        client.setCompanyDescription(clientDTO.getCompanyDescription());
        client.setTypeOfProject(clientDTO.getTypeOfProject());
        client.setRepName(clientDTO.getRepName());
        client.setRepDesignation(clientDTO.getRepDesignation());
        String hashedPassword = BCrypt.hashpw(clientDTO.getPassword(), BCrypt.gensalt());
        clientService.hashExistingPasswords();
        client.setPassword(hashedPassword);

        boolean isRegistered = clientService.registerClient(client);
        if (isRegistered) {
            response.put("message", "Sign Up Successful!");
            return ResponseEntity.ok(response);
        } else {
            response.put("message", "Failed to register. Please try again.");
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    @GetMapping("/signup/freelancer")
    public ResponseEntity<String> showFreelancerSignupPage() {
        return ResponseEntity.status(HttpStatus.OK).body("Frontend handled by Angular");
    }

    @PostMapping("/signup/freelancer")
    public ResponseEntity<?> registerFreelancer(@RequestParam(value = "resume", required = false) MultipartFile resume,
            @RequestParam(value = "profileImage", required = false) MultipartFile profileImage,
            @Valid FreeDTO freelancerDTO,
            BindingResult bindingResult) {

        Map<String, String> response = new HashMap<>();
        if (bindingResult.hasErrors()) {
            Map<String, String> errors = new HashMap<>();
            bindingResult.getFieldErrors().forEach(error -> errors.put(error.getField(), error.getDefaultMessage()));
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errors);
        }

        boolean emailExists = freeService.isEmailAlreadyRegistered(freelancerDTO.getFreeEmail());
        if (emailExists) {
            response.put("message", "Email already exists. Please use a different email.");
            return ResponseEntity.status(HttpStatus.CONFLICT).body(response);
        }

        String imageUrl = null;
        String pdfUrl = null;

        if (profileImage != null && !profileImage.isEmpty()) {
            imageUrl = freeService.saveProfileImage(profileImage);
        }

        if (resume != null && !resume.isEmpty()) {
            pdfUrl = freeService.saveFile(resume);
        }

        Freelancer freelancer = new Freelancer();
        freelancer.setFreeEmail(freelancerDTO.getFreeEmail());
        freelancer.setFreeName(freelancerDTO.getFreeName());
        freelancer.setFreeAge(freelancerDTO.getFreeAge());
        freelancer.setCountry(freelancerDTO.getCountry());
        freelancer.setFOW(freelancerDTO.getFOW());
        freelancer.setExperience(freelancerDTO.getExperience());
        freelancer.setSkills(freelancerDTO.getSkills());
        freelancer.setQualification(freelancerDTO.getQualification());
        String hashedPassword = BCrypt.hashpw(freelancerDTO.getPassword(), BCrypt.gensalt());
        freelancer.setPassword(hashedPassword);
        freeService.hashExistingFreelancerPasswords();

        if (imageUrl != null) {
            freelancer.setProfile_image(imageUrl);
        }

        if (pdfUrl != null) {
            freelancer.setResume(pdfUrl);
        }

        boolean success = freeService.registerFreelancer(freelancer);
        if (success) {
            response.put("message", "Freelancer Registered Successfully");
            return ResponseEntity.status(HttpStatus.CREATED).body(response);
        } else {
            response.put("message", "Error in Registration");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
        }

    }

    @PostMapping("/login")
    public ResponseEntity<Map<String, String>> login(@RequestBody LoginRequest loginRequest,
            Model model,
            RedirectAttributes redirectAttributes) {
        String email = loginRequest.getEmail();
        String password = loginRequest.getPassword();
        Map<String, String> response = new HashMap<>();
        freeService.hashExistingFreelancerPasswords();
        clientService.hashExistingPasswords();
        Client client = clientService.clientRepository.findBycompEmail(email);
        if (client != null && BCrypt.checkpw(password, client.getPassword())) {
            String role = clientService.getUserRole(client.getClientId());

            response.put("status", "success");
            response.put("message", "Client login successful!");
            response.put("role", role);
            response.put("userId", client.getClientId());
            return ResponseEntity.ok(response);
        }

        Freelancer freelancer = freeService.freeRepository.findByfreeEmail(email);
        if (freelancer != null && BCrypt.checkpw(password, freelancer.getPassword())) {
            String role = freeService.getUserRole(freelancer.getFreeId());

            response.put("status", "success");
            response.put("message", "Freelancer login successful!");
            response.put("role", role);
            response.put("userId", freelancer.getFreeId());
            return ResponseEntity.ok(response);
        }

        response.put("status", "error");
        response.put("message", "Invalid email or password.");
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(response);
    }

    @GetMapping("/logout")
    public String logout(HttpSession session, RedirectAttributes redirectAttributes) {
        session.invalidate();
        redirectAttributes.addFlashAttribute("notificationType", "success");
        redirectAttributes.addFlashAttribute("notificationMessage", "Logged out successfully!");
        return "redirect:/";
    }

    @GetMapping("/getUnreadNotifications")
    @ResponseBody
    public Map<String, Object> getUnreadNotifications(@RequestParam("userId") String userId) {
        notificationService.delNotification(userId);
        List<Notification> unreadNotifs = notificationService.getNotifications(userId);
        for (Notification notif : unreadNotifs) {
            notificationRepository.save(notif);
        }
        long unreadCount = unreadNotifs.stream()
                .filter(notif -> "false".equals(notif.isRead()))
                .count();

        Map<String, Object> response = new HashMap<>();
        response.put("notifications", unreadNotifs);
        response.put("unreadCount", unreadCount);
        return response;
    }

    @PostMapping("/markNotificationsAsRead")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void markNotificationsAsRead(@RequestParam("userId") String userId) {
        List<Notification> unreadNotifications = notificationService.getNotifications(userId);
        for (Notification notif : unreadNotifications) {
            notif.setRead("true");
            notificationRepository.save(notif);
        }
    }

    @GetMapping("/profile")
    public String getProfilePage(Model model) {
        String role = (String) session.getAttribute("role");

        if (role == null) {
            return "redirect:/login";
        }

        if (role.equals("client")) {
            return "redirect:/profile/client";

        } else if (role.equals("freelancer")) {
            return "redirect:/profile/freelancer";
        }

        return "redirect:/";
    }

    @PostMapping("/ratings")
    public Rating addRating(@RequestParam String freelancerId,
            @RequestParam String clientId,
            @RequestParam int jobId,
            @RequestParam int rating) {
        return ratingService.addRating(freelancerId, clientId, jobId, rating);
    }

    @GetMapping("/ratings")
    public Integer getRatingCount(@RequestParam String freelancerId) {
        return ratingService.countFreelancerRatings(freelancerId);
    }

}
