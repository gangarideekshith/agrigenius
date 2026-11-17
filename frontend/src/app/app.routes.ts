import { Routes } from '@angular/router';
import { authGuard, guestGuard } from './core/auth/auth.guard';

/** Every page is lazy-loaded. Protected pages require a valid session. */
export const routes: Routes = [
  { path: '', pathMatch: 'full', redirectTo: 'dashboard' },
  {
    path: 'login',
    canActivate: [guestGuard],
    loadComponent: () => import('./features/auth/login').then((m) => m.LoginPage),
    title: 'Sign in · AgriGenius',
  },
  {
    path: 'register',
    canActivate: [guestGuard],
    loadComponent: () => import('./features/auth/register').then((m) => m.RegisterPage),
    title: 'Create account · AgriGenius',
  },
  {
    path: 'dashboard',
    canActivate: [authGuard],
    loadComponent: () => import('./features/dashboard/dashboard').then((m) => m.DashboardPage),
    title: 'Home · AgriGenius',
  },
  {
    path: 'profile',
    canActivate: [authGuard],
    loadComponent: () => import('./features/profile/profile').then((m) => m.ProfilePage),
    title: 'My profile · AgriGenius',
  },
  {
    path: 'recommendations',
    canActivate: [authGuard],
    loadComponent: () => import('./features/recommendations/history').then((m) => m.RecommendationHistoryPage),
    title: 'Advice history · AgriGenius',
  },
  {
    path: 'recommendations/new',
    canActivate: [authGuard],
    loadComponent: () => import('./features/recommendations/new-recommendation').then((m) => m.NewRecommendationPage),
    title: 'Crop advice · AgriGenius',
  },
  {
    path: 'recommendations/:id',
    canActivate: [authGuard],
    loadComponent: () => import('./features/recommendations/recommendation-result').then((m) => m.RecommendationResultPage),
    title: 'Crop advice result · AgriGenius',
  },
  {
    path: 'chat',
    canActivate: [authGuard],
    loadComponent: () => import('./features/chat/chat').then((m) => m.ChatPage),
    title: 'Ask AgriGenius',
  },
  { path: '**', redirectTo: 'dashboard' },
];
