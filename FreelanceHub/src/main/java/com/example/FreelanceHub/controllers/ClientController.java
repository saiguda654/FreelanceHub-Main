package com.example.FreelanceHub.controllers;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.bcrypt.BCrypt;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.example.FreelanceHub.Dto.ClientJobDTO;
import com.example.FreelanceHub.models.Client;
import com.example.FreelanceHub.models.ClientJob;
import com.example.FreelanceHub.models.Freelancer;
import com.example.FreelanceHub.models.FreelancerJob;
import com.example.FreelanceHub.models.Jobs;
import com.example.FreelanceHub.repositories.ClientJobRepository;
import com.example.FreelanceHub.repositories.ClientRepository;
import com.example.FreelanceHub.repositories.FreeJobRepository;
import com.example.FreelanceHub.repositories.JobRepository;
import com.example.FreelanceHub.services.ClientJobService;
import com.example.FreelanceHub.services.ClientService;
import com.example.FreelanceHub.services.NotificationService;

import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api")
public class ClientController {

    @Autowired
    private NotificationService notificationService;

    @Autowired
    private ClientJobRepository clientJobRepository;

    @Autowired
    private FreeJobRepository freelancerJobRepository;

    @Autowired
    private JobRepository jobRepository;

    @Autowired
    private ClientJobService clientJobService;

    @Autowired
    private ClientService clientService;

    @Autowired
    private ClientRepository clientRepository;

    @Autowired
    private HttpSession session;

