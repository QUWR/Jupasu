export type FriendResponseStatus = 'ACCEPTED' | 'REJECTED';

export interface FriendListItem {
  requestId: number;
  friendId: number;
  nickname: string;
  character: string;
  reviewCount: number;
  friendSince: string;
}

export interface FriendPendingItem {
  requestId: number;
  requesterId: number;
  nickname: string;
  character: string;
  reviewCount: number;
  createdAt: string;
}

export interface FriendInviteRequest {
  receiverId: number;
  receiverNickname?: string;
}

export interface FriendResponseRequest {
  requestId: number;
  status: FriendResponseStatus;
}
