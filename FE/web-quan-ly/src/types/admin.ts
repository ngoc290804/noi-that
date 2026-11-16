export type Status = 'published' | 'draft' | 'archived';
export interface ImageAsset {
  id: string; src: string; thumbnail: string; publicId?: string | null; alt: string;
  width: number; height: number; version: number; usedCount: number;
}
export interface Category { id: string; name: string; slug: string; active: boolean; displayOrder: number; version: number }
export interface Product {
  id: string; name: string; slug: string; categoryId: string; priceVnd: number;
  shortDescription: string; description: string; careInstructions: string;
  images: ImageAsset[]; specifications: { label: string; value: string | null; unit?: string | null }[];
  displayOrder: number; isFeatured: boolean; status: Status; updatedAt: string; version: number;
  material?: string | null; color?: string | null;
  lengthCm?: number | null; widthCm?: number | null; heightCm?: number | null;
}
export interface HomeContent {
  heroTitle: string; heroDescription: string; heroImage: ImageAsset; featuredProductIds: string[];
  spaces: { title: string; description: string; image: ImageAsset; href: string }[];
}
export interface AdminData { products: Product[]; categories: Category[]; media: ImageAsset[]; content: HomeContent; contentVersion: number }
export interface AdminActions {
  saveProduct: (product: Product, create: boolean) => Promise<void>;
  deleteProduct: (product: Product) => Promise<void>;
  archiveProducts: (products: Product[]) => Promise<void>;
  saveCategory: (category: Category, create: boolean) => Promise<void>;
  deleteCategory: (category: Category) => Promise<void>;
  saveContent: (content: HomeContent, version: number) => Promise<number>;
  uploadImage: (file: File, alt: string) => Promise<void>;
  saveImage: (asset: ImageAsset) => Promise<void>;
  deleteImage: (asset: ImageAsset) => Promise<void>;
}

export interface Account {
  id: string; username: string; displayName: string; role: 'ADMIN' | 'EDITOR'; active: boolean;
  mustChangePassword: boolean; version: number; createdAt: string; lastLoginAt: string | null;
}
export interface SessionInfo { authenticated: boolean; user: Account | null; setupRequired: boolean; setupAuthorized: boolean }
export interface AccountInput { displayName: string; role: Account['role']; active: boolean; version: number }
export interface CreateAccountInput { username: string; displayName: string; role: Account['role']; password: string }
