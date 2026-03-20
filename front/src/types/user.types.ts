/**
 * 사용자 정보 타입 (프로필 조회 응답)
 */
export interface UserInfo {
  nickname: string;
  character: string;
  wishlistCount: number;
  reviewCount: number;
  friendCount: number;
}

export type UserMyPageResponse = UserInfo;

/**
 * 프로필 수정 요청 타입
 */
export interface UpdateProfileRequest {
  nickname?: string;
  character?: string;
  currentPassword?: string;
  newPassword?: string;
  confirmNewPassword?: string;
}

export interface WithdrawRequest {
  password: string;
}

export interface UpdatePreferenceRequest {
  sweetness?: number;
  acidity?: number;
  body?: number;
  tannin?: number;
  abv?: number;
  preferredPriceMin?: number;
  preferredPriceMax?: number;
  preferredTypes?: string[];
  preferredFlavors?: string[];
  drinkingSituations?: string[];
}

export interface UserProfileResponse {
  status: string;
  data: UserInfo;
}

export type AuthStatus = 'idle' | 'loading' | 'authenticated' | 'unauthenticated';

export interface UserSearchResponse {
  userId: number;
  nickname: string;
  character: string | null;
  reviewCount: number;
  friendStatus: 'NONE' | 'PENDING' | 'ACCEPTED' | 'REJECTED';
}
