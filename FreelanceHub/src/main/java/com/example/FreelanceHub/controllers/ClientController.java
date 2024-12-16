package com.example.FreelanceHub.controllers;

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
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
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

    // Display the job creation form
    @GetMapping("/postjob")
    public String showJobForm(Model model) {
    	String clientId = (String) session.getAttribute("userId");
        model.addAttribute("clientId", clientId);
        model.addAttribute("clientJob", new ClientJob());
        return "postjob"; 
    }

    // Handle form submission
    @PostMapping("/postjob")
public ResponseEntity<Map<String, String>> createJob(
        @RequestBody ClientJobDTO clientJobDTO,
        @RequestParam("userId") String userId) {
    Map<String, String> response = new HashMap<>();
    try {
        if (userId == null || userId.isEmpty()) {
            response.put("message", "User not logged in");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(response);
        }

        ClientJob clientJob = new ClientJob();
        System.out.println(clientJobDTO.getSkillReq());
        clientJob.setClientId(userId);
        clientJob.setJobTitle(clientJobDTO.getJobTitle());
        clientJob.setJobDesc(clientJobDTO.getJobDesc());
        clientJob.setSkillsFromList(clientJobDTO.getSkillReq());
        clientJob.setDurMin(clientJobDTO.getDurMin());
        clientJob.setDurMax(clientJobDTO.getDurMax());
        clientJob.setCostMin(clientJobDTO.getCostMin());
        clientJob.setCostMax(clientJobDTO.getCostMax());
        clientJob.setExpMin(clientJobDTO.getExpMin());
        clientJob.setJobStat(clientJobDTO.getJobStat());

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
        // Fetch all jobs for the client
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
                return bidData;
            }).collect(Collectors.toList());

            // Sort based on the chosen criterion
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
    public ResponseEntity<String> acceptBid(@RequestBody Map<String, Object> payload){
        int jobId = (int) payload.get("jobId");
        String userId = (String) payload.get("userId");

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
        freelancerJobRepository.save(acceptedBid);  

        List<FreelancerJob> allBids = freelancerJobRepository.findByJobId(clientJob);
        
        // notificationService.addNotification(userId, "Your bid was accepted! Check the dashboard.");

        
        for (FreelancerJob bid : allBids) {
            if (!bid.getFreeId().getFreeId().equals(userId)) {
            	// notificationService.addNotification(bid.getFreeId().getFreeId(), "Your bid was rejected! Check the dashboard.");
            	bid.setStatus("rejected");
                freelancerJobRepository.save(bid);
            }
        }

        return ResponseEntity.ok("Bid accepted successfully!");
    }
    
    @GetMapping("/assigned-jobs")
    public ResponseEntity<Map<String, List<FreelancerJob>>> getAssignedProjects(@RequestParam("userId") String userId) {
        String clientId = userId;

        List<FreelancerJob> ongoingJobs = freelancerJobRepository.findByClientIdAndProgress(clientId, "ongoing");
        ongoingJobs.addAll(freelancerJobRepository.findByClientIdAndProgress(clientId, "unverified"));

        List<FreelancerJob> completedJobs = freelancerJobRepository.findByClientIdAndProgress(clientId, "completed");

        Map<String, List<FreelancerJob>> response = new HashMap<>();
        response.put("ongoingJobs", ongoingJobs);
        response.put("completedJobs", completedJobs);
        return ResponseEntity.ok(response);
    }
    
    @PostMapping("/verify-project")
    public ResponseEntity<String> verifyProject(@RequestParam("jobId") Integer jobId) {
        Jobs job = jobRepository.findById(jobId).orElseThrow(() -> new RuntimeException("Job not found"));
        String freeId = job.getFreeId().getFreeId();
        job.setProgress("completed");
        notificationService.addNotification(freeId, "One of your projects was verified. Job marked as complete!");
        jobRepository.save(job);
        return ResponseEntity.ok("Project verified successfully!");
    }
    
    @GetMapping("/profile/client")
    public String getClientProfile(Model model,HttpSession session) {
        String clientId = (String) session.getAttribute("userId");

        if (clientId == null) {
            return "redirect:/login"; // Redirect to login if no email is found in the session
        }

        Client client = clientService.findByClientId(clientId);
        if (client == null) {
            model.addAttribute("error", "Client not found.");
            return "error"; // Display an error page if client is not found
        }

		/* String clientId = (String) session.getAttribute("userId"); */
            
        List<FreelancerJob> ongoingJobs = freelancerJobRepository.findByClientIdAndProgress(clientId, "ongoing");
        for(FreelancerJob job: freelancerJobRepository.findByClientIdAndProgress(clientId, "unverified")){
        	ongoingJobs.add(job);
        }
        List<FreelancerJob> completedJobs = freelancerJobRepository.findByClientIdAndProgress(clientId, "completed");

        model.addAttribute("ongoingJobs", ongoingJobs);
        model.addAttribute("completedJobs", completedJobs);

        model.addAttribute("client", client);
        return "clientprofile"; // Render client profile page
    }
    

    @GetMapping("/client/edit/{clientId}")
    public String showEditForm(@PathVariable("clientId") String clientId, Model model) {
        Client client = clientService.findByClientId(clientId);
                
        model.addAttribute("client", client);
        return "editClientForm"; // Refers to the Thymeleaf template for editing
    }

    // Handle form submission for updating client details
    @PostMapping("/client/edit")
    public String updateClient(@ModelAttribute("client") Client updatedClient,RedirectAttributes redirectAttributes) {
    	String clientId = (String) session.getAttribute("userId");
        Client existingClient = clientService.findByClientId(clientId);
                
        
        // Update the client fields
        existingClient.setCompEmail(updatedClient.getCompEmail());
        existingClient.setCompanyName(updatedClient.getCompanyName());
        existingClient.setCompanyDescription(updatedClient.getCompanyDescription());
        existingClient.setTypeOfProject(updatedClient.getTypeOfProject());
        existingClient.setRepName(updatedClient.getRepName());
        existingClient.setRepDesignation(updatedClient.getRepDesignation());
		existingClient.setPassword(updatedClient.getPassword()); 
        
        // Save updated client to the database
        clientRepository.save(existingClient);
        redirectAttributes.addFlashAttribute("notificationType", "success");
        redirectAttributes.addFlashAttribute("notificationMessage", "Profile edited successfully!");
        return "redirect:/profile/client"; // Redirect to the client profile page
    }
    




}
