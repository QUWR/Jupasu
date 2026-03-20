export type WineType = 'RED' | 'WHITE' | 'ROSE' | 'DESSERT' | 'FORTIFIED' | 'SPARKLING';

export interface Wine {
  id: number;
  nameKr: string;
  nameEn: string;
  type: WineType;
  country: string;
  averageRating: number;
  price: number;
  imageUrl: string;
}

export interface WineDetail {
  wineId: number;
  nameKr: string;
  nameEn: string;
  imageUrl: string;
  country: string;
  region: string;
  winery: string;
  grapeVariety: string;
  alcoholDegree: number;
  averageRating: number;
  price: number;
  description: string;
  sweetness: number;
  acidity: number;
  body: number;
  tannin: number;
  matchRate: number;
  pairingFoods: string[];
}

export interface WineReview {
  reviewId: number;
  userId: number;
  nickname: string;
  rating: number;
  content: string;
  createdAt: string;
}

export interface WineListParams {
  keyword?: string;
  page?: number;
  size?: number;
  type?: WineType;
  sort?: string;
}

export interface ScrapWine {
  wineId: number;
  scrapId: number;
  wineName: string;
  wineType: string;
  averageRating: number;
  price: number;
  matchRate: number;
  country: string;
  imageUrl: string;
  isScraped: boolean;
}

export type DrinkingSituation = 'GIFT' | 'ALONE' | 'HOUSEWARMING' | 'PARTY' | 'DATE' | 'FAMILY';

export interface WineRecommendationItem {
  wineId: number;
  nameKr: string;
  matchRate: number;
  recommendationReason: string;
}

export interface SituationResult {
  situation: DrinkingSituation;
  situationName: string;
  recommendations: WineRecommendationItem[];
}

export interface WineQuickRecommendResponse {
  general: WineRecommendationItem[];
  bySituation: SituationResult[];
}
