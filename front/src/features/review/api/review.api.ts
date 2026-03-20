import { api } from '@/lib/axios';
import { API_PATH } from '@/constants/api-path';
import {
  CreateReviewRequest,
  DeleteReviewRequest,
  MyPageReview,
  Review,
  UpdateReviewRequest,
} from '../types/review.types';
import { ApiResponse, PaginatedResponse } from '@/types/api.types';

export const reviewApi = {
  getMyReviews: () =>
    api.get<ApiResponse<MyPageReview[]>>(API_PATH.USER.REVIEWS).then((res) => res.data.data),

  getWineReviews: (wineId: string | number) =>
    api.get<PaginatedResponse<Review>>(API_PATH.REVIEW.LIST(wineId)).then((res) => res.data),

  create: (data: CreateReviewRequest) => {
    const payload = {
      rating: Number(data.rating),
      content: data.content.trim() === '' ? ' ' : data.content,
    };
    return api.post(API_PATH.REVIEW.CREATE(data.wineId), payload).then((res) => res.data);
  },

  update: ({ wineId, reviewId, rating, content }: UpdateReviewRequest) =>
    api
      .patch(API_PATH.REVIEW.DETAIL(wineId, reviewId), { rating, content })
      .then((res) => res.data),

  delete: ({ wineId, reviewId }: DeleteReviewRequest) =>
    api.delete(API_PATH.REVIEW.DETAIL(wineId, reviewId)).then((res) => res.data),
};
