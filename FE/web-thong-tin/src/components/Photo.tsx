import { useState } from 'react';
import type { ProductImage } from '../types/catalog';

export function Photo({ image, priority = false, sizes = '(min-width: 1024px) 25vw, (min-width: 768px) 33vw, 50vw', className = '' }: {
  image: ProductImage;
  priority?: boolean;
  sizes?: string;
  className?: string;
}) {
  const [failed, setFailed] = useState(false);
  if (failed) return <div className={`image-fallback ${className}`} role="img" aria-label={image.alt}><span>Ảnh đang được cập nhật</span></div>;
  return <img className={className} src={image.src} srcSet={`${image.thumbnail} 600w, ${image.src} ${image.width}w`} sizes={sizes} alt={image.alt} width={image.width} height={image.height} loading={priority ? 'eager' : 'lazy'} fetchPriority={priority ? 'high' : 'auto'} decoding="async" onError={() => setFailed(true)} />;
}
