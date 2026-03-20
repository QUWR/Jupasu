'use client';

import { useState } from 'react';
import { X, Search } from 'lucide-react';
import Image from 'next/image';
import Link from 'next/link';
import Modal from '@/components/ui/modal/Modal';
import { useSearchUsersQuery } from '@/features/user/hooks/useUserQueries';
import {
  useInviteFriendMutation,
  useFriendListQuery,
} from '@/features/friend/hooks/useFriendQueries';
import { cn } from '@/lib/utils';

export default function FriendsPage() {
  const [isSearchOpen, setIsSearchOpen] = useState(false);
  const [searchQuery, setSearchQuery] = useState('');

  const { data: searchResults = [], isFetching } = useSearchUsersQuery(searchQuery);
  const { data: friendsList = [] } = useFriendListQuery();
  const inviteFriendMutation = useInviteFriendMutation();

  const handleInvite = async (userId: number, nickname: string) => {
    await inviteFriendMutation.mutateAsync({ receiverId: userId, receiverNickname: nickname });
  };

  return (
    <div className="bg-background flex min-h-screen flex-col pb-24">
      <header className="flex items-center justify-between px-6 py-6">
        <Link
          href="/mypage"
          className="border-primary-100 flex h-10 w-10 items-center justify-center rounded-full border bg-white shadow-sm"
        >
          <X size={24} className="text-text-main rotate-45" />{' '}
          {/* Using X as back arrow placeholder */}
        </Link>
        <h1 className="text-text-main text-xl font-black">친구 목록</h1>
        <button
          onClick={() => setIsSearchOpen(true)}
          className="border-primary-100 flex h-10 w-10 items-center justify-center rounded-full border bg-white shadow-sm"
        >
          <Search size={20} className="text-[#B36262]" />
        </button>
      </header>

      <main className="space-y-6 px-6">
        {friendsList.map((friend) => (
          <div
            key={friend.friendId}
            className="border-primary-100 flex items-center justify-between rounded-[32px] border bg-white p-5 shadow-sm"
          >
            <div className="flex items-center gap-4">
              <div className="bg-primary-100 border-primary-100 relative h-14 w-14 overflow-hidden rounded-full border-2">
                <Image
                  src={
                    friend.character
                      ? friend.character.startsWith('/')
                        ? friend.character
                        : `/${friend.character}1.png`
                      : '/tiger1.png'
                  }
                  alt={friend.nickname}
                  fill
                  className="object-cover"
                />
              </div>
              <div className="flex flex-col">
                <span className="text-text-main leading-tight font-black">{friend.nickname}</span>
                <span className="text-text-main/30 text-xs font-bold">
                  {friend.reviewCount ?? 0}종 시음
                </span>
              </div>
            </div>
            <button className="rounded-2xl bg-gray-500 px-5 py-2.5 text-xs font-black text-white transition-all">
              친구
            </button>
          </div>
        ))}
      </main>

      {/* Friend Search Modal Overlay */}
      <Modal
        isOpen={isSearchOpen}
        onClose={() => {
          setIsSearchOpen(false);
          setSearchQuery('');
        }}
        title="친구 검색"
        hideDefaultFooter
        className="w-[calc(100vw-1rem)] max-w-[20.5rem] rounded-[1.7rem] bg-[#F7F5F1] px-3.5 py-4 sm:max-w-[21.5rem] sm:px-4"
      >
        <div className="bg-primary-100/80 mb-4 h-px" />
        <div className="space-y-3.5">
          <div className="relative">
            <input
              value={searchQuery}
              onChange={(e) => setSearchQuery(e.target.value)}
              className="focus:border-primary-500 placeholder:text-text-main/35 text-text-main h-11 w-full rounded-full border border-[#DDD4C8] bg-[#FBFAF7] py-3 pr-12 pl-4 text-[13px] font-medium focus:outline-none"
              placeholder="친구 닉네임 입력..."
            />
            <button className="absolute top-1/2 right-2 flex h-8 w-8 -translate-y-1/2 items-center justify-center rounded-full bg-[#B17672] text-white">
              <Search size={16} />
            </button>
          </div>

          <div className="max-h-[50vh] space-y-2.5 overflow-y-auto pr-1">
            {searchResults.length > 0 ? (
              searchResults.map((f) => (
                <div
                  key={f.userId}
                  className="flex items-center justify-between rounded-[1.2rem] border border-[#DDD4C8] bg-[#FBFAF7] p-3"
                >
                  <div className="flex items-center gap-3">
                    <div className="relative h-10 w-10 overflow-hidden rounded-full bg-[#8E5A45]">
                      <Image
                        src={
                          f.character
                            ? f.character.startsWith('/')
                              ? f.character
                              : `/${f.character}1.png`
                            : '/tiger1.png'
                        }
                        alt={f.nickname}
                        fill
                        className="object-cover"
                      />
                    </div>
                    <div className="flex flex-col">
                      <span className="text-text-main text-[13px] font-black">{f.nickname}</span>
                      <span className="text-text-main/45 text-[11px] font-medium">
                        {f.reviewCount ?? 0}종 시음
                      </span>
                    </div>
                  </div>
                  <button
                    onClick={() => {
                      if (f.friendStatus === 'NONE') handleInvite(f.userId, f.nickname);
                    }}
                    className={cn(
                      'rounded-full px-3.5 py-1.5 text-[10px] font-black transition-colors',
                      f.friendStatus === 'ACCEPTED'
                        ? 'bg-[#8B8B8B] text-white'
                        : f.friendStatus === 'PENDING'
                          ? 'text-text-main/55 bg-[#F3EFE8]'
                          : 'bg-[#F6EAEA] text-[#B17672]',
                    )}
                    disabled={f.friendStatus !== 'NONE' || inviteFriendMutation.isPending}
                  >
                    {f.friendStatus === 'ACCEPTED'
                      ? '친구'
                      : f.friendStatus === 'PENDING'
                        ? '요청 중'
                        : '친구 요청'}
                  </button>
                </div>
              ))
            ) : isFetching ? (
              <div className="text-text-main/50 py-8 text-center text-[13px] font-medium">
                검색 중...
              </div>
            ) : searchQuery.trim().length >= 2 ? (
              <div className="text-text-main/50 py-8 text-center text-[13px] font-medium">
                검색 결과가 없습니다.
              </div>
            ) : (
              <div className="text-text-main/50 py-8 text-center text-[13px] font-medium">
                닉네임을 2글자 이상 검색해 보세요.
              </div>
            )}
          </div>
        </div>
      </Modal>
    </div>
  );
}
