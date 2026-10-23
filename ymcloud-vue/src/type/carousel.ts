export interface CarouselImage {
  id: string;
  url: string;
}

export interface CarouselSetting {
  configured: boolean;
  images: CarouselImage[];
}