    @PostMapping("/postjob")
    public ResponseEntity<Map<String, String>> createJob(
            @RequestBody ClientJob clientJob,
            @RequestParam("userId") String userId) {
        Map<String, String> response = new HashMap<>();
        try {
            if (userId == null || userId.isEmpty()) {
                response.put("message", "User not logged in");
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(response);
            }

            clientJob.setClientId(userId);
            clientJobRepository.save(clientJob);
            response.put("message", "Job posted successfully");
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            response.put("message", "Job posting failed");
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    @GetMapping("/posted-jobs")
    public ResponseEntity<List<ClientJob>> getPostedJobs(@RequestParam("userId") String userId) {
        String clientId = userId;
        List<ClientJob> allJobs = clientJobService.findByClientId(clientId);

        List<ClientJob> pendingJobs = allJobs.stream()
                .filter(job -> "pending".equals(job.getJobStat()))
                .collect(Collectors.toList());

        return ResponseEntity.ok(pendingJobs);
    }

    @GetMapping("/bidding")
    public ResponseEntity<Map<String, Object>> showAllBids(
            @RequestParam(name = "sortBy", required = false, defaultValue = "duration") String sortBy,
            @RequestParam(name = "userId") String userId,
            Model model) {
        String clientId = userId;
        List<ClientJob> jobs = clientJobService.findByClientId(clientId);
        if (jobs == null || jobs.isEmpty()) {
            return ResponseEntity.ok(Collections.singletonMap("jobsWithBids", new ArrayList<>()));
        }

        List<Map<String, Object>> jobsWithBids = jobs.stream()
                .filter(job -> "pending".equals(job.getJobStat()))
                .map(job -> {
                    List<FreelancerJob> freelancerBids = freelancerJobRepository.findByJobId(job);

                    List<Map<String, Object>> enrichedBids = freelancerBids.stream().map(freelancerJob -> {
                        Freelancer freelancer = freelancerJob.getFreeId();
                        Map<String, Object> bidData = new HashMap<>();
                        bidData.put("freelancerJob", freelancerJob);
                        bidData.put("freelancerName", freelancer != null ? freelancer.getFreeName() : "Unknown");
                        bidData.put("freelancerJobDuration", freelancerJob.getDuration());
                        bidData.put("freelancerJobSalary", freelancerJob.getSalary());
                        bidData.put("freelancerJobExp", freelancerJob.getJobExp());
                        bidData.put("freelancerSkillMatch", freelancerJob.getSkillMatch());
                        bidData.put("freelancerRating", freelancer.getRating() != null ? freelancer.getRating() : 0);
                        return bidData;
                    }).collect(Collectors.toList());
                    switch (sortBy) {
                        case "duration":
                            enrichedBids.sort(Comparator.comparingInt(
                                    bid -> (int) bid.get("freelancerJobDuration")));
                            break;
                        case "salary":
                            enrichedBids.sort(Comparator.comparingLong(
                                    bid -> (long) bid.get("freelancerJobSalary")));
                            break;
                        case "experience":
                            enrichedBids.sort((bid1, bid2) -> Integer.compare(
                                    (int) bid2.get("freelancerJobExp"),
                                    (int) bid1.get("freelancerJobExp")));

                            break;
                        case "skillMatch":
                            enrichedBids.sort((bid1, bid2) -> Float.compare(
                                    (float) bid2.get("freelancerSkillMatch"),
                                    (float) bid1.get("freelancerSkillMatch")));
                            break;
                        case "rating":
                            enrichedBids.sort((bid1, bid2) -> Double.compare(
                                    (double) bid2.get("freelancerRating"),
                                    (double) bid1.get("freelancerRating")));
                            break;
                        default:
                            enrichedBids.sort(Comparator.comparingInt(
                                    bid -> (int) bid.get("freelancerJobDuration")));
                            break;
                    }

                    Map<String, Object> jobWithBids = new HashMap<>();
                    jobWithBids.put("job", job);
                    jobWithBids.put("bids", enrichedBids);

                    return jobWithBids;
                }).collect(Collectors.toList());

        Map<String, Object> response = new HashMap<>();
        response.put("jobsWithBids", jobsWithBids);
        response.put("sortBy", sortBy);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/acceptBid")
    public ResponseEntity<String> acceptBid(@RequestBody Map<String, Object> payload) {
        int jobId = (int) payload.get("jobId");
        String userId = (String) payload.get("userId");
        System.out.println("Fetched: " + jobId + " " + userId);

        ClientJob clientJob = clientJobRepository.findById(jobId);
        FreelancerJob acceptedBid = freelancerJobRepository.findByJobIdAndFreeId(clientJob, userId);

        if (clientJob == null || acceptedBid == null) {
            return ResponseEntity.badRequest().body("Invalid job or freelancer.");
        }

        Jobs newJob = new Jobs();
        newJob.setClientId(clientJob.getClients());
        newJob.setFreeId(acceptedBid.getFreeId());
        newJob.setJobId(clientJob);
        newJob.setProgress("ongoing");

        jobRepository.save(newJob);

        clientJob.setJobStat("assigned");
        clientJobRepository.save(clientJob);

        acceptedBid.setStatus("accepted");
        acceptedBid.setJobDetails(newJob);
        acceptedBid.setAcceptedAt(LocalDateTime.now());
        freelancerJobRepository.save(acceptedBid);

        List<FreelancerJob> allBids = freelancerJobRepository.findByJobId(clientJob);

        notificationService.addNotification(userId, "Your bid was accepted! Advance: received! Check the dashboard.");

        for (FreelancerJob bid : allBids) {
            if (!bid.getFreeId().getFreeId().equals(userId)) {
                notificationService.addNotification(bid.getFreeId().getFreeId(),
                        "Your bid was rejected! Check the dashboard.");
                bid.setStatus("rejected");
                freelancerJobRepository.save(bid);
            }
        }

        return ResponseEntity.ok("Bid accepted successfully!");
    }

    @GetMapping("/assigned-jobs")
    public ResponseEntity<Map<String, List<FreelancerJob>>> getAssignedProjects(@RequestParam("userId") String userId) {
        String clientId = userId;

        List<FreelancerJob> ongoingJobs = freelancerJobRepository.findByClientIdAndProgress(clientId, "unverified");
        ongoingJobs.addAll(freelancerJobRepository.findByClientIdAndProgress(clientId, "ongoing"));

        List<FreelancerJob> completedJobs = freelancerJobRepository.findByClientIdAndProgress(clientId, "completed");
        List<FreelancerJob> unpaidJobs = new ArrayList<>();
        List<FreelancerJob> paidJobs = new ArrayList<>();

        for (FreelancerJob job : completedJobs) {
            String paymentStatus = job.getJobDetails() != null ? job.getJobDetails().getPayment_stat() : "Unpaid";
            if ("Unpaid".equalsIgnoreCase(paymentStatus)) {
                unpaidJobs.add(job);
            } else if ("Paid".equalsIgnoreCase(paymentStatus)) {
                paidJobs.add(job);
            }
        }
        Collections.reverse(paidJobs);
        unpaidJobs.addAll(paidJobs);
        completedJobs = unpaidJobs;

        Map<String, List<FreelancerJob>> response = new HashMap<>();
        response.put("ongoingJobs", ongoingJobs);
        response.put("completedJobs", completedJobs);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/update-job")
    public ResponseEntity<String> updateJob(@RequestBody Map<String, Object> requestData) {
        Integer jobId = (Integer) requestData.get("jobId");
        String progress = (String) requestData.get("progress");
        String paymentStatus = (String) requestData.get("paymentStatus");
        Jobs job = jobRepository.findById(jobId).orElseThrow(() -> new RuntimeException("Job not found"));
        String notif = "";

        if (progress != null) {
            if (!job.getProgress().equals(progress)) {
                System.out.println(job.getProgress());
                System.out.println(progress);
                notif += "One of your projects was marked complete!";
            }
            job.setProgress(progress);
        }
        if (paymentStatus != null) {
            if (notif.equals("")) {
                notif += "One of your completed projects got a payment! Check the dashboard!";
            } else {
                notif += " Payment status: " + paymentStatus;
            }
            job.setPayment_stat(paymentStatus);
        }
        jobRepository.save(job);
        notificationService.addNotification(job.getFreeId().getFreeId(), notif);
        return ResponseEntity.ok("Job updated successfully!");
    }

    @GetMapping("/profile/client")
    public ResponseEntity<Map<String, Object>> getClientProfile(@RequestParam String userId) {
        try {
            Client client = clientService.findByClientId(userId);
            if (client == null) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "Client not found"));
            }
            List<FreelancerJob> ongoingJobs = freelancerJobRepository.findByClientIdAndProgress(userId, "ongoing");
            for (FreelancerJob job : freelancerJobRepository.findByClientIdAndProgress(userId, "unverified")) {
                ongoingJobs.add(job);
            }
            List<FreelancerJob> completedJobs = freelancerJobRepository.findByClientIdAndProgress(userId, "completed");
            Map<String, Object> response = new HashMap<>();
            response.put("client", client);
            response.put("ongoingJobs", ongoingJobs);
            response.put("completedJobs", completedJobs);

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "An error occurred while fetching client profile"));
        }
    }

    @GetMapping("/client/edit/{clientId}")
    public ResponseEntity<Client> showEditForm(@PathVariable("clientId") String clientId) {
        Client client = clientService.findByClientId(clientId);
        client.setPassword(null);

        return ResponseEntity.ok(client);
    }

    @PostMapping("/client/edit")
    public ResponseEntity<String> updateClient(@RequestBody Map<String, Object> requestBody) {
        String userId = (String) requestBody.get("userId");
        if (userId == null || userId.isEmpty()) {
            return ResponseEntity.badRequest().body("User ID is required");
        }

        Client existingClient = clientService.findByClientId(userId);
        if (existingClient == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Client not found");
        }
        existingClient.setCompEmail((String) requestBody.get("compEmail"));
        existingClient.setCompanyName((String) requestBody.get("companyName"));
        existingClient.setCompanyDescription((String) requestBody.get("companyDescription"));
        existingClient.setTypeOfProject((String) requestBody.get("typeOfProject"));
        existingClient.setRepName((String) requestBody.get("repName"));
        existingClient.setRepDesignation((String) requestBody.get("repDesignation"));
        String hashedPassword = BCrypt.hashpw(requestBody.get("password").toString(), BCrypt.gensalt());
        existingClient.setPassword(hashedPassword);
        existingClient.setResetToken(null);
        existingClient.setTokenExpiry(null);
        clientRepository.save(existingClient);
        return ResponseEntity.ok("Client updated successfully");
    }

    @PostMapping("/verify-password")
    public ResponseEntity<String> verifyPassword(@RequestBody Map<String, String> payload) {
        String clientId = payload.get("clientId");
        String password = payload.get("password");
        Client client = clientRepository.findByClientId(clientId);
        String mail = client.getCompEmail();
        if (clientService.validateClient(mail, password)) {
            return ResponseEntity.ok("Password verified");
        } else {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Incorrect password");
        }
    }

}
