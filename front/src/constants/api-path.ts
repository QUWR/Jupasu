/**
 * API 엔드포인트 경로 상수
 */
export const API_PATH = {
  AUTH: {
    SIGNIN: '/auth/signin',
    SIGNOUT: '/auth/signout',
    SIGNUP: '/auth/signup',
    REFRESH: '/auth/refresh',
    VALIDATE_NICKNAME: '/auth/validate/nickname',
    VALIDATE_EMAIL: '/auth/validate/email',
    SEND_OTP: '/auth/email-verification/send',
    VERIFY_OTP: '/auth/email-verification/verify',
    PASS_RESET_SEND: '/auth/password/reset/send',
    PASS_RESET_VERIFY: '/auth/password/reset/verify',
    PASS_RESET: '/auth/password/reset',
  },
  WINE: {
    LIST: '/wines',
    DETAIL: (id: number | string) => `/wines/${id}`,
    RECOMMEND: '/wines/recommend',
    SCRAPS: '/wines/scraps',
  },
  USER: {
    PROFILE: '/users/profile',
    ONBOARDING: '/users/onboarding',
    ME: '/users/me',
    REVIEWS: '/users/reviews',
    WITHDRAW: '/users/withdraw',
    PREFERENCES: '/preferences',
    SEARCH: '/users/search',
  },
  FRIEND: {
    LIST: '/friends',
    PENDING: '/friends/pending',
    INVITE: '/friends/invite',
    ACCEPT: '/friends/accept',
    DETAIL: (requestId: number | string) => `/friends/${requestId}`,
  },
  REVIEW: {
    LIST: (wineId: number | string) => `/reviews/${wineId}`,
    CREATE: (wineId: number | string) => `/reviews/${wineId}/review`,
    DETAIL: (wineId: number | string, reviewId: number | string) =>
      `/reviews/${wineId}/review/${reviewId}`,
  },
  REPORT: {
    TASTE: '/users/reports',
  },
} as const;
