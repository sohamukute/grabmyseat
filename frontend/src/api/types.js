/**
 * @typedef {{ accessToken: string, refreshToken: string, tokenType?: string, expiresInSeconds?: number }} SessionTokens
 * @typedef {'ROLE_CUSTOMER' | 'ROLE_ADMIN' | 'ROLE_ORGANIZER' | 'ROLE_STAFF'} Role
 * @template T
 * @typedef {{ ok: true, status?: number, data: T }} ApiSuccess
 * @typedef {{ kind: 'aborted' | 'unauthorized' | 'conflict' | 'invalid-response' | 'request-failed', message: string, status?: number, fields?: Record<string, string> }} ApiError
 * @typedef {{ ok: false, error: ApiError }} ApiFailure
 * @template T
 * @typedef {ApiSuccess<T> | ApiFailure} ApiResult
 *
 * @typedef {{ id: number, displayName: string, phone?: string | null, email?: string | null, roles: Role[] }} AdminUserSummary
 * @typedef {{ content: AdminUserSummary[], number: number, totalPages: number, totalElements: number }} AdminUserPage
 * @typedef {{ id: number, name: string, publicationStatus?: string, rejectionReason?: string | null }} OrganizerEventSummary
 * @typedef {{ applicationId: number, applicantUsername: string, eventName: string, status: 'PENDING' | 'APPROVED' | 'REJECTED' | 'REVOKED', appliedAt?: string | null }} StaffApplicationSummary
 * @typedef {{ generalAdmissionCapacity: number, generalAdmissionPrice: number, leftPremiumCapacity: number, leftPremiumPrice: number, rightPremiumCapacity: number, rightPremiumPrice: number }} EventLayoutRequest
 * @typedef {{ name: string, capacity: number, price: number, seats: unknown[], type: 'STANDING' | 'SEATED' }} EventZoneRequest
 * @typedef {{ name: string, venue: string, artworkUrl: string, startsAt: string, endsAt: string | null, queueOpensAt: string | null, saleStartsAt: string, saleEndsAt: string, saleType: 'STANDARD' | 'FLASH', layout: EventLayoutRequest, zones: EventZoneRequest[] }} CreateEventRequest
 */

export {};
