export interface ProductImage {
  src: string;
  thumbnail: string;
  alt: string;
  width: number;
  height: number;
}

export interface Specification {
  label: string;
  value: string | null;
  unit?: string;
}

export interface Product {
  id: string;
  slug: string;
  name: string;
  categoryId: string;
  priceVnd: number;
  shortDescription: string;
  description: string;
  images: ProductImage[];
  specifications: Specification[];
  displayOrder: number;
  isFeatured: boolean;
}

export interface Category {
  id: string;
  name: string;
  slug: string;
}

export type SortOrder = 'featured' | 'price-asc' | 'price-desc';
export interface CatalogQuery {
  q: string;
  category: string;
  sort: SortOrder;
}
