import { lazy, Suspense } from 'react';
import { BrowserRouter, Navigate, Route, Routes } from 'react-router-dom';
import { ToastProvider } from '@/context/ToastContext';
import { ConnectivityProvider } from '@/context/ConnectivityContext';
import { AuthProvider } from '@/context/AuthContext';
import { RealtimeProvider } from '@/context/RealtimeContext';
import { NotificationsProvider } from '@/context/NotificationsContext';
import { ErrorBoundary } from '@/components/ErrorBoundary';
import { AppShell } from '@/components/layout/AppShell';
import { FullScreenLoader, RequireAdmin, RequireAuth } from '@/components/RouteGuards';
import { ForbiddenPage, NotFoundPage } from '@/pages/ForbiddenPage';

// Route-level code splitting keeps the initial shell light: Leaflet, Recharts and
// the admin tables load only when their route is opened.
const LoginPage = lazy(() => import('@/pages/LoginPage').then((module) => ({ default: module.LoginPage })));
const OverviewPage = lazy(() => import('@/pages/OverviewPage').then((module) => ({ default: module.OverviewPage })));
const LivePage = lazy(() => import('@/pages/LivePage').then((module) => ({ default: module.LivePage })));
const MapPage = lazy(() => import('@/pages/MapPage').then((module) => ({ default: module.MapPage })));
const DevicesPage = lazy(() => import('@/pages/DevicesPage').then((module) => ({ default: module.DevicesPage })));
const AnalyticsPage = lazy(() => import('@/pages/AnalyticsPage').then((module) => ({ default: module.AnalyticsPage })));
const UsersPage = lazy(() => import('@/pages/admin/UsersPage').then((module) => ({ default: module.UsersPage })));
const AuditLogPage = lazy(() => import('@/pages/admin/AuditLogPage').then((module) => ({ default: module.AuditLogPage })));
const SettingsPage = lazy(() => import('@/pages/admin/SettingsPage').then((module) => ({ default: module.SettingsPage })));
const ProfilePage = lazy(() => import('@/pages/ProfilePage').then((module) => ({ default: module.ProfilePage })));

export function App() {
  return (
    <ErrorBoundary>
      <ToastProvider>
        <ConnectivityProvider>
          <AuthProvider>
            <RealtimeProvider>
              <NotificationsProvider>
                <BrowserRouter>
                  <Suspense fallback={<FullScreenLoader label="Loading console…" />}>
                    <Routes>
                      <Route path="/login" element={<LoginPage />} />
                      <Route element={<RequireAuth />}>
                        <Route element={<AppShell />}>
                          <Route index element={<OverviewPage />} />
                          <Route path="live" element={<LivePage />} />
                          <Route path="map" element={<MapPage />} />
                          <Route path="devices" element={<DevicesPage />} />
                          <Route path="analytics" element={<AnalyticsPage />} />
                          <Route path="profile" element={<ProfilePage />} />
                          <Route path="403" element={<ForbiddenPage />} />
                          <Route path="admin" element={<RequireAdmin />}>
                            <Route index element={<Navigate to="users" replace />} />
                            <Route path="users" element={<UsersPage />} />
                            <Route path="audit" element={<AuditLogPage />} />
                            <Route path="settings" element={<SettingsPage />} />
                          </Route>
                          <Route path="*" element={<NotFoundPage />} />
                        </Route>
                      </Route>
                      <Route path="*" element={<NotFoundPage />} />
                    </Routes>
                  </Suspense>
                </BrowserRouter>
              </NotificationsProvider>
            </RealtimeProvider>
          </AuthProvider>
        </ConnectivityProvider>
      </ToastProvider>
    </ErrorBoundary>
  );
}
