export type UserRole = "ADMIN" | "COACH" | "ATHLETE";
export type UserAccount = {
  id: number; email: string; name: string; role: UserRole; active: boolean;
  athleteId: number | null; accountStatus: "ACTIVE" | "INVITED" | "INACTIVE";
};
export type Session = { authenticationRequired: boolean; user: UserAccount | null; permissions: string[] };
export type PendingInvitation = {
  id: number; email: string; name: string; role: UserRole; athleteId: number | null;
  expiresAt: string; status: "AWAITING_VERIFICATION" | "EXPIRED_OR_LOCKED";
};
export type EnrollmentInfo = {
  email: string; name: string; role: UserRole; verified: boolean; expiresAt: string; codeExpiresAt: string;
};
export type InviteRequest = { name: string; email: string; role: UserRole; athleteId: number | null };
export type UserUpdate = { name: string; email: string; role: UserRole; athleteId: number | null; active: boolean };
