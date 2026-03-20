import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { QUERY_KEY } from '@/constants/query-key';
import { userApi } from '../api/user.api';

export function useUserProfile() {
  return useQuery({
    queryKey: QUERY_KEY.USER.ME,
    queryFn: userApi.getMyPageProfile,
  });
}

export function useUpdateProfileMutation() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: userApi.updateUserProfile,
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: QUERY_KEY.USER.ME });
    },
  });
}

export function useWithdrawMutation() {
  return useMutation({
    mutationFn: userApi.withdrawUser,
  });
}

export function useUpdatePreferenceMutation() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: userApi.updatePreference,
    onSuccess: () => {
      // 취향 정보가 업데이트되면 유저의 최신 AI 리포트가 만료되므로 report taste 캐시를 무효화하여 다시 가져옴
      queryClient.invalidateQueries({ queryKey: ['report', 'taste'] });
      // 자신의 취향 정보 쿼리도 무효화
      queryClient.invalidateQueries({ queryKey: [QUERY_KEY.USER.ME, 'preferences'] });
    },
  });
}

export function useSearchUsersQuery(nickname: string) {
  return useQuery({
    queryKey: ['users', 'search', nickname],
    queryFn: () => userApi.searchUsers(nickname),
    enabled: nickname.trim().length >= 2,
  });
}

export function usePreferenceQuery() {
  return useQuery({
    queryKey: [QUERY_KEY.USER.ME, 'preferences'],
    queryFn: userApi.getPreference,
  });
}
