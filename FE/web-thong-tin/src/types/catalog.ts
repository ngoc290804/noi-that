export interface ProductImage {
  src: string;
  thumbnail: string;
  alt: string;
  width: number;
  height: number;
  publicId?: string | null;
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
  lengthCm?: number | null;
  widthCm?: number | null;
  heightCm?: number | null;
  material?: string | null;
  color?: string | null;
}

export type ProductSummary = Pick<Product, 'id' | 'slug' | 'name' | 'categoryId' | 'priceVnd' | 'shortDescription'> & {
  primaryImage: ProductImage | null;
};
export type ProductDetails = Pick<Product, 'description' | 'specifications' | 'lengthCm' | 'widthCm' | 'heightCm' | 'material' | 'color'>;
export type ProductPreview = Pick<Product, 'id' | 'slug' | 'name' | 'priceVnd' | 'images'>;

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
  page?: number;
}

export interface ProductPage {
  items: Product[];
  total: number;
  page: number;
  size: number;
}

export interface HomeContent {
  heroTitle: string;
  heroDescription: string;
  heroImage: ProductImage;
  featuredProductIds: string[];
  spaces: { title: string; description: string; image: ProductImage; href: string }[];
}

export type HomeHero = Pick<HomeContent, 'heroTitle' | 'heroDescription' | 'heroImage'>;
export type HomeSpace = HomeContent['spaces'][number];
