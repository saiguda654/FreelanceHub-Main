import { Component,OnInit } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { AuthService } from '../../auth.service';
@Component({
  selector: 'app-explore',
  standalone: false,
  
  templateUrl: './explore.component.html',
  styleUrl: './explore.component.css'
})
export class ExploreComponent implements OnInit{
  searchQuery: string = '';
  jobs: any[] = [];
  role: string | null = '';
  userId: string | null = '';
  private URL = "http://freelancehub12.us-east-1.elasticbeanstalk.com/api";

  constructor(private http: HttpClient, private authService: AuthService) {}

  ngOnInit(): void {
    try {
      if (typeof window !== 'undefined' && window.localStorage) {
        this.role = this.authService.getRole();
        this.userId = localStorage.getItem('userId');
      } else {
        console.warn('localStorage is not available.');
      }
      if (!this.role) {
        console.warn('Role is not found in localStorage. Ensure it is set correctly.');
        this.role = ''; 
      }
      if (!this.userId) {
        console.warn('User ID is not found in localStorage. Ensure it is set correctly.');
        this.handleUserNotLoggedIn();
        return;
      }
      this.fetchAllJobs();
    } catch (error) {
      console.error('Error during initialization:', error);
      this.handleError('An error occurred. Please try again.');
    }
  }

  performSearch(): void {
    if (this.searchQuery.trim()) {
      this.http
        .get<any>(`${this.URL}/search?query=${encodeURIComponent(this.searchQuery)}&userId=${this.userId}`)
        .subscribe(
          (response) => {
            this.jobs = response.jobs;
          },
          (error) => {
            console.error('Error while searching jobs:', error);
            this.handleError('No matching jobs found.');
          }
        );
    } else {
      this.fetchAllJobs();
    }
  }

  fetchAllJobs(): void {
    this.http
      .get<any>(`${this.URL}/search?query=&userId=${this.userId}`)
      .subscribe(
        (response) => {
          this.jobs = response.jobs;
        },
        (error) => {
          console.error('Error while fetching all jobs:', error);
          this.handleError('No jobs found.');
        }
      );
  }

  private handleError(message: string): void {
    console.error(message);
    if (typeof window !== 'undefined') {
      alert(message);
    }
  }

  private handleUserNotLoggedIn(): void {
    console.warn('User is not logged in.');
    if (typeof window !== 'undefined') {
      alert('You are not logged in. Redirecting to login page.');
    }
  }
}

